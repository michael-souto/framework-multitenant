package com.detrasoft.framework.multitenant.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.detrasoft.framework.multitenant.interceptor.TenantInterceptor;

@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantInterceptorConfig implements WebMvcConfigurer {

	@Autowired
	TenantInterceptor tenantInterceptor;

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tenantInterceptor);
	}

}