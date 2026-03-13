package com.example.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class DatabaseHealthController {

    private final DataSource dataSource;

    public DatabaseHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> checkDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5-second timeout
            if (isValid) {
                response.put("status", "UP");
                response.put("database", "AWS RDS PostgreSQL");
                response.put("url", connection.getMetaData().getURL());
                response.put("message", "Database connection is healthy");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "DOWN");
                response.put("database", "AWS RDS PostgreSQL");
                response.put("message", "Connection obtained but validation failed");
                return ResponseEntity.status(503).body(response);
            }
        } catch (SQLException e) {
            response.put("status", "DOWN");
            response.put("database", "AWS RDS PostgreSQL");
            response.put("message", "Cannot connect to database: " + e.getMessage());
            response.put("errorCode", e.getErrorCode());
            return ResponseEntity.status(503).body(response);
        }
    }
}
