package com.detrasoft.framework.multitenant.interceptor;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.detrasoft.framework.core.context.GenericContext;
import com.detrasoft.framework.multitenant.config.DatabaseSettings;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolve o schema da requisicao.
 *
 * O tenant vem do detrasoftId do token. O header "tenant" so troca o schema
 * quando e igual ao tenant do token ou quando um {@link TenantAccessValidator}
 * autoriza explicitamente o acesso. Antes o header era aceito sem validacao, o
 * que permitia a qualquer usuario autenticado ler/escrever no schema de outra
 * conta apenas enviando "tenant: <id>".
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

	private static final String TENANT_HEADER = "tenant";
	private static final Pattern TENANT_PATTERN = Pattern.compile("^\\d{1,19}$");

	private final ObjectProvider<TenantAccessValidator> validators;

	public TenantInterceptor(ObjectProvider<TenantAccessValidator> validators) {
		this.validators = validators;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		String homeTenant = GenericContext.getContexts("detrasoftId");
		String requested = request.getHeader(TENANT_HEADER);

		if (requested == null || requested.isBlank() || requested.equals(homeTenant)) {
			GenericContext.setContexts("detrasoftId",
					homeTenant != null ? homeTenant : DatabaseSettings.DEFAULT_TENANT);
			return true;
		}

		if (homeTenant == null || !TENANT_PATTERN.matcher(requested).matches() || !isAllowed(homeTenant, requested)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return false;
		}

		GenericContext.setContexts("detrasoftId", requested);
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) throws Exception {
		// afterCompletion roda mesmo com excecao no handler (postHandle nao).
		GenericContext.setContexts("detrasoftId", null);
	}

	private boolean isAllowed(String homeTenant, String requested) {
		return validators.orderedStream().anyMatch(v -> v.canAccess(homeTenant, requested));
	}
}
