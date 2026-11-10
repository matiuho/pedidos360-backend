package com.pedidos360.bff.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

// Cliente hacia ms-pedidos360-catalog
@Component
public class CatalogClient extends ServiceClient {

    private static final String PRODUCTS = "/api/catalog/products";

    public CatalogClient(@Value("${catalog.service.url}") String catalogServiceUrl, ObjectMapper objectMapper) {
        super(catalogServiceUrl, objectMapper);
    }

    public ResponseEntity<JsonNode> getProducts() {
        return call(HttpMethod.GET, PRODUCTS, null);
    }

    public ResponseEntity<JsonNode> getProduct(Long id) {
        return call(HttpMethod.GET, PRODUCTS + "/{id}", null, id);
    }

    public ResponseEntity<JsonNode> createProduct(JsonNode product) {
        return call(HttpMethod.POST, PRODUCTS, product);
    }

    public ResponseEntity<JsonNode> updateProduct(Long id, JsonNode product) {
        return call(HttpMethod.PUT, PRODUCTS + "/{id}", product, id);
    }

    public ResponseEntity<JsonNode> deleteProduct(Long id) {
        return call(HttpMethod.DELETE, PRODUCTS + "/{id}", null, id);
    }
}
