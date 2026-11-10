package com.pedidos360.notifications.controller;

import com.pedidos360.notifications.model.Notification;
import com.pedidos360.notifications.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Consulta de notificaciones (solo la consume el BFF por la red interna)
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<Notification> findAll() {
        return notificationService.findAll();
    }

    @GetMapping("/customer/{customerId}")
    public List<Notification> findByCustomer(@PathVariable String customerId) {
        return notificationService.findByCustomer(customerId);
    }
}
