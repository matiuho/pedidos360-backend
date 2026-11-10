package com.pedidos360.orders.service;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.event.OrderCreatedEvent;
import com.pedidos360.orders.event.OrderStatusChangedEvent;
import com.pedidos360.orders.exception.OrderNotFoundException;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.Order.OrderStatus;
import com.pedidos360.orders.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Lógica de negocio de pedidos. Solo emite eventos de dominio: no conoce RabbitMQ.
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository, CatalogClient catalogClient,
                        ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.catalogClient = catalogClient;
        this.events = events;
    }

    // Obtener todos los pedidos
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    // Obtener pedido por ID
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Obtener los pedidos de un cliente
    public List<Order> findByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    // Crear pedido: siempre nace en estado CREADO y el producto debe existir
    public Order create(Order order) {
        catalogClient.assertProductExists(order.getProductId());

        order.setId(null);
        order.setStatus(OrderStatus.CREADO);
        order.setCreatedAt(null);
        order.setTicketCode(null);

        Order saved = orderRepository.save(order);
        events.publishEvent(new OrderCreatedEvent(saved));
        return saved;
    }

    // Actualizar producto/cantidad: solo mientras el pedido no ha sido aceptado
    public Order update(Long id, Long productId, Integer quantity) {
        Order order = findById(id);

        if (order.getStatus() != OrderStatus.CREADO) {
            throw new IllegalStateException(
                    "Solo se puede modificar un pedido en estado CREADO (estado actual: "
                            + order.getStatus() + ")"
            );
        }

        catalogClient.assertProductExists(productId);

        order.setProductId(productId);
        order.setQuantity(quantity);

        return orderRepository.save(order);
    }

    // Eliminar pedido
    public void deleteById(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }

        orderRepository.deleteById(id);
    }

    // Cambiar estado del pedido
    public Order changeStatus(Long id, OrderStatus newStatus) {

        Order order = findById(id);
        OrderStatus currentStatus = order.getStatus();

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                    "Cambio de estado no permitido: "
                            + currentStatus
                            + " -> "
                            + newStatus
            );
        }

        // Regla de negocio: el stock disminuye al aceptar el pedido
        if (newStatus == OrderStatus.ACEPTADO) {
            catalogClient.decreaseStock(order.getProductId(), order.getQuantity());
        }

        order.setStatus(newStatus);

        Order saved = orderRepository.save(order);
        events.publishEvent(new OrderStatusChangedEvent(saved, currentStatus));
        return saved;
    }

    // Guarda el código del comprobante generado de forma asíncrona por ms-tickets (idempotente)
    public Order assignTicket(Long id, String ticketCode) {
        Order order = findById(id);

        if (ticketCode.equals(order.getTicketCode())) {
            return order;
        }

        order.setTicketCode(ticketCode);
        return orderRepository.save(order);
    }

    // Validar secuencia de estados
    private boolean isValidTransition(
            OrderStatus currentStatus,
            OrderStatus newStatus
    ) {

        if (currentStatus == OrderStatus.CREADO) {
            return newStatus == OrderStatus.ACEPTADO
                    || newStatus == OrderStatus.CANCELADO;
        }

        if (currentStatus == OrderStatus.ACEPTADO) {
            return newStatus == OrderStatus.EN_PREPARACION
                    || newStatus == OrderStatus.CANCELADO;
        }

        if (currentStatus == OrderStatus.EN_PREPARACION) {
            return newStatus == OrderStatus.DESPACHADO
                    || newStatus == OrderStatus.CANCELADO;
        }

        if (currentStatus == OrderStatus.DESPACHADO) {
            return newStatus == OrderStatus.ENTREGADO
                    || newStatus == OrderStatus.CANCELADO;
        }

        return false;
    }
}
