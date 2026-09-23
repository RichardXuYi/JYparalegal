package com.jyfc.backend.module.marketing.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.marketing.dto.DiscountDTO;
import com.jyfc.backend.module.marketing.entity.Discount;
import com.jyfc.backend.module.marketing.repository.DiscountRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/discounts")
public class AdminDiscountController {
    private final DiscountRepository discountRepository;

    public AdminDiscountController(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<Page<Discount>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Integer status) {
        int pageNum = Math.max(0, page - 1);
        int pageSize = Math.max(1, Math.min(size, 100));
        var pageable = PageRequest.of(pageNum, pageSize);
        Page<Discount> p = status == null ? discountRepository.findAll(pageable) : discountRepository.findByStatus(status, pageable);
        return ApiResponse.success(p);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Discount> create(@Valid @RequestBody DiscountDTO dto) {
        Discount d = new Discount();
        d.setName(dto.getName());
        d.setType(dto.getType());
        d.setValue(dto.getValue());
        d.setStartTime(dto.getStartTime());
        d.setEndTime(dto.getEndTime());
        d.setStatus(1); // 默认启用
        return ApiResponse.success(discountRepository.save(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Discount> update(@PathVariable Long id, @Valid @RequestBody DiscountDTO dto) {
        return discountRepository.findById(id).map(d -> {
            d.setName(dto.getName());
            d.setType(dto.getType());
            d.setValue(dto.getValue());
            d.setStartTime(dto.getStartTime());
            d.setEndTime(dto.getEndTime());
            return ApiResponse.success("Discount updated successfully", discountRepository.save(d));
        }).orElse(ApiResponse.error(404, "Discount not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return discountRepository.findById(id).map(d -> {
            discountRepository.delete(d);
            return ApiResponse.success("Discount deleted successfully", (Void)null);
        }).orElse(ApiResponse.error(404, "Discount not found"));
    }
}
