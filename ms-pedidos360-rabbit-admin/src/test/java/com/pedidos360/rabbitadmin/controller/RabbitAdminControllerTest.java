package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.*;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import com.pedidos360.rabbitadmin.security.TestTokens;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.pedidos360.rabbitadmin.security.Roles.ADMIN;
import static com.pedidos360.rabbitadmin.security.Roles.OPERADOR;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Endpoints REST del administrador: seguridad, validación de entrada y códigos HTTP
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTokens.Config.class)
class RabbitAdminControllerTest {

    private static final String BASE = "/api/rabbit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitAdminService service;

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TestTokens.token("oid-admin", ADMIN))
                .contentType(MediaType.APPLICATION_JSON);
    }

    // ---------------- Seguridad ----------------

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post(BASE + "/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"q1\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void operadorNoPuedeAdministrarRabbit() throws Exception {
        mockMvc.perform(post(BASE + "/queues")
                        .header("Authorization", "Bearer " + TestTokens.token("oid-op", OPERADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"q1\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    // ---------------- Colas ----------------

    @Test
    void creaColaValidaDevuelve201ConLocation() throws Exception {
        when(service.createQueue(any())).thenReturn(new QueueInfoResponse("demo.cola", 0, 0));

        mockMvc.perform(admin(post(BASE + "/queues"))
                        .content("{\"name\":\"demo.cola\",\"messageTtlMs\":60000,\"deadLetterExchange\":\"pedidos360.dlx\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/rabbit/queues/demo.cola")))
                .andExpect(jsonPath("$.name").value("demo.cola"));
    }

    @Test
    void colaConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("name")));
        verifyNoInteractions(service);
    }

    @Test
    void colaSinNombreDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void colaConCaracteresInvalidosDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"mi cola/$\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void colaConPrefijoReservadoAmqDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"amq.mia\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void ttlNegativoDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"q1\",\"messageTtlMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("messageTtlMs")));
    }

    @Test
    void routingKeyDeDlqSinExchangeDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"q1\",\"deadLetterRoutingKey\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("deadLetterExchange")));
    }

    @Test
    void colaExclusiveDurableDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"q1\",\"exclusive\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void colaExistenteDevuelve409() throws Exception {
        when(service.createQueue(any())).thenThrow(new ResourceConflictException("La cola 'q1' ya existe"));

        mockMvc.perform(admin(post(BASE + "/queues")).content("{\"name\":\"q1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La cola 'q1' ya existe"));
    }

    @Test
    void eliminaColaDevuelve204() throws Exception {
        mockMvc.perform(admin(delete(BASE + "/queues/demo.cola")).param("ifEmpty", "true"))
                .andExpect(status().isNoContent());

        verify(service).deleteQueue("demo.cola", false, true);
    }

    @Test
    void eliminarColaInexistenteDevuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("La cola 'x' no existe")).when(service).deleteQueue("x", false, false);

        mockMvc.perform(admin(delete(BASE + "/queues/x")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarColaDelSistemaAmqDevuelve400() throws Exception {
        mockMvc.perform(admin(delete(BASE + "/queues/amq.gen-123")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void consultaColaDevuelveContadores() throws Exception {
        when(service.getQueue("q1")).thenReturn(new QueueInfoResponse("q1", 4, 1));

        mockMvc.perform(admin(get(BASE + "/queues/q1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCount").value(4))
                .andExpect(jsonPath("$.consumerCount").value(1));
    }

    // ---------------- Exchanges ----------------

    @Test
    void creaExchangeTopicDevuelve201() throws Exception {
        when(service.createExchange(any()))
                .thenReturn(new ExchangeResponse("demo.ex", ExchangeType.TOPIC, true, false));

        mockMvc.perform(admin(post(BASE + "/exchanges")).content("{\"name\":\"demo.ex\",\"type\":\"TOPIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TOPIC"));
    }

    @Test
    void exchangeSinTipoDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/exchanges")).content("{\"name\":\"demo.ex\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("type")));
    }

    @Test
    void exchangeConTipoInexistenteDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/exchanges")).content("{\"name\":\"demo.ex\",\"type\":\"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("DIRECT, TOPIC, FANOUT, HEADERS")));
    }

    @Test
    void eliminaExchangeDevuelve204() throws Exception {
        mockMvc.perform(admin(delete(BASE + "/exchanges/demo.ex")))
                .andExpect(status().isNoContent());
        verify(service).deleteExchange("demo.ex");
    }

    // ---------------- Bindings ----------------

    @Test
    void creaBindingDevuelve201() throws Exception {
        when(service.createBinding(any())).thenReturn(new BindingResponse("demo.ex", "demo.cola", "pedido.#"));

        mockMvc.perform(admin(post(BASE + "/bindings"))
                        .content("{\"exchange\":\"demo.ex\",\"queue\":\"demo.cola\",\"routingKey\":\"pedido.#\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("pedido.#"));
    }

    @Test
    void bindingSinColaDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/bindings")).content("{\"exchange\":\"demo.ex\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void bindingConRoutingKeyInvalidaDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/bindings"))
                        .content("{\"exchange\":\"demo.ex\",\"queue\":\"q1\",\"routingKey\":\"con espacios\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminaBindingDevuelve204() throws Exception {
        mockMvc.perform(admin(delete(BASE + "/bindings"))
                        .param("exchange", "demo.ex").param("queue", "demo.cola").param("routingKey", "pedido.#"))
                .andExpect(status().isNoContent());
        verify(service).deleteBinding("demo.ex", "demo.cola", "pedido.#");
    }

    @Test
    void eliminarBindingSinExchangeDevuelve400() throws Exception {
        mockMvc.perform(admin(delete(BASE + "/bindings")).param("queue", "demo.cola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("exchange")));
    }

    @Test
    void jsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(admin(post(BASE + "/queues")).content("{nombre"))
                .andExpect(status().isBadRequest());
    }
}
