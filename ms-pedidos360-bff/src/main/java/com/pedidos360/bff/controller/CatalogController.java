package com.pedidos360.bff.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pedidos360.bff.client.CatalogClient;
import com.pedidos360.bff.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog/products")
public class CatalogController {

    private final CatalogClient catalogClient;

    public CatalogController(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    // Ver catálogo: todos los roles
    @GetMapping
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> getProducts() {
        return catalogClient.getProducts();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.IS_ANY)
    public ResponseEntity<JsonNode> getProduct(@PathVariable Long id) {
        return catalogClient.getProduct(id);
    }

    // Crear/editar/eliminar producto: solo Admin
    @PostMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<JsonNode> createProduct(@RequestBody JsonNode product) {
        return catalogClient.createProduct(product);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<JsonNode> updateProduct(@PathVariable Long id, @RequestBody JsonNode product) {
        return catalogClient.updateProduct(id, product);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<JsonNode> deleteProduct(@PathVariable Long id) {
        return catalogClient.deleteProduct(id);
    }

    // Administrar stock: Admin y Operador
    @PutMapping("/{id}/stock")
    @PreAuthorize(Roles.IS_STAFF)
    public ResponseEntity<JsonNode> updateStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        ResponseEntity<JsonNode> current = catalogClient.getProduct(id);

        if (!current.getStatusCode().is2xxSuccessful() || current.getBody() == null) {
            return current;
        }

        ObjectNode product = (ObjectNode) current.getBody();
        product.put("stock", request.stock());
        return catalogClient.updateProduct(id, product);
    }

    public record StockRequest(
            @NotNull(message = "El stock es obligatorio")
            @PositiveOrZero(message = "El stock no puede ser negativo")
            Integer stock) {
    }
}
