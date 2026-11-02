package com.detrasoft.framework.multitenant.provisioning.amqp;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningProperties;

/**
 * Topologia do RabbitMQ usada no provisionamento de tenant, declarada pelo proprio
 * servico para nao depender de _infra/scripts/RabbitMQ/init-topology.sh ter rodado.
 *
 * <ul>
 * <li>{@code ex.customer.events} (fanout) -> {@code q.tenant.<produto>.create};</li>
 * <li>{@code ex.customer.completed} (direct) -> {@code q.tenant.<produto>.completed},
 * com a routing key {@code rk.tenant.<produto>.completed}.</li>
 * </ul>
 * Mesma topologia do script (duraveis, sem argumentos extras), entao declarar dos
 * dois lados e idempotente.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "multitenant.provisioning", name = "enabled", havingValue = "true")
public class AmqpTenantProvisioningTopology {

	@Bean
	public Declarables tenantProvisioningTopology(TenantProvisioningProperties properties) {
		FanoutExchange events = new FanoutExchange(properties.getEventsExchange(), true, false);
		DirectExchange completed = new DirectExchange(properties.getCompletedExchange(), true, false);
		Queue createQueue = new Queue(properties.getCreateQueue(), true, false, false);
		Queue completedQueue = new Queue(properties.getCompletedQueue(), true, false, false);
		return new Declarables(events, completed, createQueue, completedQueue,
				BindingBuilder.bind(createQueue).to(events),
				BindingBuilder.bind(completedQueue).to(completed).with(properties.getCompletedRoutingKey()));
	}
}
