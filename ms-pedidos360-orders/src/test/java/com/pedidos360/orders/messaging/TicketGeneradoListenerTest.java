package com.pedidos360.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.orders.config.RabbitProperties;
import com.pedidos360.orders.exception.OrderNotFoundException;
import com.pedidos360.orders.messaging.support.EventReader;
import com.pedidos360.orders.messaging.support.MessageFailureHandler;
import com.pedidos360.orders.messaging.tickets.TicketGeneradoListener;
import com.pedidos360.orders.service.OrderService;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Verifica la confirmación de mensajes: ACK, rechazo a DLQ y reintento diferido
class TicketGeneradoListenerTest {

    private static final long TAG = 7L;
    private static final String VALID = "{\"eventId\":\"e-1\",\"pedidoId\":10,\"customerId\":\"c-1\","
            + "\"codigoTicket\":\"TCK-0010\",\"generadoEn\":\"2026-11-05T10:00:00Z\"}";

    private final RabbitProperties props = new RabbitProperties(
            new RabbitProperties.Exchanges("ex.pedidos", "ex.tickets", "ex.dlx"),
            new RabbitProperties.RoutingKeys("pedido.creado", "pedido.estado.", "ticket.generado"),
            new RabbitProperties.Queues(new RabbitProperties.QueueNames("q.tickets", "q.tickets.dlq", "q.tickets.retry")),
            new RabbitProperties.Retry(3, 1000));

    private OrderService orderService;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private TicketGeneradoListener listener;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        EventReader reader = new EventReader(new ObjectMapper().findAndRegisterModules(),
                Validation.buildDefaultValidatorFactory().getValidator());
        listener = new TicketGeneradoListener(reader, orderService,
                new MessageFailureHandler(rabbitTemplate, props), props);
    }

    private static Message message(String body, int previousAttempts) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        if (previousAttempts > 0) {
            properties.setHeader(MessageFailureHandler.ATTEMPTS_HEADER, previousAttempts);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void mensajeValidoHaceAck() throws Exception {
        listener.onTicketGenerado(message(VALID, 0), channel);

        verify(orderService).assignTicket(10L, "TCK-0010");
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void jsonInvalidoVaDirectoADlq() throws Exception {
        listener.onTicketGenerado(message("{esto no es json", 0), channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(orderService, rabbitTemplate);
    }

    @Test
    void eventoSinCamposObligatoriosVaADlq() throws Exception {
        listener.onTicketGenerado(message("{\"eventId\":\"e-1\"}", 0), channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(orderService);
    }

    @Test
    void pedidoInexistenteVaADlqSinReintentar() throws Exception {
        when(orderService.assignTicket(anyLong(), anyString())).thenThrow(new OrderNotFoundException(10L));

        listener.onTicketGenerado(message(VALID, 0), channel);

        verify(channel).basicReject(TAG, false);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void errorRecuperableSeReintentaYHaceAckDelOriginal() throws Exception {
        when(orderService.assignTicket(anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("MySQL caído"));

        listener.onTicketGenerado(message(VALID, 0), channel);

        verify(rabbitTemplate).send(eq(""), eq("q.tickets.retry"),
                argThat(m -> MessageFailureHandler.attempts(m) == 1));
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void reintentosAgotadosVanADlq() throws Exception {
        when(orderService.assignTicket(anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("MySQL caído"));

        listener.onTicketGenerado(message(VALID, 2), channel);

        verify(channel).basicReject(TAG, false);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void contadorDeIntentosSeLeeDesdeElHeader() {
        assertThat(MessageFailureHandler.attempts(message(VALID, 0))).isZero();
        assertThat(MessageFailureHandler.attempts(message(VALID, 2))).isEqualTo(2);
    }
}
