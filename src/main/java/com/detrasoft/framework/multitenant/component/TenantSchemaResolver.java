package com.detrasoft.framework.multitenant.component;

import com.detrasoft.framework.multitenant.config.HibernateConfig;
import com.detrasoft.framework.multitenant.context.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver {

	@Override
	public String resolveCurrentTenantIdentifier() {
		String t = TenantContext.getTenantSchema();
		if (t != null) {
			return t;
		} else {
			return HibernateConfig.DEFAULT_SCHEMA;
		}
	}

	@Override
	public boolean validateExistingCurrentSessions() {
		return true;
	}

}