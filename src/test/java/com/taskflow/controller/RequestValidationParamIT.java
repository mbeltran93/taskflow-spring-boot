package com.taskflow.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests parametrizados de validacion de entrada (Bean Validation), con mas variedad de casos
 * invalidos de los que cubren los *ControllerIT existentes (que solo prueban uno o dos casos
 * de error por endpoint). Cubre registro de usuario, login, proyectos y tareas.
 */
class RequestValidationParamIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    // ---- POST /api/users (CreateUserRequest) ----

    static Stream<Arguments> usuariosInvalidos() {
        String nombreDe121Caracteres = "a".repeat(121);
        String passwordDe101Caracteres = "a".repeat(101);
        return Stream.of(
                Arguments.of("""
                        {"name": "", "email": "valido@taskflow.dev", "password": "password123"}
                        """, "name"),
                Arguments.of("""
                        {"name": "   ", "email": "valido@taskflow.dev", "password": "password123"}
                        """, "name"),
                Arguments.of(("""
                        {"name": "%s", "email": "valido@taskflow.dev", "password": "password123"}
                        """).formatted(nombreDe121Caracteres), "name"),
                Arguments.of("""
                        {"name": "Alguien", "email": "no-es-un-email", "password": "password123"}
                        """, "email"),
                Arguments.of("""
                        {"name": "Alguien", "email": "", "password": "password123"}
                        """, "email"),
                Arguments.of("""
                        {"name": "Alguien", "email": "valido@taskflow.dev", "password": "123"}
                        """, "password"),
                Arguments.of("""
                        {"name": "Alguien", "email": "valido@taskflow.dev", "password": ""}
                        """, "password"),
                Arguments.of(("""
                        {"name": "Alguien", "email": "valido@taskflow.dev", "password": "%s"}
                        """).formatted(passwordDe101Caracteres), "password")
        );
    }

    @ParameterizedTest(name = "[{index}] registro invalido -> 400 con error de {1}")
    @MethodSource("usuariosInvalidos")
    void registrarUsuario_conDatosInvalidos_devuelve400ConElCampoQueFalla(String body, String campoEsperado) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(campoEsperado + ":")));
    }

    // ---- POST /api/auth/login (LoginRequest) ----

    static Stream<Arguments> loginsInvalidos() {
        return Stream.of(
                Arguments.of("""
                        {"email": "", "password": "password123"}
                        """, "email"),
                Arguments.of("""
                        {"email": "no-es-un-email", "password": "password123"}
                        """, "email"),
                Arguments.of("""
                        {"email": "valido@taskflow.dev", "password": ""}
                        """, "password")
        );
    }

    @ParameterizedTest(name = "[{index}] login invalido -> 400 con error de {1}")
    @MethodSource("loginsInvalidos")
    void login_conDatosInvalidos_devuelve400ConElCampoQueFalla(String body, String campoEsperado) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(campoEsperado + ":")));
    }

    // ---- POST /api/projects (ProjectRequest) ----

    static Stream<Arguments> proyectosInvalidos() {
        String nombreDe161Caracteres = "p".repeat(161);
        String descripcionDe2001Caracteres = "d".repeat(2001);
        return Stream.of(
                Arguments.of("""
                        {"name": "", "description": "desc", "ownerId": OWNER_ID}
                        """, "name"),
                Arguments.of(("""
                        {"name": "%s", "description": "desc", "ownerId": OWNER_ID}
                        """).formatted(nombreDe161Caracteres), "name"),
                Arguments.of(("""
                        {"name": "Proyecto", "description": "%s", "ownerId": OWNER_ID}
                        """).formatted(descripcionDe2001Caracteres), "description"),
                Arguments.of("""
                        {"name": "Proyecto", "description": "desc", "ownerId": null}
                        """, "ownerId")
        );
    }

    @ParameterizedTest(name = "[{index}] proyecto invalido -> 400 con error de {1}")
    @MethodSource("proyectosInvalidos")
    void crearProyecto_conDatosInvalidos_devuelve400ConElCampoQueFalla(String bodyTemplate, String campoEsperado) throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Owner " + unico(), "owner" + unico() + "@taskflow.dev", "password123");

        String body = bodyTemplate.replace("OWNER_ID", String.valueOf(owner.id()));

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(campoEsperado + ":")));
    }

    // ---- POST /api/tasks (TaskRequest) ----

    static Stream<Arguments> tareasInvalidas() {
        String tituloDe201Caracteres = "t".repeat(201);
        String descripcionDe2001Caracteres = "d".repeat(2001);
        return Stream.of(
                Arguments.of("""
                        {"title": "", "projectId": PROJECT_ID}
                        """, "title"),
                Arguments.of(("""
                        {"title": "%s", "projectId": PROJECT_ID}
                        """).formatted(tituloDe201Caracteres), "title"),
                Arguments.of(("""
                        {"title": "Tarea", "description": "%s", "projectId": PROJECT_ID}
                        """).formatted(descripcionDe2001Caracteres), "description"),
                Arguments.of("""
                        {"title": "Tarea", "projectId": null}
                        """, "projectId")
        );
    }

    @ParameterizedTest(name = "[{index}] tarea invalida -> 400 con error de {1}")
    @MethodSource("tareasInvalidas")
    void crearTarea_conDatosInvalidos_devuelve400ConElCampoQueFalla(String bodyTemplate, String campoEsperado) throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Owner " + unico(), "ownertarea" + unico() + "@taskflow.dev", "password123");

        String projectBody = """
                {"name": "Proyecto de validacion", "description": "desc", "ownerId": %d}
                """.formatted(owner.id());

        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();
        String body = bodyTemplate.replace("PROJECT_ID", String.valueOf(projectId));

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(campoEsperado + ":")));
    }

    // ---- PATCH /api/tasks/{id}/status (TaskStatusUpdateRequest) ----

    @Test
    void cambiarStatus_sinStatus_devuelve400() throws Exception {
        TestAuthSupport.RegisteredUser owner = TestAuthSupport.registerAndLogin(
                mockMvc, objectMapper, "Owner " + unico(), "ownerstatus" + unico() + "@taskflow.dev", "password123");

        String projectBody = """
                {"name": "Proyecto status", "description": "desc", "ownerId": %d}
                """.formatted(owner.id());
        String projectResponse = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(projectResponse).get("id").asLong();

        String taskBody = """
                {"title": "Tarea sin status nuevo", "projectId": %d}
                """.formatted(projectId);
        String taskResponse = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(taskResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status:")));
    }

    /** Sufijo corto para que cada caso parametrizado use un email distinto y no choque con 409. */
    private static String unico() {
        return Base64.getEncoder().withoutPadding().encodeToString(
                Long.toString(System.nanoTime()).getBytes()).toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
