package com.pedidos360.rabbitadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import static com.pedidos360.rabbitadmin.dto.RabbitNameRules.*;

/**
 * Datos para crear una cola. Los campos opcionales con valor null usan el valor por defecto:
 * durable=true, exclusive=false, autoDelete=false.
 */
public record QueueRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = NEW_NAME, message = NEW_NAME_MESSAGE)
        String name,

        Boolean durable,
        Boolean exclusive,
        Boolean autoDelete,

        // x-message-ttl: tiempo máximo (ms) que un mensaje puede esperar en la cola (máx. 7 días)
        @Positive(message = "debe ser mayor a 0")
        @Max(value = 604_800_000, message = "no puede superar 7 días (604800000 ms)")
        Integer messageTtlMs,

        // x-max-length: cantidad máxima de mensajes en la cola
        @Positive(message = "debe ser mayor a 0")
        @Max(value = 1_000_000, message = "no puede superar 1.000.000 mensajes")
        Integer maxLength,

        // x-dead-letter-exchange / x-dead-letter-routing-key: a dónde van los mensajes rechazados
        @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE)
        String deadLetterExchange,

        @Pattern(regexp = ROUTING_KEY, message = ROUTING_KEY_MESSAGE)
        String deadLetterRoutingKey) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public boolean exclusiveOrDefault() {
        return exclusive != null && exclusive;
    }

    public boolean autoDeleteOrDefault() {
        return autoDelete != null && autoDelete;
    }

    @JsonIgnore
    @AssertTrue(message = "deadLetterRoutingKey requiere indicar deadLetterExchange")
    public boolean isDeadLetterConsistent() {
        return deadLetterRoutingKey == null || deadLetterExchange != null;
    }

    @JsonIgnore
    @AssertTrue(message = "una cola exclusive no puede ser durable: use durable=false")
    public boolean isExclusiveConsistent() {
        return !exclusiveOrDefault() || !durableOrDefault();
    }
}
