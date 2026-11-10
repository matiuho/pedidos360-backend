package com.pedidos360.notifications.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Notificación enviada (simulada) a un cliente. eventId evita registrar dos veces el mismo evento.
@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(name = "uk_notification_event", columnNames = "event_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType tipo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(String eventId, String customerId, Long pedidoId, NotificationType tipo,
                        String mensaje, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.customerId = customerId;
        this.pedidoId = pedidoId;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public NotificationType getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public enum NotificationType {
        PEDIDO_CREADO,
        PEDIDO_ESTADO,
        TICKET_GENERADO
    }
}
