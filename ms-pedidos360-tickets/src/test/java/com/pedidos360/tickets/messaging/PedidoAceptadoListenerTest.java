package com.pedidos360.tickets.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.tickets.config.RabbitProperties;
import com.pedidos360.tickets.messaging.event.TicketGeneradoEvent;
import com.pedidos360.tickets.messaging.pedidos.PedidoAceptadoListener;
import com.pedidos360.tickets.messaging.support.EventReader;
import com.pedidos360.tickets.messaging.support.MessageFailureHandler;
import com.pedidos360.tickets.messaging.tickets.TicketEventPublisher;
import com.pedidos360.tickets.model.Ticket;
import com.pedidos360.tickets.service.TicketService;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// ACK manual del consumidor de pedidos aceptados
class PedidoAceptadoListenerTest {

    private static final long TAG = 3L;

    private final RabbitProperties props = new RabbitProperties(
            new RabbitProperties.Exchanges("ex.pedidos", "ex.tickets", "ex.dlx"),
            new RabbitProperties.RoutingKeys("pedido.estado.aceptado", "ticket.generado"),
            new RabbitProperties.Queues(new RabbitProperties.QueueNames("q.aceptados", "q.aceptados.dlq", "q.aceptados.retry")),
            new RabbitProperties.Retry(3, 1000));

    private TicketService ticketService;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private PedidoAceptadoListener listener;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        EventReader reader = new EventReader(new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator());
        listener = new PedidoAceptadoListener(reader, ticketService, new TicketEventPublisher(rabbitTemplate, props),
                new MessageFailureHandler(rabbitTemplate, props), props);
    }

    private static Message message(String estado) {
        String body = "{\"eventId\":\"e-1\",\"tipo\":\"CAMBIO_ESTADO\",\"pedidoId\":15,\"customerId\":\"oid-1\","
                + "\"productId\":3,\"quantity\":2,\"estado\":\"" + estado + "\",\"estadoAnterior\":\"CREADO\"}";
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static Ticket ticket() {
        return new Ticket("TCK-20261105-000015", 15L, "oid-1", 3L, 2, "...", LocalDateTime.now());
    }

    @Test
    void generaTicketPublicaEventoYHaceAck() throws Exception {
        when(ticketService.generate(15L, "oid-1", 3L, 2, "ACEPTADO")).thenReturn(ticket());

        listener.onPedidoAceptado(message("ACEPTADO"), channel);

        verify(rabbitTemplate).convertAndSend(eq("ex.tickets"), eq("ticket.generado"),
                any(TicketGeneradoEvent.class), any(CorrelationData.class));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void estadoDistintoDeAceptadoVaADlq() throws Exception {
        when(ticketService.generate(anyLong(), any(), anyLong(), any(), eq("CREADO")))
                .thenThrow(new IllegalArgumentException("estado inválido"));

        listener.onPedidoAceptado(message("CREADO"), channel);

        verify(channel).basicReject(TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void brokerCaidoAlPublicarSeReintenta() throws Exception {
        when(ticketService.generate(15L, "oid-1", 3L, 2, "ACEPTADO")).thenReturn(ticket());
        doThrow(new AmqpConnectException(new ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(eq("ex.tickets"), eq("ticket.generado"),
                        any(Object.class), any(CorrelationData.class));

        listener.onPedidoAceptado(message("ACEPTADO"), channel);

        verify(rabbitTemplate).send(eq(""), eq("q.aceptados.retry"), any(Message.class));
        verify(channel).basicAck(TAG, false);
    }
}
