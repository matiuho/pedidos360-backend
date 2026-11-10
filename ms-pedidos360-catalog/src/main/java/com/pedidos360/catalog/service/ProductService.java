package com.pedidos360.catalog.service;

import com.pedidos360.catalog.exception.InsufficientStockException;
import com.pedidos360.catalog.exception.ProductNotFoundException;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Product create(Product product) {
        product.setId(null);
        return productRepository.save(product);
    }

    public Product update(Long id, Product product) {
        findById(id);
        product.setId(id);
        return productRepository.save(product);
    }

    public void deleteById(Long id) {
        findById(id);
        productRepository.deleteById(id);
    }

    // Regla de negocio: el stock disminuye al aceptar un pedido
    @Transactional
    public Product decreaseStock(Long id, int quantity) {
        Product product = findById(id);

        if (product.getStock() < quantity) {
            throw new InsufficientStockException(id, product.getStock(), quantity);
        }

        product.setStock(product.getStock() - quantity);
        return productRepository.save(product);
    }
}
