package com.detrasoft.framework.multitenant.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.detrasoft.framework.core.context.GenericContext;
import com.detrasoft.framework.multitenant.context.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.detrasoft.framework.multitenant.config.HibernateConfig;

@Component
public class TenantInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		String tenant = request.getHeader("tenant");

		if (tenant == null) {
			tenant = GenericContext.getContexts("id_detrasoft");
		}

		if (tenant != null) {
			TenantContext.setTenantSchema(tenant);
		} else {
			TenantContext
					.setTenantSchema(HibernateConfig.DEFAULT_SCHEMA);
		}
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		TenantContext.setTenantSchema(null);
	}

}
