package com.pedidos360.bff.security;

import com.pedidos360.bff.client.CatalogClient;
import com.pedidos360.bff.client.OrderClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static com.pedidos360.bff.security.Roles.CLIENTE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pruebas obligatorias: sin token, token inválido o expirado, issuer/audience/firma incorrectos
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestTokens.Config.class)
class JwtValidationTest {

    private static final String URL = "/api/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogClient catalogClient;

    @MockitoBean
    private OrderClient orderClient;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokenMalFormadoDevuelve401() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDevuelve401() throws Exception {
        String token = TestTokens.builder("oid-1", CLIENTE)
                .expiresAt(Instant.now().minusSeconds(600))
                .sign();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuerIncorrectoDevuelve401() throws Exception {
        String token = TestTokens.builder("oid-1", CLIENTE)
                .issuer("https://login.microsoftonline.com/otro-tenant/v2.0")
                .sign();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void audienceIncorrectaDevuelve401() throws Exception {
        String token = TestTokens.builder("oid-1", CLIENTE)
                .audience("otra-api")
                .sign();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firmaDeOtraLlaveDevuelve401() throws Exception {
        String token = TestTokens.builder("oid-1", CLIENTE)
                .signedWith(TestTokens.OTHER_KEYS)
                .sign();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoEsAceptado() throws Exception {
        when(catalogClient.getProducts()).thenReturn(ResponseEntity.ok(null));

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + TestTokens.token("oid-1", CLIENTE)))
                .andExpect(status().isOk());
    }

    @Test
    void audienceConApplicationIdUriEsAceptada() throws Exception {
        when(catalogClient.getProducts()).thenReturn(ResponseEntity.ok(null));

        String token = TestTokens.builder("oid-1", CLIENTE)
                .audience("api://pedidos360-api")
                .sign();

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
