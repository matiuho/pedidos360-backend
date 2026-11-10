package com.pedidos360.notifications.messaging.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Evento publicado por ms-pedidos360-tickets al generar un comprobante. */
public record TicketGeneradoEvent(
        @NotBlank String eventId,
        @NotNull Long pedidoId,
        @NotBlank String customerId,
        @NotBlank String codigoTicket,
        Instant generadoEn) {
}
