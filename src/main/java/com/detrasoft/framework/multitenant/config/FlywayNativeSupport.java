package com.detrasoft.framework.multitenant.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Suporte ao Flyway configurado manualmente ({@code Flyway.configure()}) em GraalVM native image.
 * <p>
 * O scanner de classpath do Flyway não entende o protocolo {@code resource:} da imagem nativa
 * ("Unable to scan location ... unsupported protocol: resource") e não encontra nenhuma migration.
 * Na imagem nativa, as migrations passam a ser localizadas pelo
 * {@link PathMatchingResourcePatternResolver} do Spring; na JVM nada muda.
 * <p>
 * Os diretórios de migration precisam estar registrados como resource hints
 * (ex.: {@code hints.resources().registerPattern("db/migration/**")}).
 */
public final class FlywayNativeSupport {

	private FlywayNativeSupport() {
	}

	/**
	 * Aplica, somente em imagem nativa, um {@link ResourceProvider} compatível. Chamar depois de
	 * definir {@code locations}, {@code encoding} e {@code classLoader}.
	 */
	public static FluentConfiguration nativeCompatible(FluentConfiguration configuration) {
		if (NativeDetector.inNativeImage()) {
			configuration.resourceProvider(resourceProvider(configuration));
			// Sem isso o scanner padrão ainda varre o classpath atrás de JavaMigration e loga
			// "unsupported protocol: resource". Migrations em Java não são suportadas no nativo.
			configuration.javaMigrationClassProvider(List::of);
		}
		return configuration;
	}

	/** {@link ResourceProvider} baseado no Spring, funcional em JVM (jar/diretório) e nativo. */
	public static ResourceProvider resourceProvider(FluentConfiguration configuration) {
		return new SpringClassPathResourceProvider(configuration.getLocations(), configuration.getClassLoader(),
				configuration.getEncoding());
	}

	/**
	 * Falha se nenhuma migration foi encontrada nas locations configuradas. Deve ser chamado antes de
	 * {@code repair()}/{@code migrate()}: com zero migrations resolvidas, o {@code repair()} marcaria
	 * as migrations já aplicadas como removidas no histórico.
	 */
	public static void assertMigrationsResolved(Flyway flyway) {
		boolean anyResolved = Arrays.stream(flyway.info().all())
				.map(MigrationInfo::getState)
				.anyMatch(state -> state.isResolved());
		if (!anyResolved) {
			throw new IllegalStateException("Nenhuma migration encontrada em "
					+ Arrays.toString(flyway.getConfiguration().getLocations())
					+ ". Abortando antes de repair/migrate para não corromper o flyway_schema_history.");
		}
	}

	static final class SpringClassPathResourceProvider implements ResourceProvider {

		private final Location[] locations;

		private final ClassLoader classLoader;

		private final Charset encoding;

		private final PathMatchingResourcePatternResolver resolver;

		SpringClassPathResourceProvider(Location[] locations, ClassLoader classLoader, Charset encoding) {
			this.locations = locations;
			this.classLoader = classLoader;
			this.encoding = encoding;
			this.resolver = new PathMatchingResourcePatternResolver(classLoader);
		}

		@Override
		public LoadableResource getResource(String name) {
			if (classLoader.getResource(name) == null) {
				return null;
			}
			return new ClassPathResource(null, name, classLoader, encoding);
		}

		@Override
		public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
			List<LoadableResource> result = new ArrayList<>();
			for (Location location : locations) {
				if (!location.isClassPath()) {
					continue;
				}
				String root = trimSlashes(location.getRootPath());
				for (Resource resource : find("classpath*:" + root + "/**/*")) {
					String fileName = resource.getFilename();
					if (fileName == null || !resource.isReadable() || !matches(fileName, prefix, suffixes)) {
						continue;
					}
					String path = pathFromRoot(resource, root);
					if (path != null) {
						result.add(new ClassPathResource(location, path, classLoader, encoding));
					}
				}
			}
			return result;
		}

		private Resource[] find(String pattern) {
			try {
				return resolver.getResources(pattern);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Falha ao localizar migrations em " + pattern, ex);
			}
		}

		private static boolean matches(String fileName, String prefix, String[] suffixes) {
			if (!fileName.startsWith(prefix)) {
				return false;
			}
			for (String suffix : suffixes) {
				if (fileName.endsWith(suffix)) {
					return true;
				}
			}
			return false;
		}

		/** Caminho relativo ao classpath ("db/migration/V001__x.sql"), em jar, diretório ou resource:. */
		private static String pathFromRoot(Resource resource, String root) {
			try {
				String url = resource.getURL().toString();
				int index = url.lastIndexOf("/" + root + "/");
				return index < 0 ? null : url.substring(index + 1);
			}
			catch (IOException ex) {
				return null;
			}
		}

		private static String trimSlashes(String path) {
			String trimmed = path;
			while (trimmed.startsWith("/")) {
				trimmed = trimmed.substring(1);
			}
			while (trimmed.endsWith("/")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			return trimmed;
		}

	}

}
