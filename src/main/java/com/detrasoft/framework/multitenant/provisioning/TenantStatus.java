package com.detrasoft.framework.multitenant.provisioning;

/** Situacao do tenant de uma conta neste servico. */
public enum TenantStatus {
	/** Schema migrado e dados padrao aplicados: pode usar. */
	READY,
	/** Criacao pedida ou em andamento. */
	PENDING
}
