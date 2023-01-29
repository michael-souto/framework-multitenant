package com.detrasoft.framework.multitenant.controller;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.detrasoft.framework.security.context.UsuarioContext;

@RestController
@RequestMapping("/tenant-admin")
public class TenantController {

	@Autowired
	private DataSource dataSource;

	@GetMapping
	public ResponseEntity<String> createUpdateTenant() {
		try {
			// Criando o schema para o banco de dados
			String schema = UsuarioContext.getIdDetrasoft().toString();
			Flyway flywayDML = Flyway.configure().dataSource(dataSource).schemas(schema).load();
			flywayDML.migrate();
			return new ResponseEntity<>("Ambiente criado com sucesso", HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>("Erro na criação do ambiente" + e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}
