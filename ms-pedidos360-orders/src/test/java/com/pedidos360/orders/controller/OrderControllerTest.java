package com.pedidos360.orders.controller;

import com.pedidos360.orders.client.CatalogClient;
import com.pedidos360.orders.messaging.event.PedidoEvent;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.Order.OrderStatus;
import com.pedidos360.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final String BASE = "/api/orders";
    private static final String CLIENTE_A = "11111111-aaaa-aaaa-aaaa-111111111111";
    private static final String CLIENTE_B = "22222222-bbbb-bbbb-bbbb-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private CatalogClient catalogClient;

    // Reemplaza el RabbitTemplate real: permite verificar qué se publica sin un broker
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    private Order saveOrder(String customerId, OrderStatus status) {
        return orderRepository.save(new Order(null, customerId, 1L, 2, status, LocalDateTime.now()));
    }

    private void changeStatus(Long id, String status, int expectedHttp) throws Exception {
        mockMvc.perform(put(BASE + "/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().is(expectedHttp));
    }

    @Test
    void creaPedidoEnEstadoCreado() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CLIENTE_A + "\",\"productId\":1,\"quantity\":2,\"status\":\"ENTREGADO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREADO"))
                .andExpect(jsonPath("$.data.customerId").value(CLIENTE_A));

        verify(catalogClient).assertProductExists(1L);
    }

    @Test
    void rechazaPedidoConProductoInexistente() throws Exception {
        doThrow(new IllegalArgumentException("El producto 99 no existe en el catálogo"))
                .when(catalogClient).assertProductExists(99L);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CLIENTE_A + "\",\"productId\":99,\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void rechazaPedidoSinCliente() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listaSoloLosPedidosDelCliente() throws Exception {
        saveOrder(CLIENTE_A, OrderStatus.CREADO);
        saveOrder(CLIENTE_A, OrderStatus.CREADO);
        saveOrder(CLIENTE_B, OrderStatus.CREADO);

        mockMvc.perform(get(BASE + "/customer/" + CLIENTE_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void aceptarDescuentaStock() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);

        changeStatus(order.getId(), "ACEPTADO", 200);

        verify(catalogClient).decreaseStock(1L, 2);
    }

    @Test
    void noSePuedeDespacharSinAceptar() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);

        changeStatus(order.getId(), "DESPACHADO", 400);
    }

    @Test
    void stockInsuficienteImpideAceptar() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);
        doThrow(new IllegalStateException("Stock insuficiente para aceptar el pedido"))
                .when(catalogClient).decreaseStock(anyLong(), anyInt());

        changeStatus(order.getId(), "ACEPTADO", 400);

        mockMvc.perform(get(BASE + "/" + order.getId()))
                .andExpect(jsonPath("$.data.status").value("CREADO"));
    }

    @Test
    void catalogoCaidoDevuelve503() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);
        doThrow(new CatalogClient.CatalogUnavailableException(new RuntimeException()))
                .when(catalogClient).decreaseStock(anyLong(), anyInt());

        changeStatus(order.getId(), "ACEPTADO", 503);
    }

    @Test
    void flujoCompletoHastaEntregado() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);

        changeStatus(order.getId(), "ACEPTADO", 200);
        changeStatus(order.getId(), "EN_PREPARACION", 200);
        changeStatus(order.getId(), "DESPACHADO", 200);
        changeStatus(order.getId(), "ENTREGADO", 200);
        changeStatus(order.getId(), "CANCELADO", 400);

        verify(catalogClient, times(1)).decreaseStock(1L, 2);
    }

    @Test
    void noSePuedeEditarPedidoAceptado() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.ACEPTADO);

        mockMvc.perform(put(BASE + "/" + order.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CLIENTE_A + "\",\"productId\":1,\"quantity\":5}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------- Mensajería (RabbitMQ) ----------------

    @Test
    void crearPedidoPublicaEventoPedidoCreado() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CLIENTE_A + "\",\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        verify(rabbitTemplate).convertAndSend(eq("pedidos360.pedidos.exchange"), eq("pedido.creado"),
                any(PedidoEvent.class), any(CorrelationData.class));
    }

    @Test
    void aceptarPedidoPublicaEventoDeEstado() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);

        changeStatus(order.getId(), "ACEPTADO", 200);

        verify(rabbitTemplate).convertAndSend(eq("pedidos360.pedidos.exchange"), eq("pedido.estado.aceptado"),
                any(PedidoEvent.class), any(CorrelationData.class));
    }

    @Test
    void cambioDeEstadoRechazadoNoPublicaEvento() throws Exception {
        Order order = saveOrder(CLIENTE_A, OrderStatus.CREADO);

        changeStatus(order.getId(), "DESPACHADO", 400);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(CorrelationData.class));
    }

    @Test
    void brokerCaidoNoImpideCrearPedido() throws Exception {
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                        any(CorrelationData.class));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + CLIENTE_A + "\",\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());
    }

    @Test
    void pedidoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound());
    }
}
