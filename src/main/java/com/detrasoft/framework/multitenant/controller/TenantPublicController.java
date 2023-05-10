package com.detrasoft.framework.multitenant.controller;

import com.detrasoft.framework.multitenant.context.TenantContext;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@RestController
@RequestMapping("/public/tenant-admin")
public class TenantPublicController {
    @Autowired
    private DataSource dataSource;

    @Value("${KEY_TENANT:}")
    private String keyTenantEnv;

    @GetMapping(value = "/{id}/{key}")
    public ResponseEntity<String> createUpdateTenant(@PathVariable String id, @PathVariable String key) {
        try {
            if ((keyTenantEnv != null && !keyTenantEnv.isEmpty() && keyTenantEnv.equals(key))
                    && (id != null && !id.isBlank())
            ) {
                String schema = TenantContext.getTenantSchema();
                Flyway flywayDML = Flyway.configure().dataSource(dataSource).schemas(id).load();
                flywayDML.migrate();
                return new ResponseEntity<>("Ambiente criado com sucesso", HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Não foi possível atualizar o ambiente", HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            return new ResponseEntity<>("Erro na criação do ambiente" + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
