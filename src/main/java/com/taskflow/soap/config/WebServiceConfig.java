package com.taskflow.soap.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Configuracion del endpoint SOAP (Spring-WS), contract-first a partir de
 * {@code src/main/resources/xsd/projects.xsd}.
 *
 * <p>Convive con los controllers REST existentes: el dominio Project se expone tanto en
 * {@code /api/projects} (JSON) como en {@code /ws} (SOAP) - literalmente "REST and SOAP APIs"
 * sobre el mismo servicio. {@code @EnableWs} registra automaticamente el
 * {@code PayloadRootAnnotationMethodEndpointMapping} que enruta cada mensaje al metodo del
 * {@code @Endpoint} anotado con {@code @PayloadRoot} correspondiente.</p>
 */
@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "projects")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema projectsSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("ProjectsPort");
        wsdl11Definition.setLocationUri("/ws");
        wsdl11Definition.setTargetNamespace("http://taskflow.com/soap/projects");
        wsdl11Definition.setSchema(projectsSchema);
        return wsdl11Definition;
    }

    @Bean
    public XsdSchema projectsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/projects.xsd"));
    }
}
