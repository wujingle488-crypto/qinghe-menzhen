package com.commerce.cs.server.web;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        try (Connection connection = dataSource.getConnection()) {
            data.put("database", connection.isValid(2) ? "up" : "down");
        } catch (Exception ex) {
            data.put("status", "degraded");
            data.put("database", "down");
        }
        return data;
    }
}
