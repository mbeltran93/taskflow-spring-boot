package com.taskflow.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Trazabilidad por request: genera (o propaga si ya viene del cliente/otro servicio) un
 * traceId/requestId, lo deja en el MDC de SLF4J durante todo el ciclo de vida del request
 * (asi aparece en todos los logs emitidos mientras se procesa, ver logback-spring.xml) y lo
 * devuelve en la respuesta para que el cliente pueda correlacionar.
 *
 * <p>Se registra con la precedencia mas alta posible ({@link FilterRegistrationConfig}) para
 * que corra antes que la cadena de filtros de Spring Security (incluido {@code JwtAuthFilter})
 * y asi esos logs tambien queden con el traceId.</p>
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = firstNonBlank(request.getHeader(REQUEST_ID_HEADER), request.getHeader(TRACE_ID_HEADER));
        String traceId = (incoming != null) ? incoming : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(REQUEST_ID_HEADER, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
