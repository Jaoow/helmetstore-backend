package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.product.ProductCreateDTO;
import com.jaoow.helmetstore.dto.product.ProductDto;
import com.jaoow.helmetstore.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @GetMapping
    public List<ProductDto> getAll(Principal principal) {
        return productService.findAll(principal);
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable Long id, Principal principal) {
        return productService.findById(id, principal);
    }

    @PostMapping
    public ProductDto create(@RequestBody @Valid ProductCreateDTO productCreateDTO, Principal principal) {
        return productService.save(productCreateDTO, principal);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable Long id, @RequestBody @Valid ProductDto productDTO, Principal principal) {
        return productService.update(id, productDTO, principal);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Principal principal) {
        productService.delete(id, principal);
    }
}
