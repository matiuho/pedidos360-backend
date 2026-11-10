package com.pedidos360.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

// Cliente hacia ms-pedidos360-orders
@Component
public class OrderClient extends ServiceClient {

    private static final String ORDERS = "/api/orders";

    public OrderClient(@Value("${orders.service.url}") String ordersServiceUrl, ObjectMapper objectMapper) {
        super(ordersServiceUrl, objectMapper);
    }

    public ResponseEntity<JsonNode> getOrders() {
        return call(HttpMethod.GET, ORDERS, null);
    }

    public ResponseEntity<JsonNode> getOrdersByCustomer(String customerId) {
        return call(HttpMethod.GET, ORDERS + "/customer/{customerId}", null, customerId);
    }

    public ResponseEntity<JsonNode> getOrder(Long id) {
        return call(HttpMethod.GET, ORDERS + "/{id}", null, id);
    }

    public ResponseEntity<JsonNode> createOrder(JsonNode order) {
        return call(HttpMethod.POST, ORDERS, order);
    }

    public ResponseEntity<JsonNode> updateOrder(Long id, JsonNode order) {
        return call(HttpMethod.PUT, ORDERS + "/{id}", order, id);
    }

    public ResponseEntity<JsonNode> changeStatus(Long id, String status) {
        return call(HttpMethod.PUT, ORDERS + "/{id}/status", Map.of("status", status), id);
    }

    public ResponseEntity<JsonNode> deleteOrder(Long id) {
        return call(HttpMethod.DELETE, ORDERS + "/{id}", null, id);
    }
}
