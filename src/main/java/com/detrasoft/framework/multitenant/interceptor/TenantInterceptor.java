package com.detrasoft.framework.multitenant.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.detrasoft.framework.multitenant.context.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.detrasoft.framework.multitenant.config.HibernateConfig;
import br.com.detrasoft.framework.security.context.UsuarioContext;

@Component
public class TenantInterceptor extends HandlerInterceptorAdapter {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		var id_detrasoft = UsuarioContext.getIdDetrasoft();

		String headerNames = request.getHeader("enviroment");

		if (headerNames != null && id_detrasoft == null) {
			TenantContext.setTenantSchema(headerNames);
			UsuarioContext.setIdDetrasoft(Long.parseLong(headerNames));
		} else {
			TenantContext
					.setTenantSchema(id_detrasoft != null ? id_detrasoft.toString() : HibernateConfig.DEFAULT_SCHEMA);
		}
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {
		TenantContext.setTenantSchema(null);
	}

}
