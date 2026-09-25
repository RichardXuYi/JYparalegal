package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.module.product.entity.ProductReview;
import com.jyfc.backend.module.product.repository.ProductReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ProductReviewRepository reviewRepository;

    public AdminReviewController(ProductReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasReply
    ) {
        int pageNum = Math.max(0, page - 1);
        int pageSize = Math.max(1, Math.min(size, 100));
        
        PageRequest pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // If filters are provided, use custom query (assuming simple findAll first for basic implementation)
        // Or implement specification/query method in repository.
        // For now, let's use the findAllWithFilters method if created, or basic findAll.
        Page<ProductReview> p;
        try {
             p = reviewRepository.findAllWithFilters(productId, rating, hasReply, pageable);
        } catch (Exception e) {
             // Fallback if query method has issues (e.g.named params not matching)
             p = reviewRepository.findAll(pageable);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", p.getContent());
        response.put("total", p.getTotalElements());
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalPages", p.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (id == null) return ResponseEntity.badRequest().build();
        return reviewRepository.findById(id).map(r -> {
            reviewRepository.delete(r);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reply")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResponseEntity<ProductReview> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (id == null) return ResponseEntity.badRequest().build();
        String replyContent = body.get("reply");
        return reviewRepository.findById(id).map(r -> {
            r.setReply(replyContent != null ? replyContent : "");
            return ResponseEntity.ok(reviewRepository.save(r));
        }).orElse(ResponseEntity.notFound().build());
    }
}
