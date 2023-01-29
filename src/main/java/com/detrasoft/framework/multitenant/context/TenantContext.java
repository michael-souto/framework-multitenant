package com.detrasoft.framework.multitenant.context;

public class TenantContext {

	private static ThreadLocal<String> tenantSchema = new ThreadLocal<>();

	public static String getTenantSchema() {
		return tenantSchema.get();
	}

	public static void setTenantSchema(String uuid) {
		tenantSchema.set(uuid);
	}
}