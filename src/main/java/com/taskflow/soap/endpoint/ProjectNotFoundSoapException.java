package com.taskflow.soap.endpoint;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Se traduce automaticamente a un SOAP Fault (cliente) gracias a {@code @SoapFault} + el
 * {@code SimpleSoapExceptionResolver} que registra Spring-WS por defecto para excepciones
 * de endpoint no controladas de otra forma.
 */
@SoapFault(faultCode = FaultCode.CLIENT, faultStringOrReason = "Proyecto no encontrado")
public class ProjectNotFoundSoapException extends RuntimeException {

    public ProjectNotFoundSoapException(String message) {
        super(message);
    }
}
