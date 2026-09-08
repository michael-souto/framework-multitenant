package com.detrasoft.framework.multitenant.component;

import com.detrasoft.framework.multitenant.service.TenantMigrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Executa a migração automática do Flyway em todos os schemas (tenants)
 * durante a inicialização da aplicação, antes de iniciar o atendimento de requisições HTTP.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantAutoMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantAutoMigrationRunner.class);

    private final TenantMigrationService migrationService;

    @Value("${multitenant.auto-migration.enabled:true}")
    private boolean autoMigrationEnabled;

    @Value("${multitenant.auto-migration.concurrency:5}")
    private int concurrency;

    public TenantAutoMigrationRunner(TenantMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(String... args) {
        if (!autoMigrationEnabled) {
            log.info("[MULTITENANT] Migração automática na inicialização desativada (multitenant.auto-migration.enabled=false).");
            return;
        }

        log.info("[MULTITENANT] Executando auto-migration em todos os schemas na inicialização do serviço...");
        migrationService.migrateAllSchemas(concurrency);
    }
}
