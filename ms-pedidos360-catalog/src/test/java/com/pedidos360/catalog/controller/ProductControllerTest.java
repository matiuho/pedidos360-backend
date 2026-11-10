package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    private static final String BASE = "/api/catalog/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private Product saved;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        saved = productRepository.save(new Product(null, "Teclado", 15000.0, 10));
    }

    @Test
    void listaProductos() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Teclado"));
    }

    @Test
    void productoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creaProductoValido() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mouse\",\"price\":8000,\"stock\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mouse"));
    }

    @Test
    void rechazaProductoConStockNegativo() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mouse\",\"price\":8000,\"stock\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descuentaStock() throws Exception {
        mockMvc.perform(put(BASE + "/" + saved.getId() + "/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void rechazaDescuentoConStockInsuficiente() throws Exception {
        mockMvc.perform(put(BASE + "/" + saved.getId() + "/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":11}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get(BASE + "/" + saved.getId()))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    void rechazaCantidadCero() throws Exception {
        mockMvc.perform(put(BASE + "/" + saved.getId() + "/stock/decrease")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminaProducto() throws Exception {
        mockMvc.perform(delete(BASE + "/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + saved.getId()))
                .andExpect(status().isNotFound());
    }
}
