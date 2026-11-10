package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.pedidos360.rabbitadmin.dto.RabbitNameRules.*;

/**
 * Enlace exchange -> cola. routingKey vacío o null equivale a "" (válido para fanout).
 */
public record BindingRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE)
        String exchange,

        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = EXISTING_NAME, message = EXISTING_NAME_MESSAGE)
        String queue,

        @Pattern(regexp = ROUTING_KEY, message = ROUTING_KEY_MESSAGE)
        String routingKey) {

    public String routingKeyOrDefault() {
        return routingKey == null ? "" : routingKey;
    }
}
