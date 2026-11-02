package com.detrasoft.framework.multitenant.provisioning;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.json.JsonParserFactory;

/**
 * Pedido de criacao do tenant de uma conta Detrasoft.
 *
 * O corpo e o UserDTO que o detrasoft-core-api publica no cadastro
 * ({@code detrasoftId}, {@code language}, {@code email}, {@code firstName}). A
 * leitura usa o parser JSON do proprio Spring Boot para nao depender da versao do
 * Jackson de cada servico nem de reflexao no binario nativo.
 *
 * @param tenant   schema do tenant (detrasoftId), sempre so digitos
 * @param language idioma dos dados padrao (pode ser nulo)
 * @param payload  corpo original, devolvido no evento de conclusao
 */
public record TenantRequest(String tenant, String language, String email, String firstName, byte[] payload) {

	private static final Pattern TENANT = Pattern.compile("^\\d{1,19}$");

	public TenantRequest {
		if (tenant == null || !TENANT.matcher(tenant).matches()) {
			throw new IllegalArgumentException("Tenant invalido: " + tenant);
		}
	}

	public static boolean isValidTenant(String tenant) {
		return tenant != null && TENANT.matcher(tenant).matches();
	}

	/** Pedido feito pela propria aplicacao (ex.: usuario logado sem tenant). */
	public static TenantRequest of(String tenant, String language, String email, String firstName) {
		String json = "{\"detrasoftId\":" + tenant
				+ ",\"language\":" + quote(language)
				+ ",\"email\":" + quote(email)
				+ ",\"firstName\":" + quote(firstName) + "}";
		return new TenantRequest(tenant, language, email, firstName, json.getBytes(StandardCharsets.UTF_8));
	}

	public static TenantRequest fromJson(byte[] body) {
		Map<String, Object> map = JsonParserFactory.getJsonParser().parseMap(new String(body, StandardCharsets.UTF_8));
		return new TenantRequest(asId(map.get("detrasoftId")), asText(map.get("language")),
				asText(map.get("email")), asText(map.get("firstName")), body);
	}

	private static String asId(Object value) {
		if (value instanceof Number n) {
			return Long.toString(n.longValue());
		}
		return value == null ? null : value.toString().trim();
	}

	private static String asText(Object value) {
		return value == null ? null : value.toString();
	}

	private static String quote(String value) {
		if (value == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.append('"').toString();
	}
}
