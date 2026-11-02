package com.detrasoft.framework.multitenant.provisioning;

/**
 * Como o servico pede a criacao do proprio tenant. A implementacao padrao publica
 * na fila de criacao do produto (amqp); sem mensageria, a criacao roda local.
 */
public interface TenantProvisioningRequester {

	void request(TenantRequest request);
}
