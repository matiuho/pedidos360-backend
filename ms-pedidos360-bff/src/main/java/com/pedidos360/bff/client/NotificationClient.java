package com.pedidos360.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

// Cliente hacia ms-pedidos360-notifications (notificaciones generadas de forma asíncrona vía RabbitMQ)
@Component
public class NotificationClient extends ServiceClient {

    private static final String NOTIFICATIONS = "/api/notifications";

    public NotificationClient(@Value("${notifications.service.url}") String notificationsServiceUrl,
                              ObjectMapper objectMapper) {
        super(notificationsServiceUrl, objectMapper);
    }

    public ResponseEntity<JsonNode> getAll() {
        return call(HttpMethod.GET, NOTIFICATIONS, null);
    }

    public ResponseEntity<JsonNode> getByCustomer(String customerId) {
        return call(HttpMethod.GET, NOTIFICATIONS + "/customer/{customerId}", null, customerId);
    }
}
