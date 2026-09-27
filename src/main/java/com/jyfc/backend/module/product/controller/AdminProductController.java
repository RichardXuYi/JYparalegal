package com.jyfc.backend.module.product.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.shared.dto.PageResponse;
import com.jyfc.backend.module.product.dto.ProductDTO;
import com.jyfc.backend.module.product.dto.SkuCreateDTO;
import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.entity.ProductAttribute;
import com.jyfc.backend.module.product.entity.Sku;
import com.jyfc.backend.module.product.repository.ProductAttributeRepository;
import com.jyfc.backend.module.product.repository.ProductRepository;
import com.jyfc.backend.module.product.repository.SkuRepository;
import com.jyfc.backend.module.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductAttributeRepository attributeRepository;
    private final SkuRepository skuRepository;

    public AdminProductController(ProductService productService, ProductRepository productRepository,
                                  ProductAttributeRepository attributeRepository, SkuRepository skuRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.attributeRepository = attributeRepository;
        this.skuRepository = skuRepository;
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<PageResponse<Product>> listProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "productType", required = false) String productType) {

        Page<Product> p = productService.listProducts(page, pageSize, status, categoryId, keyword, productType);

        PageResponse<Product> pr = new PageResponse<>(p.getContent(), p.getTotalElements(), page, pageSize);
        return ApiResponse.success(pr);
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Product> createProduct(@Valid @RequestBody ProductDTO dto) {
        Product p = productService.createProduct(dto);
        return ApiResponse.success(p);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Product> updateProduct(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        try {
            Product p = productService.updateProduct(id, payload);
            return ApiResponse.success("Product updated successfully", p);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> deleteProduct(@PathVariable long id) {
        return productRepository.findById(id).map(p -> {
            productRepository.delete(p);
            return ApiResponse.success("Product deleted successfully", (Void) null);
        }).orElse(ApiResponse.error(404, "Product not found"));
    }

    // ========= SKU CRUD =========

    @GetMapping("/products/{id}/skus")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<List<Sku>> getSkus(@PathVariable long id) {
        return ApiResponse.success(skuRepository.findByProductId(id));
    }

    @PostMapping("/products/{id}/skus")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Sku> createSku(@PathVariable long id, @Valid @RequestBody SkuCreateDTO dto) {
        // Verify product exists
        if (!productRepository.existsById(id)) {
            return ApiResponse.error(404, "Product not found");
        }

        // Check duplicate skuCode
        if (dto.getSkuCode() != null && !dto.getSkuCode().isBlank()
                && skuRepository.existsBySkuCode(dto.getSkuCode())) {
            return ApiResponse.error(409, "SKU code already exists: " + dto.getSkuCode());
        }

        Sku sku = new Sku();
        sku.setProductId(id);
        sku.setSkuCode(dto.getSkuCode());
        sku.setSpecs(dto.getTitle() != null ? "{\"title\":\"" + dto.getTitle() + "\"}" : "{}");
        sku.setPrice(dto.getCurrentPrice() != null
                ? BigDecimal.valueOf(dto.getCurrentPrice()) : BigDecimal.ZERO);
        sku.setStock(dto.getStock() != null ? dto.getStock() : 0);

        Sku saved = skuRepository.save(sku);
        return ApiResponse.success("SKU created successfully", saved);
    }

    @PutMapping("/products/{productId}/skus/{skuId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Sku> updateSku(@PathVariable long productId, @PathVariable long skuId,
                                      @RequestBody Map<String, Object> body) {
        return skuRepository.findById(skuId).map(sku -> {
            if (productId != sku.getProductId()) {
                return ApiResponse.<Sku>error(400, "SKU does not belong to this product");
            }

            if (body.containsKey("skuCode")) {
                String newCode = (String) body.get("skuCode");
                if (newCode != null && !newCode.isBlank()
                        && !newCode.equals(sku.getSkuCode())
                        && skuRepository.existsBySkuCode(newCode)) {
                    return ApiResponse.<Sku>error(409, "SKU code already exists: " + newCode);
                }
                sku.setSkuCode(newCode);
            }
            if (body.containsKey("specs")) {
                sku.setSpecs((String) body.get("specs"));
            }
            if (body.containsKey("price")) {
                Object price = body.get("price");
                sku.setPrice(price != null
                        ? BigDecimal.valueOf(Double.parseDouble(String.valueOf(price)))
                        : BigDecimal.ZERO);
            }
            if (body.containsKey("originalPrice")) {
                Object p = body.get("originalPrice");
                sku.setOriginalPrice(p != null
                        ? BigDecimal.valueOf(Double.parseDouble(String.valueOf(p)))
                        : null);
            }
            if (body.containsKey("stock") && body.get("stock") != null) {
                sku.setStock(Integer.valueOf(String.valueOf(body.get("stock"))));
            }
            if (body.containsKey("image")) {
                sku.setImage((String) body.get("image"));
            }

            Sku saved = skuRepository.save(sku);
            return ApiResponse.<Sku>success("SKU updated successfully", saved);
        }).orElse(ApiResponse.error(404, "SKU not found"));
    }

    @DeleteMapping("/products/{productId}/skus/{skuId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> deleteSku(@PathVariable long productId, @PathVariable long skuId) {
        return skuRepository.findById(skuId).map(sku -> {
            if (productId != sku.getProductId()) {
                return ApiResponse.<Void>error(400, "SKU does not belong to this product");
            }
            skuRepository.delete(sku);
            return ApiResponse.<Void>success("SKU deleted successfully", null);
        }).orElse(ApiResponse.error(404, "SKU not found"));
    }

    // Attribute management
    @GetMapping("/products/{id}/attributes")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<List<ProductAttribute>> getAttributes(@PathVariable long id) {
        return ApiResponse.success(attributeRepository.findByProductId(id));
    }
}
