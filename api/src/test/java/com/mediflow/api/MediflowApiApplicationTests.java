package com.mediflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the full context against the local Postgres (see application.yml),
 * which runs the Flyway migrations.
 */
@SpringBootTest
class MediflowApiApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsAndMigrationsCreateTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains("patient", "doctor", "appointment", "flyway_schema_history");
    }
}
