package com.taskflow.soap.endpoint;

import com.taskflow.dto.ProjectResponse;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.service.ProjectService;
import com.taskflow.soap.model.GetProjectByIdRequest;
import com.taskflow.soap.model.GetProjectByIdResponse;
import com.taskflow.soap.model.ListProjectsRequest;
import com.taskflow.soap.model.ListProjectsResponse;
import com.taskflow.soap.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Expone el dominio Project por SOAP, contract-first a partir de
 * {@code src/main/resources/xsd/projects.xsd}, consumiendo el mismo {@link ProjectService}
 * que usa {@link com.taskflow.controller.ProjectController} para REST. No duplica logica de
 * negocio: solo traduce entre el modelo SOAP (JAXB, generado del XSD) y el DTO interno.
 */
@Endpoint
public class ProjectSoapEndpoint {

    private static final Logger log = LoggerFactory.getLogger(ProjectSoapEndpoint.class);

    private static final String NAMESPACE_URI = "http://taskflow.com/soap/projects";

    private final ProjectService projectService;

    public ProjectSoapEndpoint(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getProjectByIdRequest")
    @ResponsePayload
    public GetProjectByIdResponse getProjectById(@RequestPayload GetProjectByIdRequest request) {
        log.info("[SOAP] getProjectById id={}", request.getId());
        ProjectResponse projectResponse;
        try {
            projectResponse = projectService.findById(request.getId());
        } catch (ResourceNotFoundException ex) {
            throw new ProjectNotFoundSoapException(ex.getMessage());
        }

        GetProjectByIdResponse response = new GetProjectByIdResponse();
        response.setProject(toSoapProject(projectResponse));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listProjectsRequest")
    @ResponsePayload
    public ListProjectsResponse listProjects(@RequestPayload ListProjectsRequest request) {
        log.info("[SOAP] listProjects");
        ListProjectsResponse response = new ListProjectsResponse();
        projectService.findAll().forEach(p -> response.getProject().add(toSoapProject(p)));
        return response;
    }

    private Project toSoapProject(ProjectResponse source) {
        Project soapProject = new Project();
        soapProject.setId(source.id());
        soapProject.setName(source.name());
        soapProject.setDescription(source.description());
        soapProject.setOwnerId(source.ownerId());
        soapProject.setCreatedAt(toXmlCalendar(source.createdAt()));
        return soapProject;
    }

    private XMLGregorianCalendar toXmlCalendar(java.time.Instant instant) {
        GregorianCalendar calendar = GregorianCalendar.from(instant.atZone(java.time.ZoneOffset.UTC));
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo convertir la fecha para el SOAP response", e);
        }
    }
}
