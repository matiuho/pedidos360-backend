package com.pedidos360.orders.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nombres de exchanges, routing keys y colas leídos desde application.yml (prefijo pedidos360.rabbitmq).
 * Es el único lugar del código que conoce esos nombres; si falta alguno, la aplicación no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "pedidos360.rabbitmq")
public record RabbitProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull RoutingKeys routingKeys,
        @Valid @NotNull Queues queues,
        @Valid @NotNull Retry retry) {

    public record Exchanges(@NotBlank String pedidos, @NotBlank String tickets, @NotBlank String deadLetter) {
    }

    public record RoutingKeys(@NotBlank String pedidoCreado,
                              @NotBlank String pedidoEstadoPrefijo,
                              @NotBlank String ticketGenerado) {
    }

    public record Queues(@Valid @NotNull QueueNames ticketsGenerados) {
    }

    /** Una cola de trabajo con su DLQ y su cola de reintento. */
    public record QueueNames(@NotBlank String name, @NotBlank String dlq, @NotBlank String retry) {
    }

    public record Retry(@Min(1) int maxAttempts, @Min(0) long delayMs) {
    }
}
