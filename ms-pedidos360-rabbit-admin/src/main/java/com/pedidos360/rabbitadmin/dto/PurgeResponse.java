package com.pedidos360.rabbitadmin.dto;

public record PurgeResponse(String queue, int purgedMessages) {
}
