package com.taskflow.tracing;

import org.junit.jupiter.api.Test;

import com.taskflow.controller.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que {@link TraceIdFilter} propaga el traceId/requestId que llega del cliente, y
 * que genera uno nuevo cuando no viene en el request.
 */
class TraceIdFilterIT extends AbstractIntegrationTest {

    @Test
    void propagaElRequestIdQueLlegaDelCliente() throws Exception {
        String incoming = "mi-request-id-de-prueba-123";

        mockMvc.perform(get("/api/projects").header(TraceIdFilter.REQUEST_ID_HEADER, incoming))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, incoming))
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, incoming));
    }

    @Test
    void generaUnTraceIdCuandoElClienteNoMandaUno() throws Exception {
        var result = mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andReturn();

        String traceId = result.getResponse().getHeader(TraceIdFilter.REQUEST_ID_HEADER);
        org.assertj.core.api.Assertions.assertThat(traceId).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(traceId);
    }
}
