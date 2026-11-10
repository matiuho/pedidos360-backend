package com.pedidos360.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.pedidos360.bff.client.NotificationClient;
import com.pedidos360.bff.security.CurrentUser;
import com.pedidos360.bff.security.Roles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationClient notificationClient;

    public NotificationController(NotificationClient notificationClient) {
        this.notificationClient = notificationClient;
    }

    // Admin/Operador ven todas las notificaciones; Cliente solo las suyas
    @GetMapping
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> getNotifications(Authentication authentication) {
        CurrentUser user = CurrentUser.from(authentication);

        return user.staff()
                ? notificationClient.getAll()
                : notificationClient.getByCustomer(user.id());
    }
}
