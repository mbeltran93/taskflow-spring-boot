package com.taskflow.soap;

import com.taskflow.entity.Project;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.soap.model.GetProjectByIdRequest;
import com.taskflow.soap.model.GetProjectByIdResponse;
import com.taskflow.soap.model.ListProjectsRequest;
import com.taskflow.soap.model.ListProjectsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba el endpoint SOAP con un cliente SOAP real ({@link WebServiceTemplate}), contra la
 * app completa levantada en un puerto aleatorio: serializa/deserializa XML de verdad sobre
 * HTTP, no solo invoca el metodo Java del endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProjectSoapEndpointIT {

    private static final String NAMESPACE_URI = "http://taskflow.com/soap/projects";

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectRepository projectRepository;

    private WebServiceTemplate webServiceTemplate;

    @BeforeEach
    void setUp() {
        projectRepository.deleteAll();

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.taskflow.soap.model");

        webServiceTemplate = new WebServiceTemplate(marshaller, marshaller);
        webServiceTemplate.setDefaultUri("http://localhost:" + port + "/ws");
    }

    @Test
    void getProjectById_devuelveElProyectoPorSoap() {
        Project saved = projectRepository.save(Project.builder()
                .name("Proyecto SOAP")
                .description("creado directo via repositorio para el test SOAP")
                .ownerId(1L)
                .build());

        GetProjectByIdRequest request = new GetProjectByIdRequest();
        request.setId(saved.getId());

        GetProjectByIdResponse response = (GetProjectByIdResponse) webServiceTemplate.marshalSendAndReceive(request);

        assertThat(response.getProject()).isNotNull();
        assertThat(response.getProject().getId()).isEqualTo(saved.getId());
        assertThat(response.getProject().getName()).isEqualTo("Proyecto SOAP");
        assertThat(response.getProject().getOwnerId()).isEqualTo(1L);
    }

    @Test
    void getProjectById_conIdInexistente_devuelveSoapFault() {
        GetProjectByIdRequest request = new GetProjectByIdRequest();
        request.setId(999999L);

        assertThrows(SoapFaultClientException.class,
                () -> webServiceTemplate.marshalSendAndReceive(request));
    }

    @Test
    void listProjects_devuelveTodosLosProyectosPorSoap() {
        projectRepository.save(Project.builder().name("Proyecto A").ownerId(1L).build());
        projectRepository.save(Project.builder().name("Proyecto B").ownerId(1L).build());

        ListProjectsResponse response =
                (ListProjectsResponse) webServiceTemplate.marshalSendAndReceive(new ListProjectsRequest());

        assertThat(response.getProject()).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder("Proyecto A", "Proyecto B");
    }
}
