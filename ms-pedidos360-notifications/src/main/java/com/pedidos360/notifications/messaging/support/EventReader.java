package com.pedidos360.notifications.messaging.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convierte el cuerpo JSON de un mensaje en un evento y valida sus campos.
 * Cualquier problema aquí es NO recuperable: reintentar el mismo mensaje fallaría igual.
 */
@Component
public class EventReader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public EventReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> T read(Message message, Class<T> type) {
        T event;
        try {
            event = objectMapper.readValue(message.getBody(), type);
        } catch (IOException e) {
            throw new NonRecoverableMessageException("JSON inválido para " + type.getSimpleName(), e);
        }

        if (event == null) {
            throw new NonRecoverableMessageException("Mensaje vacío para " + type.getSimpleName());
        }

        Set<ConstraintViolation<T>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new NonRecoverableMessageException("Evento " + type.getSimpleName() + " inválido: " + detail);
        }
        return event;
    }
}
