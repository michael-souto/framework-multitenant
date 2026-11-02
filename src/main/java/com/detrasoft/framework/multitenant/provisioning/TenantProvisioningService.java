package com.detrasoft.framework.multitenant.provisioning;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import com.detrasoft.framework.multitenant.dto.MigrationResult;
import com.detrasoft.framework.multitenant.service.TenantMigrationService;

/**
 * Criacao do tenant de uma conta Detrasoft neste servico. Unico caminho para
 * criar tenant: listener da fila do produto, endpoint do usuario logado e
 * qualquer chamada interna passam por {@link #provision}.
 *
 * Idempotente e seguro em concorrencia:
 * <ol>
 * <li>um advisory lock do Postgres por tenant serializa replicas e
 * reentregas;</li>
 * <li>schema novo nasce com a tabela {@code tenant_provisioning} em PENDING;</li>
 * <li>Flyway aplica as migrations (so o que falta);</li>
 * <li>os {@link TenantSeeder} rodam numa unica transacao que tambem marca
 * READY. Se falharem, a marca continua PENDING e a proxima tentativa refaz o
 * seed inteiro.</li>
 * </ol>
 * Tenants anteriores a esta tabela (sem a marca) contam como prontos quando ja
 * tem historico do Flyway, e nunca recebem seed.
 */
@Service
public class TenantProvisioningService {

