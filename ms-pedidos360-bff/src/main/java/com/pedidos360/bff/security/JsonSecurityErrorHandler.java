package com.pedidos360.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Respuestas JSON para 401 (sin token / token inválido) y 403 (rol sin permiso)
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {

        String detail = e instanceof InvalidBearerTokenException
                ? "Token inválido: " + e.getMessage()
                : "Se requiere un token Bearer válido";

        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        write(response, HttpStatus.UNAUTHORIZED, detail, request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {

        write(response, HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta operación", request.getRequestURI());
    }

    private static void write(HttpServletResponse response, HttpStatus status,
                              String detail, String path) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":" + status.value()
                        + ",\"error\":\"" + status.getReasonPhrase()
                        + "\",\"detail\":\"" + escape(detail)
                        + "\",\"path\":\"" + escape(path) + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
