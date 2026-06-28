package com.pipeline.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseUrlParserTest {

    @Test
    void parsesNeonStyleUrl() {
        DatabaseUrlParser.ParsedDatabaseUrl parsed = DatabaseUrlParser.parse(
                "postgresql://neondb_owner:secret@ep-example-pooler.us-east-1.aws.neon.tech/neondb"
                        + "?sslmode=require&channel_binding=require");

        assertEquals("neondb_owner", parsed.username());
        assertEquals("secret", parsed.password());
        assertEquals(
                "jdbc:postgresql://ep-example-pooler.us-east-1.aws.neon.tech:5432/neondb?sslmode=require",
                parsed.jdbcUrl());
    }

    @Test
    void parsesLocalFallbackStyleComponents() {
        DatabaseUrlParser.ParsedDatabaseUrl parsed = DatabaseUrlParser.parse(
                "postgresql://codestream:codestream@localhost:5432/codestream");

        assertEquals(
                "jdbc:postgresql://localhost:5432/codestream",
                parsed.jdbcUrl());
    }
}
