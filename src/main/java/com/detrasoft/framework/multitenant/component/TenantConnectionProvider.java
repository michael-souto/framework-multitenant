package com.detrasoft.framework.multitenant.component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import com.detrasoft.framework.multitenant.config.DatabaseSettings;

@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider, HibernatePropertiesCustomizer {

	private static final long serialVersionUID = 1348353870772468815L;
	private static final Logger logger = LoggerFactory.getLogger(TenantConnectionProvider.class);
	private final DataSource datasource;

	public TenantConnectionProvider(DataSource dataSource) {
		this.datasource = dataSource;
	}

	@Override
	public Connection getAnyConnection() throws SQLException {
		return datasource.getConnection();
	}

	@Override
	public void releaseAnyConnection(Connection connection) throws SQLException {
		connection.close();
	}

	@Override
	public Connection getConnection(Object tenantIdentifier) throws SQLException {
		String tenantId = (tenantIdentifier != null) ? tenantIdentifier.toString() : DatabaseSettings.DEFAULT_TENANT;
		logger.debug("Get connection for tenant {}", tenantId);
		final Connection connection = getAnyConnection();
		connection.setSchema(tenantId);
		return connection;
	}

	@Override
	public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {
		String tenantId = (tenantIdentifier != null) ? tenantIdentifier.toString() : DatabaseSettings.DEFAULT_TENANT;
		logger.debug("Release connection for tenant {}", tenantId);
		connection.setSchema(DatabaseSettings.DEFAULT_TENANT);
		releaseAnyConnection(connection);
	}

	@Override
	public boolean supportsAggressiveRelease() {
		return false;
	}

	@Override
	public boolean isUnwrappableAs(Class<?> unwrapType) {
		return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
	}

	@Override
	public <T> T unwrap(Class<T> unwrapType) {
		if (isUnwrappableAs(unwrapType)) {
			return unwrapType.cast(this);
		}
		return null;
	}

	@Override
	public void customize(Map<String, Object> hibernateProperties) {
		hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, this);
	}
}