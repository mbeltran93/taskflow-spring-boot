package com.taskflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de concurrencia simple: dispara varios requests en paralelo contra el mismo proyecto/tarea
 * para verificar que no se pierden escrituras ni se rompe el servidor bajo acceso simultaneo.
 * Es un tipo de test que no existia todavia en el repo (los *ControllerIT y *Test son secuenciales).
 */
class ConcurrentTaskOperationsIT extends AbstractIntegrationTest {

    private static final int HILOS = 20;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void crearTareasEnParalelo_sobreElMismoProyecto_noPierdeNingunaYNoGeneraIdsDuplicados() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Concurrente", "concurrente-create@taskflow.dev", "password123");

        String projectBody = """
                {"name": "Proyecto concurrencia", "description": "desc", "ownerId": %d}
                """.formatted(owner.id());
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();

        List<Integer> statusCodes = new CopyOnWriteArrayList<>();
        List<Long> idsCreados = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        CountDownLatch arranqueSimultaneo = new CountDownLatch(HILOS);
        List<Callable<Void>> tareas = IntStream.range(0, HILOS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    // Todos los hilos avisan que ya estan listos y esperan a que el ultimo llegue,
                    // para disparar los requests lo mas simultaneamente posible.
                    arranqueSimultaneo.countDown();
                    arranqueSimultaneo.await();
                    String body = """
                            {"title": "Tarea concurrente %d", "projectId": %d}
                            """.formatted(i, projectId);
                    var result = mockMvc.perform(post("/api/tasks")
                            .header("Authorization", "Bearer " + owner.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andReturn();
                    int sc = result.getResponse().getStatus();
                    statusCodes.add(sc);
                    if (sc == 201) {
                        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
                        idsCreados.add(id);
                    }
                    return null;
                })
                .collect(Collectors.toList());

        try {
            List<java.util.concurrent.Future<Void>> futures = pool.invokeAll(tareas);
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statusCodes).hasSize(HILOS);
        assertThat(statusCodes).allMatch(sc -> sc == 201);

        Set<Long> idsUnicos = idsCreados.stream().collect(Collectors.toSet());
        assertThat(idsCreados).hasSize(HILOS);
        assertThat(idsUnicos).hasSize(HILOS); // ningun id duplicado

        mockMvc.perform(get("/api/tasks").param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(HILOS));
    }

    @Test
    void cambiosDeStatusEnParalelo_sobreLaMismaTarea_terminanEnUnEstadoValidoSinErrores() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Concurrente2", "concurrente-status@taskflow.dev", "password123");

        String projectBody = """
                {"name": "Proyecto concurrencia status", "description": "desc", "ownerId": %d}
                """.formatted(owner.id());
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();

        String taskBody = """
                {"title": "Tarea para concurrencia de status", "projectId": %d}
                """.formatted(projectId);
        String taskResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(taskResponse).get("id").asLong();

        String[] estados = {"TODO", "IN_PROGRESS", "DONE"};
        List<Integer> statusCodes = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(HILOS);
        try {
            List<java.util.concurrent.Future<?>> futures = IntStream.range(0, HILOS)
                    .mapToObj(i -> pool.submit(() -> {
                        String nuevoEstado = estados[i % estados.length];
                        try {
                            var result = mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                                    .header("Authorization", "Bearer " + owner.token())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"status": "%s"}
                                            """.formatted(nuevoEstado))).andReturn();
                            statusCodes.add(result.getResponse().getStatus());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .collect(Collectors.toList());
            for (var f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(statusCodes).hasSize(HILOS);
        assertThat(statusCodes).allMatch(sc -> sc == 200);

        String finalTask = mockMvc.perform(get("/api/tasks/" + taskId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String estadoFinal = objectMapper.readTree(finalTask).get("status").asText();
        assertThat(estadoFinal).isIn("TODO", "IN_PROGRESS", "DONE");
    }
}
