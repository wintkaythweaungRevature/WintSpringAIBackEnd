package com.example.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_returns400WithErrorMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Email already registered");

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Email already registered");
    }

    @Test
    void handleBadRequest_includesExceptionMessageInBody() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid password");

        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(ex);

        assertThat(response.getBody().get("error")).isEqualTo("Invalid password");
    }

    @Test
    void handleGeneric_returns500WithGenericMessage() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, String>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An error occurred");
    }

    @Test
    void handleGeneric_doesNotExposeInternalExceptionMessage() {
        Exception ex = new RuntimeException("Secret internal detail");

        ResponseEntity<Map<String, String>> response = handler.handleGeneric(ex);

        assertThat(response.getBody().get("error")).doesNotContain("Secret internal detail");
    }

    @Test
    void handleGeneric_handlesNullMessageException() {
        Exception ex = new RuntimeException((String) null);

        ResponseEntity<Map<String, String>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsKey("error");
    }
}
