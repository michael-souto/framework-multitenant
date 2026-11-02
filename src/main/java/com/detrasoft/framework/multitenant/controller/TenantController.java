package com.detrasoft.framework.multitenant.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.detrasoft.framework.core.context.GenericContext;
import com.detrasoft.framework.multitenant.dto.MigrationResult;
import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningRequester;
import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningService;
import com.detrasoft.framework.multitenant.provisioning.TenantRequest;
import com.detrasoft.framework.multitenant.provisioning.TenantStatus;
import com.detrasoft.framework.multitenant.service.TenantMigrationService;

@RestController
@RequestMapping("/tenant-admin")
public class TenantController {

	@Autowired
	private TenantMigrationService migrationService;

	@Autowired
	private TenantProvisioningService provisioningService;

	@Autowired
	private ObjectProvider<TenantProvisioningRequester> requesters;

	@GetMapping
	public ResponseEntity<String> createUpdateTenant() {
		try {
			String schema = GenericContext.getContexts("detrasoftId");
			MigrationResult result = migrationService.migrateSchema(schema);
			if (result.isSuccess()) {
				return new ResponseEntity<>("Ambiente criado/atualizado com sucesso", HttpStatus.OK);
			} else {
				return new ResponseEntity<>("Erro na criação/atualização do ambiente: " + result.getErrorMessage(),
						HttpStatus.BAD_REQUEST);
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

	/** Situacao do tenant da conta logada neste servico. */
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> status() {
		String tenant = GenericContext.getContexts("detrasoftId");
		if (!TenantRequest.isValidTenant(tenant)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(body(tenant, provisioningService.status(tenant)));
	}

	/**
	 * Garante o tenant da conta logada: READY, ou pede a criacao e devolve
	 * PENDING. Seguro para polling: o pedido se repete no maximo a cada 30s.
	 * Usado pelo login (@detrasoft.com/web-auth) quando uma conta ja existente
	 * entra pela primeira vez neste produto.
	 */
	@PostMapping("/ensure")
	public ResponseEntity<Map<String, Object>> ensure() {
		String tenant = GenericContext.getContexts("detrasoftId");
		if (!TenantRequest.isValidTenant(tenant)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		TenantRequest request = TenantRequest.of(tenant,
				GenericContext.getContexts("language"),
				GenericContext.getContexts("userEmail"),
				GenericContext.getContexts("firstName"));
		TenantProvisioningRequester requester = requesters.getIfAvailable(() -> this::provisionLocally);
		return ResponseEntity.ok(body(tenant, provisioningService.ensure(request, requester)));
	}

	/** Sem mensageria: cria em segundo plano no proprio servico. */
	private void provisionLocally(TenantRequest request) {
		Thread.ofVirtual().name("tenant-provisioning-" + request.tenant())
				.start(() -> provisioningService.provision(request));
	}

	private static Map<String, Object> body(String tenant, TenantStatus status) {
		return Map.of("tenant", tenant, "status", status.name(), "ready", status == TenantStatus.READY);
	}
}
