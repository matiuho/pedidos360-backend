package com.pedidos360.notifications.service;

import com.pedidos360.notifications.model.Notification;
import com.pedidos360.notifications.model.Notification.NotificationType;
import com.pedidos360.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Lógica de negocio: arma el texto de la notificación, la "envía" (log) y la registra. No conoce RabbitMQ.
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Map<String, String> TEXTO_ESTADO = Map.of(
            "ACEPTADO", "fue aceptado y estamos preparando tu comprobante",
            "EN_PREPARACION", "está en preparación",
            "DESPACHADO", "fue despachado",
            "ENTREGADO", "fue entregado. ¡Gracias por tu compra!",
            "CANCELADO", "fue cancelado");

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void notifyOrderCreated(String eventId, String customerId, Long pedidoId) {
        register(eventId, customerId, pedidoId, NotificationType.PEDIDO_CREADO,
                "Recibimos tu pedido #" + pedidoId + ".");
    }

    public void notifyOrderStatus(String eventId, String customerId, Long pedidoId, String estado) {
        String texto = TEXTO_ESTADO.get(estado);
        if (texto == null) {
            throw new IllegalArgumentException("Estado de pedido desconocido: " + estado);
        }
        register(eventId, customerId, pedidoId, NotificationType.PEDIDO_ESTADO,
                "Tu pedido #" + pedidoId + " " + texto + ".");
    }

    public void notifyTicketGenerated(String eventId, String customerId, Long pedidoId, String codigoTicket) {
        register(eventId, customerId, pedidoId, NotificationType.TICKET_GENERADO,
                "Tu comprobante " + codigoTicket + " del pedido #" + pedidoId + " está disponible.");
    }

    @Transactional(readOnly = true)
    public List<Notification> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Notification> findByCustomer(String customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // Idempotente: si el evento ya se notificó (mensaje duplicado) no se repite
    private void register(String eventId, String customerId, Long pedidoId, NotificationType tipo, String mensaje) {
        if (repository.existsByEventId(eventId)) {
            log.info("Evento {} ya notificado; se ignora el duplicado", eventId);
            return;
        }
        repository.save(new Notification(eventId, customerId, pedidoId, tipo, mensaje, LocalDateTime.now()));
        log.info("[CORREO SIMULADO] Para cliente {}: {}", customerId, mensaje);
    }
}
