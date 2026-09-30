package com.taskflow.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base para los tests de integracion de los controllers.
 *
 * <p>Usa el perfil "test" (H2 en memoria, en modo compatibilidad PostgreSQL, con el esquema
 * generado por Hibernate en vez de Flyway) en lugar de Testcontainers: en este entorno Windows,
 * Docker Desktop expone un pipe que el cliente docker-java que trae Testcontainers 1.20.x no
 * sabe resolver (falla con "BadRequestException Status 400" al listar el daemon), aunque el
 * Docker de la maquina funciona perfectamente para `docker compose`. H2 + @AutoConfigureMockMvc
 * es la alternativa explicitamente aceptada para mantener los tests simples y reproducibles en
 * cualquier maquina sin depender de esa integracion.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}
