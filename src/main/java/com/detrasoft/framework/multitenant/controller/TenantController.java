package com.detrasoft.framework.multitenant.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.detrasoft.framework.multitenant.context.TenantContext;
import com.detrasoft.framework.multitenant.dto.MigrationResult;
import com.detrasoft.framework.multitenant.service.TenantMigrationService;

@RestController
@RequestMapping("/tenant-admin")
public class TenantController {

	@Autowired
	private TenantMigrationService migrationService;

	@GetMapping
	public ResponseEntity<String> createUpdateTenant() {
		try {
			String schema = TenantContext.getTenantSchema();
			MigrationResult result = migrationService.migrateSchema(schema);
			if (result.isSuccess()) {
				return new ResponseEntity<>("Ambiente criado/atualizado com sucesso", HttpStatus.OK);
			} else {
				return new ResponseEntity<>("Erro na criação/atualização do ambiente: " + result.getErrorMessage(), HttpStatus.BAD_REQUEST);
			}
		} catch (Exception e) {
			return new ResponseEntity<>("Erro na criação do ambiente: " + e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@GetMapping("/all")
	public ResponseEntity<List<MigrationResult>> migrateAllTenants() {
		try {
			List<MigrationResult> results = migrationService.migrateAllSchemas();
			return ResponseEntity.ok(results);
		} catch (Exception e) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
