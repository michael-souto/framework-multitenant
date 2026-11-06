package com.detrasoft.framework.multitenant.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.detrasoft.framework.multitenant.config.FlywayNativeSupport;
import com.detrasoft.framework.multitenant.dto.MigrationResult;

@Service
public class TenantMigrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationService.class);

    private final DataSource dataSource;

    @Value("${multitenant.auto-migration.concurrency:5}")
    private int defaultConcurrency;

    /**
     * Tabela de histórico do Flyway em cada schema. Um nome próprio isola a versão nova de um serviço
     * de um histórico legado que divide os mesmos schemas (ex.: Speak V3 sobre o banco da V2).
     */
    @Value("${multitenant.flyway.table:flyway_schema_history}")
    private String historyTable = "flyway_schema_history";

    /**
     * {@code repair()} antes de cada migrate. Padrão true por compatibilidade; desligado, checksum
     * divergente falha o migrate em vez de ser realinhado em silêncio.
     */
    @Value("${multitenant.flyway.repair-on-migrate:true}")
    private boolean repairOnMigrate = true;

    /** Inclui o schema public na migração de todos os schemas. */
    @Value("${multitenant.flyway.include-public:true}")
    private boolean includePublic = true;

    public TenantMigrationService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Nome da tabela de histórico do Flyway usada em cada schema de tenant. */
    public String getHistoryTable() {
        return historyTable;
    }

    /**
     * Descobre todos os schemas de tenant existentes no banco de dados: IDs numéricos, schemas que já
     * tenham a tabela de histórico configurada e, se {@code multitenant.flyway.include-public}, o public.
     */
    public List<String> getTenantSchemas() {
        List<String> schemas = new ArrayList<>();
        String sql = """
                SELECT nspname
                FROM pg_namespace
                WHERE nspname NOT IN ('information_schema', 'pg_catalog', 'pg_toast')
                  AND nspname NOT LIKE 'pg_temp_%'
                  AND nspname NOT LIKE 'pg_toast_temp_%'
                ORDER BY CASE WHEN nspname = 'public' THEN 0 ELSE 1 END, nspname
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String schemaName = rs.getString("nspname");
                if (isEligibleTenantSchema(conn, schemaName)) {
                    schemas.add(schemaName);
                }
            }
        } catch (SQLException e) {
            log.error("[MULTITENANT] Erro ao descobrir schemas no banco de dados", e);
        }

        return schemas;
    }

    private boolean isEligibleTenantSchema(Connection conn, String schemaName) {
        if ("public".equalsIgnoreCase(schemaName)) {
            return includePublic;
        }
        // Identificadores de tenant são numéricos (detrasoftId)
        if (schemaName.matches("^\\d+$")) {
            return true;
        }
        // Ou se já possuir a tabela de histórico do Flyway
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schemaName);
            ps.setString(2, historyTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Executa as migrations do Flyway para um schema específico.
     */
    public MigrationResult migrateSchema(String schema) {
        long startTime = System.currentTimeMillis();
        log.info("[MULTITENANT] Iniciando migration para o schema '{}'...", schema);
        try {
            Flyway flyway = FlywayNativeSupport.nativeCompatible(Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .table(historyTable)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0"))
                    .load();
            FlywayNativeSupport.assertMigrationsResolved(flyway);

            if (repairOnMigrate) {
                // Realinha checksums caso o conteúdo do SQL tenha mudado (só metadados do histórico).
                flyway.repair();
            }

            MigrateResult result = flyway.migrate();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[MULTITENANT] Schema '{}' migrado com sucesso: {} migrations aplicadas em {}ms",
                    schema, result.migrationsExecuted, duration);

            return MigrationResult.builder()
                    .schema(schema)
                    .success(true)
                    .migrationsExecuted(result.migrationsExecuted)
                    .durationMs(duration)
                    .build();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[MULTITENANT] Falha ao migrar schema '{}' após {}ms: {}", schema, duration, e.getMessage(), e);

            return MigrationResult.builder()
                    .schema(schema)
                    .success(false)
                    .migrationsExecuted(0)
                    .errorMessage(e.getMessage())
                    .durationMs(duration)
                    .build();
        }
    }

    /**
     * Executa a migração em todos os schemas utilizando Virtual Threads com
     * controle de concorrência.
     */
    public List<MigrationResult> migrateAllSchemas() {
        return migrateAllSchemas(defaultConcurrency);
    }

    public List<MigrationResult> migrateAllSchemas(int concurrency) {
        List<String> schemas = getTenantSchemas();
        if (schemas.isEmpty()) {
            log.warn("[MULTITENANT] Nenhum schema de tenant encontrado para migração.");
            return Collections.emptyList();
        }

        int maxThreads = Math.max(1, Math.min(concurrency, 15));
        log.info("[MULTITENANT] Iniciando migração em {} schemas com Virtual Threads (concorrência máxima: {})...",
                schemas.size(), maxThreads);

        long totalStartTime = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(maxThreads);
        List<MigrationResult> results = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<MigrationResult>> futures = schemas.stream()
                    .map(schema -> executor.submit(() -> {
                        semaphore.acquire();
                        try {
                            return migrateSchema(schema);
                        } finally {
                            semaphore.release();
                        }
                    }))
                    .toList();

            for (Future<MigrationResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("[MULTITENANT] Thread de migração interrompida", e);
                } catch (ExecutionException e) {
                    log.error("[MULTITENANT] Erro na execução da migração", e);
                }
            }
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        long successCount = results.stream().filter(MigrationResult::isSuccess).count();
        int totalMigrations = results.stream().mapToInt(MigrationResult::getMigrationsExecuted).sum();

        log.info(
                "[MULTITENANT] Concluída migração de todos os schemas em {}ms. Sucesso: {}/{}, Total de migrations executadas: {}",
                totalDuration, successCount, schemas.size(), totalMigrations);

        return results;
    }
}
