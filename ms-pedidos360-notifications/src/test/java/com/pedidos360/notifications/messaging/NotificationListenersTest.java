package com.pedidos360.notifications.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.notifications.config.RabbitProperties;
import com.pedidos360.notifications.messaging.pedidos.PedidoNotificationListener;
import com.pedidos360.notifications.messaging.support.EventReader;
import com.pedidos360.notifications.messaging.support.MessageFailureHandler;
import com.pedidos360.notifications.messaging.tickets.TicketNotificationListener;
import com.pedidos360.notifications.service.NotificationService;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// ACK manual y política de errores de los consumidores de notificaciones
class NotificationListenersTest {

    private static final long TAG = 11L;

    private final RabbitProperties props = new RabbitProperties(
            new RabbitProperties.Exchanges("ex.pedidos", "ex.tickets", "ex.dlx"),
            new RabbitProperties.RoutingKeys("pedido.#", "ticket.generado"),
            new RabbitProperties.Queues(
                    new RabbitProperties.QueueNames("q.pedidos", "q.pedidos.dlq", "q.pedidos.retry"),
                    new RabbitProperties.QueueNames("q.tickets", "q.tickets.dlq", "q.tickets.retry")),
            new RabbitProperties.Retry(3, 1000));

    private NotificationService service;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private PedidoNotificationListener pedidos;
    private TicketNotificationListener tickets;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        EventReader reader = new EventReader(new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator());
        MessageFailureHandler failures = new MessageFailureHandler(rabbitTemplate, props);
        pedidos = new PedidoNotificationListener(reader, service, failures, props);
        tickets = new TicketNotificationListener(reader, service, failures, props);
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static String pedido(String tipo, String estado) {
        return "{\"eventId\":\"e-1\",\"tipo\":\"" + tipo + "\",\"pedidoId\":5,\"customerId\":\"oid-a\","
                + "\"productId\":1,\"quantity\":1,\"estado\":\"" + estado + "\"}";
    }

    @Test
    void pedidoCreadoSeNotificaYHaceAck() throws Exception {
        pedidos.onPedidoEvent(message(pedido("CREADO", "CREADO")), channel);

        verify(service).notifyOrderCreated("e-1", "oid-a", 5L);
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void cambioDeEstadoSeNotificaYHaceAck() throws Exception {
        pedidos.onPedidoEvent(message(pedido("CAMBIO_ESTADO", "DESPACHADO")), channel);

        verify(service).notifyOrderStatus("e-1", "oid-a", 5L, "DESPACHADO");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void tipoDesconocidoVaADlq() throws Exception {
        pedidos.onPedidoEvent(message(pedido("OTRO", "CREADO")), channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(service);
    }

    @Test
    void estadoDesconocidoVaADlq() throws Exception {
        doThrow(new IllegalArgumentException("Estado desconocido"))
                .when(service).notifyOrderStatus(any(), any(), anyLong(), eq("INVENTADO"));

        pedidos.onPedidoEvent(message(pedido("CAMBIO_ESTADO", "INVENTADO")), channel);

        verify(channel).basicReject(TAG, false);
    }

    @Test
    void ticketGeneradoSeNotificaYHaceAck() throws Exception {
        tickets.onTicketGenerado(message("{\"eventId\":\"t-1\",\"pedidoId\":5,\"customerId\":\"oid-a\","
                + "\"codigoTicket\":\"TCK-1\"}"), channel);

        verify(service).notifyTicketGenerated("t-1", "oid-a", 5L, "TCK-1");
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void baseDeDatosCaidaSeReintenta() throws Exception {
        doThrow(new DataAccessResourceFailureException("MySQL caído"))
                .when(service).notifyTicketGenerated(any(), any(), anyLong(), any());

        tickets.onTicketGenerado(message("{\"eventId\":\"t-1\",\"pedidoId\":5,\"customerId\":\"oid-a\","
                + "\"codigoTicket\":\"TCK-1\"}"), channel);

        verify(rabbitTemplate).send(eq(""), eq("q.tickets.retry"), any(Message.class));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }
}
