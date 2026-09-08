package com.detrasoft.framework.multitenant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.detrasoft.framework.multitenant.dto.MigrationResult;
import com.detrasoft.framework.multitenant.service.TenantMigrationService;

@RestController
@RequestMapping("/public/tenant-admin")
public class TenantPublicController {

    @Autowired
    private TenantMigrationService migrationService;

    @Value("${KEY_TENANT:}")
    private String keyTenantEnv;

    @GetMapping(value = "/{id}/{key}")
    public ResponseEntity<String> createUpdateTenant(@PathVariable String id, @PathVariable String key) {
        try {
            if ((keyTenantEnv != null && !keyTenantEnv.isEmpty() && keyTenantEnv.equals(key))
                    && (id != null && !id.isBlank())
            ) {
                MigrationResult result = migrationService.migrateSchema(id);
                if (result.isSuccess()) {
                    return new ResponseEntity<>("Ambiente criado com sucesso", HttpStatus.OK);
                } else {
                    return new ResponseEntity<>("Erro ao migrar schema: " + result.getErrorMessage(), HttpStatus.BAD_REQUEST);
                }
            } else {
                return new ResponseEntity<>("Não foi possível atualizar o ambiente", HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            return new ResponseEntity<>("Erro na criação do ambiente: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
