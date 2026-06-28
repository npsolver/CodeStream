package com.pipeline.api.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceConfiguration {

    @Bean
    @Primary
    public DataSource dataSource(Environment environment, DataSourceProperties properties) {
        String databaseUrl = resolveDatabaseUrl(environment);
        if (databaseUrl != null) {
            DatabaseUrlParser.ParsedDatabaseUrl parsed = DatabaseUrlParser.parse(databaseUrl);
            properties.setUrl(parsed.jdbcUrl());
            properties.setUsername(parsed.username());
            properties.setPassword(parsed.password());
        }
        return properties.initializeDataSourceBuilder().build();
    }

    private static String resolveDatabaseUrl(Environment environment) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return null;
        }
        return databaseUrl.trim();
    }
}
