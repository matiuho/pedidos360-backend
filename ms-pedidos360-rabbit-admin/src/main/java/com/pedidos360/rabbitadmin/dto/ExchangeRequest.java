package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static com.pedidos360.rabbitadmin.dto.RabbitNameRules.NEW_NAME;
import static com.pedidos360.rabbitadmin.dto.RabbitNameRules.NEW_NAME_MESSAGE;

/**
 * Datos para crear un exchange. durable=true y autoDelete=false si no se indican.
 */
public record ExchangeRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = NEW_NAME, message = NEW_NAME_MESSAGE)
        String name,

        @NotNull(message = "es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        ExchangeType type,

        Boolean durable,
        Boolean autoDelete) {

    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public boolean autoDeleteOrDefault() {
        return autoDelete != null && autoDelete;
    }
}
