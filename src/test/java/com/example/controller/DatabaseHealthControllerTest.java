package com.example.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.Controller.DatabaseHealthController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = DatabaseHealthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class DatabaseHealthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DataSource dataSource;
    @MockBean com.example.service.JwtService jwtService;

    @Test
    void checkDb_returnsUpWhenConnectionIsValid() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn("jdbc:postgresql://rds.example.com:5432/mydb");

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("AWS RDS PostgreSQL"))
                .andExpect(jsonPath("$.message").value("Database connection is healthy"));
    }

    @Test
    void checkDb_returns503WhenConnectionIsInvalid() throws Exception {
        Connection connection = mock(Connection.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(5)).thenReturn(false);

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.message").value("Connection obtained but validation failed"));
    }

    @Test
    void checkDb_returns503WhenSQLExceptionThrown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused", "08001", 1));

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot connect to database")));
    }

    @Test
    void checkDb_includesErrorCodeOnSQLException() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Timeout", "08006", 500));

        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value(500));
    }
}
