package com.pedidos360.rabbitadmin.dto;

public record BindingResponse(String exchange, String queue, String routingKey) {
}
