package com.pedidos360.tickets.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Comprobante (ticket) de un pedido aceptado. Un pedido tiene como máximo un ticket.
@Entity
@Table(name = "tickets", uniqueConstraints = @UniqueConstraint(name = "uk_ticket_pedido", columnNames = "pedido_id"))
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    // Documento del comprobante en texto plano
    @Column(nullable = false, length = 2000)
    private String contenido;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Ticket() {
    }

    public Ticket(String codigo, Long pedidoId, String customerId, Long productId, Integer quantity,
                  String contenido, LocalDateTime createdAt) {
        this.codigo = codigo;
        this.pedidoId = pedidoId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.contenido = contenido;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getContenido() {
        return contenido;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
