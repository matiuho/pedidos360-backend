package com.pedidos360.rabbitadmin.dto;

// Estado de una cola: mensajes pendientes y consumidores conectados
public record QueueInfoResponse(String name, long messageCount, int consumerCount) {
}
