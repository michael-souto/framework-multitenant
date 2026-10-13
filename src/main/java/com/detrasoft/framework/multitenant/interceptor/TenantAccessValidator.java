package com.detrasoft.framework.multitenant.interceptor;

/**
 * Decide se o usuario autenticado pode operar em um tenant diferente do seu
 * (o detrasoftId do token). Sem nenhum bean desta interface o acesso cruzado e
 * negado: o header "tenant" so e aceito quando coincide com o tenant do token.
 *
 * Servicos que suportam compartilhamento entre tenants registram uma
 * implementacao (ex.: task-api consultando o indice de compartilhamentos).
 */
public interface TenantAccessValidator {

	/**
	 * @param homeTenant      detrasoftId do token (nunca nulo)
	 * @param requestedTenant tenant pedido no header (ja validado: somente digitos)
	 * @return true para permitir a requisicao no schema requestedTenant
	 */
	boolean canAccess(String homeTenant, String requestedTenant);
}
