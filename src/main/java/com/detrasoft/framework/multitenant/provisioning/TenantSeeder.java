package com.detrasoft.framework.multitenant.provisioning;

import java.sql.Connection;

/**
 * Dados padrao de um tenant recem-criado (ex.: cadastros iniciais por idioma).
 *
 * Roda uma unica vez por tenant, depois das migrations, numa transacao com o
 * search_path apontando para o schema do tenant. Se falhar, a transacao volta e
 * a proxima tentativa roda de novo. Tenants que ja existiam antes do
 * provisionamento nunca recebem seed.
 */
public interface TenantSeeder {

	void seed(Connection connection, TenantRequest request) throws Exception;
}
