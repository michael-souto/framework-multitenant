package com.detrasoft.framework.multitenant.component;

import com.detrasoft.framework.multitenant.config.DatabaseSettings;
import com.detrasoft.framework.multitenant.context.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver, HibernatePropertiesCustomizer {

	@Override
	public String resolveCurrentTenantIdentifier() {
		String t = TenantContext.getTenantSchema();
		if (t != null) {
			return t;
		} else {
			return DatabaseSettings.DEFAULT_TENANT;
		}
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}
	@Override
	public void customize(Map<String, Object> hibernateProperties) {
		hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
	}
}