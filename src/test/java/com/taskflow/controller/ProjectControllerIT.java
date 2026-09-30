package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void crudCompleto_deProyecto() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Gina", "gina@taskflow.dev", "password123");

        String createBody = """
                {"name": "TaskFlow", "description": "Portafolio backend", "ownerId": %d}
                """.formatted(owner.id());

        String createResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("TaskFlow"))
                .andReturn().getResponse().getContentAsString();

        long projectId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk());

        String updateBody = """
                {"name": "TaskFlow v2", "description": "actualizado", "ownerId": %d}
                """.formatted(owner.id());

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TaskFlow v2"));

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_sinToken_devuelve401Oo403() throws Exception {
        String body = """
                {"name": "Sin auth", "description": "no deberia crearse", "ownerId": 1}
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void create_conOwnerInexistente_devuelve404() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Hugo", "hugo@taskflow.dev", "password123");

        String body = """
                {"name": "Proyecto", "description": "desc", "ownerId": 999999}
                """;

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }
}
