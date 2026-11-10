package com.pedidos360.rabbitadmin.dto;

public record ExchangeResponse(String name, ExchangeType type, boolean durable, boolean autoDelete) {
}
