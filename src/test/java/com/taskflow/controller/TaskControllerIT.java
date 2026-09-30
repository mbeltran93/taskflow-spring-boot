package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private long createProject(TestAuthSupport.RegisteredUser owner) throws Exception {
        String body = """
                {"name": "Proyecto Tareas", "description": "desc", "ownerId": %d}
                """.formatted(owner.id());

        String response = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void crudCompleto_deTarea_yCambioDeStatus() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Ivan", "ivan@taskflow.dev", "password123");
        long projectId = createProject(owner);

        String createBody = """
                {"title": "Diseñar esquema DB", "description": "tablas y relaciones", "projectId": %d}
                """.formatted(projectId);

        String createResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn().getResponse().getContentAsString();

        long taskId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Diseñar esquema DB"));

        mockMvc.perform(get("/api/tasks").param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/tasks")
                        .param("projectId", String.valueOf(projectId))
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(taskId));

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + taskId))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_conProyectoInexistente_devuelve404() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Julia", "julia@taskflow.dev", "password123");

        String body = """
                {"title": "Tarea huerfana", "projectId": 999999}
                """;

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_sinToken_esRechazado() throws Exception {
        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DONE"}
                                """))
                .andExpect(status().is4xxClientError());
    }
}
