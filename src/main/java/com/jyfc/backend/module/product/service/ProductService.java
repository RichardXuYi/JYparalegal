package com.jyfc.backend.module.product.service;

import com.jyfc.backend.module.product.dto.ProductDTO;
import com.jyfc.backend.module.product.entity.Product;
import com.jyfc.backend.module.product.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(value = "products", key = "#root.methodName + ':' + #page + ':' + #size")
    public Page<Product> listProducts(int page, int size, Integer status, Long categoryId, String keyword, String productType) {
        int pageNum = Math.max(0, page - 1);
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by("createdAt").descending());

        // Build query based on available filters
        if (keyword != null && !keyword.isBlank()) {
            return productRepository.findByNameContaining(keyword, pageable);
        }
        if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        }
        if (status != null && productType != null) {
            return productRepository.findByStatusAndProductType(status, productType, pageable);
        }
        if (status != null) {
            return productRepository.findByStatus(status, pageable);
        }
        if (productType != null) {
            return productRepository.findByProductType(productType, pageable);
        }
        return productRepository.findAll(pageable);
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product createProduct(ProductDTO dto) {
        Product p = new Product();
        updateProductFromDto(p, dto);
        return productRepository.save(p);
    }

    @CacheEvict(value = "products", allEntries = true)
    public Product updateProduct(Long id, Map<String, Object> payload) {
        if (id == null) throw new RuntimeException("Product id must not be null");
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        if (payload.containsKey("name")) p.setName((String) payload.get("name"));
        if (payload.containsKey("subtitle")) p.setSubtitle((String) payload.get("subtitle"));
        if (payload.containsKey("description")) p.setDescription((String) payload.get("description"));
        if (payload.containsKey("status")) p.setStatus((Integer) payload.get("status"));
        if (payload.containsKey("mainImage")) p.setMainImage((String) payload.get("mainImage"));
        if (payload.containsKey("detailImages")) p.setDetailImages((String) payload.get("detailImages"));
        if (payload.containsKey("categoryId")) p.setCategoryId(Long.valueOf(String.valueOf(payload.get("categoryId"))));
        if (payload.containsKey("salesCount")) p.setSalesCount(Integer.valueOf(String.valueOf(payload.get("salesCount"))));
        if (payload.containsKey("rating")) p.setRating(new BigDecimal(String.valueOf(payload.get("rating"))));
        if (payload.containsKey("stock")) p.setStock(Integer.valueOf(String.valueOf(payload.get("stock"))));

        if (payload.containsKey("productType")) p.setProductType((String) payload.get("productType"));
        if (payload.containsKey("author")) p.setAuthor((String) payload.get("author"));
        if (payload.containsKey("isbn")) p.setIsbn((String) payload.get("isbn"));
        if (payload.containsKey("duration")) p.setDuration(payload.get("duration") != null ? Integer.valueOf(String.valueOf(payload.get("duration"))) : null);
        if (payload.containsKey("format")) p.setFormat((String) payload.get("format"));

        BigDecimal priceVal = null;
        if (payload.containsKey("price")) {
            Object price = payload.get("price");
            priceVal = price != null ? new BigDecimal(String.valueOf(price)) : BigDecimal.ZERO;
        } else if (payload.containsKey("currentPrice")) {
            Object price = payload.get("currentPrice");
            priceVal = price != null ? new BigDecimal(String.valueOf(price)) : BigDecimal.ZERO;
        }
        if (priceVal != null) p.setPrice(priceVal);

        if (payload.containsKey("originalPrice")) {
            Object price = payload.get("originalPrice");
            p.setOriginalPrice(price != null ? new BigDecimal(String.valueOf(price)) : null);
        }

        return productRepository.save(p);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {
        if (id == null) return;
        productRepository.deleteById(id);
    }

    public Optional<Product> findById(Long id) {
        if (id == null) return Optional.empty();
        return productRepository.findById(id);
    }

    private void updateProductFromDto(Product p, ProductDTO dto) {
        p.setName(dto.getName());
        p.setSubtitle(dto.getSubtitle());
        p.setDescription(dto.getDescription());
        p.setMainImage(dto.getMainImage());
        if (dto.getCurrentPrice() != null) p.setPrice(dto.getCurrentPrice());
        if (dto.getOriginalPrice() != null) p.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getStock() != null) p.setStock(dto.getStock());

        try {
            if (dto.getCategory() != null) p.setCategoryId(Long.parseLong(dto.getCategory()));
        } catch (NumberFormatException e) {
            // ignore
        }

        if (dto.getProductType() != null) p.setProductType(dto.getProductType());
        p.setAuthor(dto.getAuthor());
        p.setIsbn(dto.getIsbn());
        p.setDuration(dto.getDuration());
        p.setFormat(dto.getFormat());
    }
}
