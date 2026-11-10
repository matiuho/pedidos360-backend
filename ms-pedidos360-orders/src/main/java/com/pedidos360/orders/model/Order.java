package com.pedidos360.orders.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del usuario en Microsoft Entra ID (claim "oid" del JWT), lo asigna el BFF
    @NotBlank(message = "El cliente es obligatorio")
    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @NotNull(message = "El producto es obligatorio")
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CREADO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Código del comprobante generado de forma asíncrona por ms-pedidos360-tickets (vía RabbitMQ)
    @Column(name = "ticket_code", length = 40)
    private String ticketCode;

    public Order(Long id, String customerId, Long productId, Integer quantity,
                 OrderStatus status, LocalDateTime createdAt) {
        this(id, customerId, productId, quantity, status, createdAt, null);
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = OrderStatus.CREADO;
        }
    }

    public enum OrderStatus {
        CREADO,
        ACEPTADO,
        EN_PREPARACION,
        DESPACHADO,
        ENTREGADO,
        CANCELADO
    }
}