package com.pipeline.api.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Configuration
public class FlywayConfiguration {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            DataSource dataSource,
            Environment environment) {
        return configuration -> {
            configuration.baselineOnMigrate(true);

            if (usesManagedDatabase(environment) && tableExists(dataSource, "execution_results")) {
                // Table was created outside Flyway (or a prior partial run); treat schema as current.
                configuration.baselineVersion("1");
            } else {
                configuration.baselineVersion("0");
            }
        };
    }

    private static boolean usesManagedDatabase(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }
        return databaseUrl != null && !databaseUrl.isBlank();
    }

    private static boolean tableExists(DataSource dataSource, String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet tables = metadata.getTables(catalog, "public", tableName, new String[]{"TABLE"})) {
                return tables.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to inspect database schema for Flyway baseline", exception);
        }
    }
}
