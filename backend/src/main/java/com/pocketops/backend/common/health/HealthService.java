package com.pocketops.backend.common.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthResponse currentHealth() {
        Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (ping == null || ping != 1) {
            throw new IllegalStateException("Database ping failed.");
        }

        return new HealthResponse("UP", "UP");
    }
}
