package com.detrasoft.framework.multitenant.provisioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuracao do provisionamento de tenant ({@code multitenant.provisioning.*}).
 *
 * <pre>
 * multitenant:
 *   provisioning:
 *     enabled: true          # liga o listener de criacao (precisa de spring-amqp)
 *     product: notes         # nome do produto nas filas: q.tenant.notes.create / rk.tenant.notes.completed
 *     seed-location: db/implantation/{language}   # opcional: SQL de dados padrao por idioma
 *     default-language: pt
 * </pre>
 *
 * As filas seguem a topologia de _infra/scripts/RabbitMQ/init-topology.sh e podem
 * ser trocadas uma a uma quando um produto fugir do padrao.
 */
@Component
public class TenantProvisioningProperties {

	@Value("${multitenant.provisioning.enabled:false}")
	private boolean enabled;

	@Value("${multitenant.provisioning.product:}")
	private String product;

	@Value("${multitenant.provisioning.create-queue:q.tenant.${multitenant.provisioning.product:}.create}")
	private String createQueue;

	@Value("${multitenant.provisioning.completed-exchange:ex.customer.completed}")
	private String completedExchange;

	@Value("${multitenant.provisioning.completed-routing-key:rk.tenant.${multitenant.provisioning.product:}.completed}")
	private String completedRoutingKey;

	@Value("${multitenant.provisioning.seed-location:}")
	private String seedLocation;

	@Value("${multitenant.provisioning.default-language:pt}")
	private String defaultLanguage;

	public boolean isEnabled() {
		return enabled;
	}

	public String getProduct() {
		return product;
	}

	public String getCreateQueue() {
		return createQueue;
	}

	public String getCompletedExchange() {
		return completedExchange;
	}

	public String getCompletedRoutingKey() {
		return completedRoutingKey;
	}

	public String getSeedLocation() {
		return seedLocation;
	}

	public String getDefaultLanguage() {
		return defaultLanguage;
	}
}
