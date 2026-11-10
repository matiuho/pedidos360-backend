package com.pedidos360.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.bff.client.CatalogClient;
import com.pedidos360.bff.client.NotificationClient;
import com.pedidos360.bff.client.OrderClient;
import com.pedidos360.bff.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import static com.pedidos360.bff.security.Roles.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pruebas de autorización por rol (token válido con y sin permiso suficiente)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTokens.Config.class)
class AuthorizationTest {

    private static final String CLIENTE_A = "oid-cliente-a";
    private static final String CLIENTE_B = "oid-cliente-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CatalogClient catalogClient;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private NotificationClient notificationClient;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String oid, String... roles) {
        return request.header("Authorization", "Bearer " + TestTokens.token(oid, roles))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<JsonNode> json(HttpStatus status, String body) throws Exception {
        return ResponseEntity.status(status).body(objectMapper.readTree(body));
    }

    private ResponseEntity<JsonNode> order(String ownerId, String status) throws Exception {
        return json(HttpStatus.OK, "{\"ok\":true,\"data\":{\"id\":1,\"customerId\":\"" + ownerId
                + "\",\"productId\":1,\"quantity\":2,\"status\":\"" + status + "\"}}");
    }

    // ---------------- Catálogo ----------------

    @Test
    void clientePuedeVerCatalogo() throws Exception {
        when(catalogClient.getProducts()).thenReturn(json(HttpStatus.OK, "[]"));

        mockMvc.perform(as(get("/api/catalog/products"), CLIENTE_A, CLIENTE))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeCrearProducto() throws Exception {
        mockMvc.perform(as(post("/api/catalog/products"), CLIENTE_A, CLIENTE).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(catalogClient);
    }

    @Test
    void operadorNoPuedeCrearProducto() throws Exception {
        mockMvc.perform(as(post("/api/catalog/products"), "oid-op", OPERADOR).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeCrearProducto() throws Exception {
        when(catalogClient.createProduct(any())).thenReturn(json(HttpStatus.CREATED, "{\"id\":1}"));

        mockMvc.perform(as(post("/api/catalog/products"), "oid-admin", ADMIN)
                        .content("{\"name\":\"X\",\"price\":1,\"stock\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void operadorPuedeAdministrarStock() throws Exception {
        when(catalogClient.getProduct(1L)).thenReturn(json(HttpStatus.OK, "{\"id\":1,\"name\":\"X\",\"price\":1,\"stock\":3}"));
        when(catalogClient.updateProduct(eq(1L), any())).thenReturn(json(HttpStatus.OK, "{\"id\":1,\"stock\":20}"));

        mockMvc.perform(as(put("/api/catalog/products/1/stock"), "oid-op", OPERADOR).content("{\"stock\":20}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(catalogClient).updateProduct(eq(1L), captor.capture());
        assertThat(captor.getValue().path("stock").asInt()).isEqualTo(20);
    }

    @Test
    void clienteNoPuedeAdministrarStock() throws Exception {
        mockMvc.perform(as(put("/api/catalog/products/1/stock"), CLIENTE_A, CLIENTE).content("{\"stock\":20}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stockNegativoDevuelve400() throws Exception {
        mockMvc.perform(as(put("/api/catalog/products/1/stock"), "oid-op", OPERADOR).content("{\"stock\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenSinRolesDevuelve403() throws Exception {
        mockMvc.perform(as(get("/api/catalog/products"), CLIENTE_A))
                .andExpect(status().isForbidden());
    }

    // ---------------- Pedidos ----------------

    @Test
    void clienteSoloVeSusPedidos() throws Exception {
        when(orderClient.getOrdersByCustomer(CLIENTE_A)).thenReturn(json(HttpStatus.OK, "{\"data\":[]}"));

        mockMvc.perform(as(get("/api/orders"), CLIENTE_A, CLIENTE))
                .andExpect(status().isOk());

        verify(orderClient).getOrdersByCustomer(CLIENTE_A);
        verify(orderClient, never()).getOrders();
    }

    @Test
    void operadorVeTodosLosPedidos() throws Exception {
        when(orderClient.getOrders()).thenReturn(json(HttpStatus.OK, "{\"data\":[]}"));

        mockMvc.perform(as(get("/api/orders"), "oid-op", OPERADOR))
                .andExpect(status().isOk());

        verify(orderClient).getOrders();
    }

    @Test
    void crearPedidoUsaElOidDelToken() throws Exception {
        when(orderClient.createOrder(any())).thenReturn(json(HttpStatus.CREATED, "{\"ok\":true}"));

        mockMvc.perform(as(post("/api/orders"), CLIENTE_A, CLIENTE)
                        .content("{\"customerId\":\"" + CLIENTE_B + "\",\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(orderClient).createOrder(captor.capture());
        assertThat(captor.getValue().path("customerId").asText()).isEqualTo(CLIENTE_A);
    }

    @Test
    void clienteNoPuedeVerPedidoAjeno() throws Exception {
        when(orderClient.getOrder(1L)).thenReturn(order(CLIENTE_B, "CREADO"));

        mockMvc.perform(as(get("/api/orders/1"), CLIENTE_A, CLIENTE))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientePuedeVerSuPedido() throws Exception {
        when(orderClient.getOrder(1L)).thenReturn(order(CLIENTE_A, "CREADO"));

        mockMvc.perform(as(get("/api/orders/1"), CLIENTE_A, CLIENTE))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeAceptarPedido() throws Exception {
        when(orderClient.getOrder(1L)).thenReturn(order(CLIENTE_A, "CREADO"));

        mockMvc.perform(as(put("/api/orders/1/status"), CLIENTE_A, CLIENTE).content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isForbidden());

        verify(orderClient, never()).changeStatus(any(), anyString());
    }

    @Test
    void clientePuedeCancelarSuPedidoCreado() throws Exception {
        when(orderClient.getOrder(1L)).thenReturn(order(CLIENTE_A, "CREADO"));
        when(orderClient.changeStatus(1L, "CANCELADO")).thenReturn(order(CLIENTE_A, "CANCELADO"));

        mockMvc.perform(as(put("/api/orders/1/status"), CLIENTE_A, CLIENTE).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeCancelarPedidoAceptado() throws Exception {
        when(orderClient.getOrder(1L)).thenReturn(order(CLIENTE_A, "ACEPTADO"));

        mockMvc.perform(as(put("/api/orders/1/status"), CLIENTE_A, CLIENTE).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorPuedeCambiarEstado() throws Exception {
        when(orderClient.changeStatus(1L, "ACEPTADO")).thenReturn(order(CLIENTE_A, "ACEPTADO"));

        mockMvc.perform(as(put("/api/orders/1/status"), "oid-op", OPERADOR).content("{\"status\":\"ACEPTADO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reglaDeNegocioInvalidaSeReenviaComo400() throws Exception {
        when(orderClient.changeStatus(1L, "DESPACHADO"))
                .thenReturn(json(HttpStatus.BAD_REQUEST, "{\"ok\":false,\"message\":\"Cambio de estado no permitido\"}"));

        mockMvc.perform(as(put("/api/orders/1/status"), "oid-op", OPERADOR).content("{\"status\":\"DESPACHADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloAdminPuedeEliminarPedido() throws Exception {
        when(orderClient.deleteOrder(1L)).thenReturn(json(HttpStatus.OK, "{\"ok\":true}"));

        mockMvc.perform(as(delete("/api/orders/1"), "oid-op", OPERADOR))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(delete("/api/orders/1"), "oid-admin", ADMIN))
                .andExpect(status().isOk());
    }

    // ---------------- Notificaciones ----------------

    @Test
    void clienteSoloVeSusNotificaciones() throws Exception {
        when(notificationClient.getByCustomer(CLIENTE_A)).thenReturn(json(HttpStatus.OK, "[]"));

        mockMvc.perform(as(get("/api/notifications"), CLIENTE_A, CLIENTE))
                .andExpect(status().isOk());

        verify(notificationClient).getByCustomer(CLIENTE_A);
        verify(notificationClient, never()).getAll();
    }

    @Test
    void operadorVeTodasLasNotificaciones() throws Exception {
        when(notificationClient.getAll()).thenReturn(json(HttpStatus.OK, "[]"));

        mockMvc.perform(as(get("/api/notifications"), "oid-op", OPERADOR))
                .andExpect(status().isOk());

        verify(notificationClient).getAll();
    }

    @Test
    void microservicioCaidoDevuelve503() throws Exception {
        when(orderClient.getOrders()).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(as(get("/api/orders"), "oid-admin", ADMIN))
                .andExpect(status().isServiceUnavailable());
    }
}
