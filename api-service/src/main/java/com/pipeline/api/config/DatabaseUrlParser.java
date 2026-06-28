package com.pipeline.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

final class DatabaseUrlParser {

    record ParsedDatabaseUrl(String jdbcUrl, String username, String password) {}

    private DatabaseUrlParser() {}

    static ParsedDatabaseUrl parse(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is empty");
        }

        String normalized = databaseUrl.trim();
        if (normalized.startsWith("postgres://")) {
            normalized = "postgresql://" + normalized.substring("postgres://".length());
        }
        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must use a postgresql:// URL");
        }

        URI uri = URI.create(normalized.replaceFirst("^postgresql://", "http://"));

        String userInfo = uri.getUserInfo();
        if (userInfo == null || userInfo.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL must include credentials");
        }

        String[] userParts = userInfo.split(":", 2);
        String username = decodeComponent(userParts[0]);
        String password = userParts.length > 1 ? decodeComponent(userParts[1]) : "";

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL must include a host");
        }

        int port = uri.getPort() > 0 ? uri.getPort() : 5432;

        String database = uri.getPath();
        if (database == null || database.isBlank() || "/".equals(database)) {
            throw new IllegalArgumentException("DATABASE_URL must include a database name");
        }
        if (database.startsWith("/")) {
            database = database.substring(1);
        }

        String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database + toJdbcQuery(uri.getQuery());
        return new ParsedDatabaseUrl(jdbcUrl, username, password);
    }

    private static String decodeComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String toJdbcQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        String filtered = Arrays.stream(query.split("&"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .filter(part -> !part.startsWith("channel_binding="))
                .collect(Collectors.joining("&"));

        return filtered.isBlank() ? "" : "?" + filtered;
    }
}
