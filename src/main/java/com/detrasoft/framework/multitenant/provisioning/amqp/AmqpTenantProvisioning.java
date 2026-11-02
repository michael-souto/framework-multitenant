package com.detrasoft.framework.multitenant.provisioning.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningProperties;
import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningRequester;
import com.detrasoft.framework.multitenant.provisioning.TenantProvisioningService;
import com.detrasoft.framework.multitenant.provisioning.TenantRequest;

/**
 * Provisionamento de tenant pelo RabbitMQ, igual em todos os produtos.
 *
 * <ul>
 * <li>Consome {@code q.tenant.<produto>.create}: chega pelo fanout do cadastro
 * (ex.customer.events) ou do proprio servico, quando um usuario de conta ja
 * existente entra sem tenant;</li>
 * <li>cria o tenant por {@link TenantProvisioningService#provision};</li>
 * <li>avisa o detrasoft-core-api em ex.customer.completed /
 * rk.tenant.&lt;produto&gt;.completed, com o mesmo corpo do pedido.</li>
 * </ul>
 * Falha sobe para a retentativa do listener. Corpo invalido e descartado com log,
 * porque reentregar nao o conserta.
 *
 * Ligado com {@code multitenant.provisioning.enabled=true}. Servicos sem
 * spring-amqp excluem este pacote da compilacao.
 */
@Component
@ConditionalOnProperty(prefix = "multitenant.provisioning", name = "enabled", havingValue = "true")
public class AmqpTenantProvisioning implements TenantProvisioningRequester {

	private static final Logger log = LoggerFactory.getLogger(AmqpTenantProvisioning.class);

	private final TenantProvisioningService service;
	private final TenantProvisioningProperties properties;
	private final RabbitTemplate rabbit;

	public AmqpTenantProvisioning(TenantProvisioningService service, TenantProvisioningProperties properties,
			RabbitTemplate rabbit) {
		if (properties.getProduct() == null || properties.getProduct().isBlank()) {
			throw new IllegalStateException(
					"multitenant.provisioning.product e obrigatorio com multitenant.provisioning.enabled=true");
		}
		this.service = service;
		this.properties = properties;
		this.rabbit = rabbit;
	}

	@RabbitListener(queues = "${multitenant.provisioning.create-queue:q.tenant.${multitenant.provisioning.product}.create}")
	public void onCreate(Message message) {
		TenantRequest request;
		try {
			request = TenantRequest.fromJson(message.getBody());
		} catch (RuntimeException e) {
			log.warn("[MULTITENANT] Pedido de tenant invalido descartado: {}", e.getMessage());
			return;
		}
		service.provision(request);
		rabbit.send(properties.getCompletedExchange(), properties.getCompletedRoutingKey(), json(request.payload()));
	}

	/** Pedido do proprio servico: entra na mesma fila (exchange padrao) e segue o mesmo caminho. */
	@Override
	public void request(TenantRequest request) {
		rabbit.send("", properties.getCreateQueue(), json(request.payload()));
	}

	private static Message json(byte[] body) {
		MessageProperties props = new MessageProperties();
		props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		props.setContentEncoding("UTF-8");
		props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		return new Message(body, props);
	}
}
