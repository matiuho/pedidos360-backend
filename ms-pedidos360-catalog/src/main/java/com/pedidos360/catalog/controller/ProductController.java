package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.StockRequest;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Solo lo consume el BFF (red interna); CORS se configura en API Gateway
@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Obtener todos los productos
    @GetMapping
    public ResponseEntity<List<Product>> findAll() {
        return ResponseEntity.ok(productService.findAll());
    }

    // Obtener producto por ID
    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    // Crear producto
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
    }

    // Actualizar producto
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @PathVariable Long id,
            @Valid @RequestBody Product product) {

        return ResponseEntity.ok(productService.update(id, product));
    }

    // Descontar stock (lo invoca ms-orders al aceptar un pedido)
    @PutMapping("/{id}/stock/decrease")
    public ResponseEntity<Product> decreaseStock(
            @PathVariable Long id,
            @Valid @RequestBody StockRequest request) {

        return ResponseEntity.ok(productService.decreaseStock(id, request.quantity()));
    }

    // Eliminar producto
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