	private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);

	static final String MARKER_TABLE = "tenant_provisioning";
	private static final long REQUEST_INTERVAL_MS = 30_000;

	private final DataSource dataSource;
	private final TenantMigrationService migrationService;
	private final ObjectProvider<TenantSeeder> seeders;
	private final TenantProvisioningProperties properties;

	/** Ultimo pedido por tenant, para o polling do front nao republicar a cada chamada. */
	private final Map<String, Long> lastRequest = new ConcurrentHashMap<>();

	public TenantProvisioningService(DataSource dataSource, TenantMigrationService migrationService,
			ObjectProvider<TenantSeeder> seeders, TenantProvisioningProperties properties) {
		this.dataSource = dataSource;
		this.migrationService = migrationService;
		this.seeders = seeders;
		this.properties = properties;
	}

	/** Situacao atual, sem efeito colateral. */
	public TenantStatus status(String tenant) {
		if (!TenantRequest.isValidTenant(tenant)) {
			throw new IllegalArgumentException("Tenant invalido: " + tenant);
		}
		try (Connection connection = dataSource.getConnection()) {
			return state(connection, tenant).ready() ? TenantStatus.READY : TenantStatus.PENDING;
		} catch (SQLException e) {
			throw new IllegalStateException("Falha ao consultar o tenant " + tenant, e);
		}
	}

	/**
	 * Garante o tenant: devolve READY ou pede a criacao (no maximo uma vez a cada
	 * 30s por tenant) e devolve PENDING.
	 */
	public TenantStatus ensure(TenantRequest request, TenantProvisioningRequester requester) {
		if (status(request.tenant()) == TenantStatus.READY) {
			lastRequest.remove(request.tenant());
			return TenantStatus.READY;
		}
		long now = System.currentTimeMillis();
		Long previous = lastRequest.get(request.tenant());
		if (previous == null || now - previous > REQUEST_INTERVAL_MS) {
			lastRequest.put(request.tenant(), now);
			log.info("[MULTITENANT] Tenant {} ausente; criacao solicitada", request.tenant());
			requester.request(request);
		}
		return TenantStatus.PENDING;
	}

	/** Cria (ou completa) o tenant. Pode ser chamado quantas vezes for preciso. */
	public void provision(TenantRequest request) {
		String tenant = request.tenant();
		long start = System.currentTimeMillis();
		try (Connection connection = dataSource.getConnection()) {
			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(true);
			lock(connection, tenant, true);
			try {
				State state = state(connection, tenant);
				if (state.ready()) {
					log.info("[MULTITENANT] Tenant {} ja estava pronto", tenant);
					return;
				}
				if (!state.schemaExists()) {
					createSchema(connection, tenant);
				}

				MigrationResult migration = migrationService.migrateSchema(tenant);
				if (!migration.isSuccess()) {
					throw new IllegalStateException(
							"Falha nas migrations do tenant " + tenant + ": " + migration.getErrorMessage());
				}

				if (!state.schemaExists() || state.markerExists()) {
					seed(connection, request);
				}
				log.info("[MULTITENANT] Tenant {} pronto em {}ms", tenant, System.currentTimeMillis() - start);
			} finally {
				lock(connection, tenant, false);
				connection.setAutoCommit(autoCommit);
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Falha ao criar o tenant " + tenant, e);
		} finally {
			lastRequest.remove(tenant);
		}
	}

	// === internos ===

	private record State(boolean schemaExists, boolean migrated, boolean markerExists, String markerStatus) {
		boolean ready() {
			return migrated && (!markerExists || "READY".equals(markerStatus));
		}
	}

	private State state(Connection connection, String tenant) throws SQLException {
		boolean schemaExists;
		boolean migrated;
		boolean markerExists;
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?),
				       to_regclass(format('%I.flyway_schema_history', ?::text)) IS NOT NULL,
				       to_regclass(format('%I.""" + MARKER_TABLE + "', ?::text)) IS NOT NULL")) {
			ps.setString(1, tenant);
			ps.setString(2, tenant);
			ps.setString(3, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				schemaExists = rs.getBoolean(1);
				migrated = rs.getBoolean(2);
				markerExists = rs.getBoolean(3);
			}
		}
		if (migrated) {
			// A tabela de historico nasce antes da primeira migration (linhas SCHEMA/BASELINE):
			// so conta como migrado com ao menos uma migration aplicada e nenhuma falha.
			try (Statement st = connection.createStatement();
					ResultSet rs = st.executeQuery("SELECT count(*) FILTER (WHERE success AND type NOT IN ('SCHEMA', 'BASELINE')) > 0"
							+ " AND coalesce(bool_and(success), false) FROM " + quote(tenant) + ".flyway_schema_history")) {
				migrated = rs.next() && rs.getBoolean(1);
			}
		}
		String markerStatus = null;
		if (markerExists) {
			try (Statement st = connection.createStatement();
					ResultSet rs = st.executeQuery("SELECT status FROM " + table(tenant) + " LIMIT 1")) {
				markerStatus = rs.next() ? rs.getString(1) : null;
			}
		}
		return new State(schemaExists, migrated, markerExists, markerStatus);
	}

	private void createSchema(Connection connection, String tenant) throws SQLException {
		inTransaction(connection, () -> {
			try (Statement st = connection.createStatement()) {
				st.execute("CREATE SCHEMA IF NOT EXISTS " + quote(tenant));
				st.execute("CREATE TABLE IF NOT EXISTS " + table(tenant)
						+ " (status varchar(10) NOT NULL, requested_at timestamptz NOT NULL DEFAULT now(),"
						+ " completed_at timestamptz)");
				st.execute("INSERT INTO " + table(tenant) + " (status) SELECT 'PENDING'"
						+ " WHERE NOT EXISTS (SELECT 1 FROM " + table(tenant) + ")");
			}
		});
	}

	/** Seed + marca READY na mesma transacao: ou tudo, ou nada. */
	private void seed(Connection connection, TenantRequest request) throws SQLException {
		String tenant = request.tenant();
		inTransaction(connection, () -> {
			try (Statement st = connection.createStatement()) {
				st.execute("SET LOCAL search_path TO " + quote(tenant) + ", public");
			}
			runClasspathSeed(connection, request);
			for (TenantSeeder seeder : seeders.orderedStream().toList()) {
				seeder.seed(connection, request);
			}
			try (Statement st = connection.createStatement()) {
				st.execute("CREATE TABLE IF NOT EXISTS " + table(tenant)
						+ " (status varchar(10) NOT NULL, requested_at timestamptz NOT NULL DEFAULT now(),"
						+ " completed_at timestamptz)");
				int updated = st.executeUpdate("UPDATE " + table(tenant)
						+ " SET status = 'READY', completed_at = now()");
				if (updated == 0) {
					st.execute("INSERT INTO " + table(tenant) + " (status, completed_at) VALUES ('READY', now())");
				}
			}
		});
	}

	/**
	 * Seed por arquivos: {@code multitenant.provisioning.seed-location} com
	 * {@code {language}}, ex. {@code db/implantation/{language}}. Roda os .sql em
	 * ordem de nome; sem arquivos para o idioma, usa o idioma padrao.
	 */
	private void runClasspathSeed(Connection connection, TenantRequest request) throws Exception {
		String location = properties.getSeedLocation();
		if (location == null || location.isBlank()) {
			return;
		}
		List<Resource> scripts = scripts(location, language(request.language()));
		if (scripts.isEmpty()) {
			scripts = scripts(location, language(properties.getDefaultLanguage()));
		}
		for (Resource script : scripts) {
			log.info("[MULTITENANT] Seed {} no tenant {}", script.getFilename(), request.tenant());
			ScriptUtils.executeSqlScript(connection, script);
		}
	}

	private List<Resource> scripts(String location, String language) throws Exception {
		String base = location.replace("{language}", language).replaceAll("^/+|/+$", "");
		Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath*:" + base + "/*.sql");
		return Arrays.stream(found)
				.filter(Resource::exists)
				.sorted(Comparator.comparing(r -> r.getFilename() == null ? "" : r.getFilename()))
				.toList();
	}

	private static String language(String value) {
		if (value == null || value.isBlank()) {
			return "pt";
		}
		String lang = value.trim().toLowerCase(Locale.ROOT);
		int sep = lang.indexOf('-') >= 0 ? lang.indexOf('-') : lang.indexOf('_');
		lang = sep > 0 ? lang.substring(0, sep) : lang;
		return lang.matches("^[a-z]{2,3}$") ? lang : "pt";
	}

	private void lock(Connection connection, String tenant, boolean acquire) throws SQLException {
		String fn = acquire ? "pg_advisory_lock" : "pg_advisory_unlock";
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT " + fn + "(hashtextextended('tenant-provisioning:' || ?, 0))")) {
			ps.setString(1, tenant);
			ps.execute();
		}
	}

	@FunctionalInterface
	private interface SqlWork {
		void run() throws Exception;
	}

	private void inTransaction(Connection connection, SqlWork work) throws SQLException {
		connection.setAutoCommit(false);
		try {
			work.run();
			connection.commit();
		} catch (Exception e) {
			connection.rollback();
			if (e instanceof SQLException sql) {
				throw sql;
			}
			throw new IllegalStateException(e.getMessage(), e);
		} finally {
			connection.setAutoCommit(true);
		}
	}

	/** O tenant ja foi validado (so digitos); as aspas protegem o nome numerico. */
	private static String quote(String tenant) {
		return "\"" + tenant + "\"";
	}

	private static String table(String tenant) {
		return quote(tenant) + "." + MARKER_TABLE;
	}
}
