package com.taskflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helper para registrar y loguear usuarios de prueba en los tests de integracion.
 */
final class TestAuthSupport {

    private TestAuthSupport() {
    }

    record RegisteredUser(Long id, String email, String token) {
    }

    static RegisteredUser registerAndLogin(MockMvc mockMvc, ObjectMapper objectMapper, String name, String email, String password) throws Exception {
        String registerJson = """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);

        String userResponse = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode userNode = objectMapper.readTree(userResponse);
        Long userId = userNode.get("id").asLong();

        String loginJson = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginNode = objectMapper.readTree(loginResponse);
        String token = loginNode.get("token").asText();

        return new RegisteredUser(userId, email, token);
    }
}
