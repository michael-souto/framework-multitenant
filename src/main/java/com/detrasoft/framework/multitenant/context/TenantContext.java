package com.detrasoft.framework.multitenant.context;

import com.detrasoft.framework.core.context.GenericContext;

public class TenantContext {

	public static String getTenantSchema() {
		return GenericContext.getContexts("tenant");
	}

	public static void setTenantSchema(String value) {
		GenericContext.setContexts("tenant", value);
	}
}
