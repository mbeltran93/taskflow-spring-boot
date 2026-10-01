package com.taskflow.tracing;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra {@link TraceIdFilter} con la maxima precedencia para que se ejecute antes que la
 * cadena de filtros de Spring Security (el {@code DelegatingFilterProxy} que Spring Boot
 * registra para ella) y, por lo tanto, antes que {@code JwtAuthFilter}: asi el traceId ya
 * esta en el MDC para cualquier log que se emita durante la autenticacion del request.
 */
@Configuration
public class TraceIdFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(traceIdFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
