package com.jyfc.backend.module.product.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.product.entity.ProductCategory;
import com.jyfc.backend.module.product.repository.ProductCategoryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin product category CRUD.
 */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final ProductCategoryRepository categoryRepository;

    public AdminCategoryController(ProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * List all categories.
     * Supports optional tree=true to return nested tree structure.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<?> list(
            @RequestParam(required = false, defaultValue = "false") boolean tree) {
        List<ProductCategory> all = categoryRepository.findAll();
        if (tree) {
            return ApiResponse.success(buildTree(all));
        }
        return ApiResponse.success(all);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ApiResponse<ProductCategory> getById(@PathVariable Long id) {
        if (id == null) return ApiResponse.error(400, "id is required");
        return categoryRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "Category not found"));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<ProductCategory> create(@RequestBody Map<String, Object> body) {
        ProductCategory cat = new ProductCategory();
        cat.setName((String) body.get("name"));

        if (body.containsKey("parentId") && body.get("parentId") != null) {
            cat.setParentId(Long.valueOf(String.valueOf(body.get("parentId"))));
            // Verify parent exists
            if (!categoryRepository.existsById(cat.getParentId())) {
                return ApiResponse.error(400, "Parent category not found: " + cat.getParentId());
            }
        }
        cat.setIcon((String) body.get("icon"));
        if (body.containsKey("sortOrder")) {
            cat.setSortOrder(Integer.valueOf(String.valueOf(body.get("sortOrder"))));
        }

        ProductCategory saved = categoryRepository.save(cat);
        return ApiResponse.success("Category created successfully", saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<ProductCategory> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (id == null) return ApiResponse.error(400, "id is required");
        return categoryRepository.findById(id).map(cat -> {
            if (body.containsKey("name")) {
                cat.setName((String) body.get("name"));
            }
            if (body.containsKey("parentId")) {
                Long parentId = body.get("parentId") != null
                        ? Long.valueOf(String.valueOf(body.get("parentId"))) : null;
                // Prevent self-referencing
                if (parentId != null && parentId.equals(id)) {
                    return ApiResponse.<ProductCategory>error(400, "Cannot set self as parent");
                }
                // Verify parent exists
                if (parentId != null && !categoryRepository.existsById(parentId)) {
                    return ApiResponse.<ProductCategory>error(400, "Parent category not found: " + parentId);
                }
                cat.setParentId(parentId);
            }
            if (body.containsKey("icon")) {
                cat.setIcon((String) body.get("icon"));
            }
            if (body.containsKey("sortOrder")) {
                cat.setSortOrder(Integer.valueOf(String.valueOf(body.get("sortOrder"))));
            }

            ProductCategory saved = categoryRepository.save(cat);
            return ApiResponse.<ProductCategory>success("Category updated successfully", saved);
        }).orElse(ApiResponse.error(404, "Category not found"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (id == null) return ApiResponse.error(400, "id is required");
        return categoryRepository.findById(id).map(cat -> {
            // Check for child categories
            List<ProductCategory> all = categoryRepository.findAll();
            boolean hasChildren = all.stream().anyMatch(c -> id.equals(c.getParentId()));
            if (hasChildren) {
                return ApiResponse.<Void>error(400, "Cannot delete category with children; remove children first");
            }
            categoryRepository.delete(cat);
            return ApiResponse.<Void>success("Category deleted successfully", null);
        }).orElse(ApiResponse.error(404, "Category not found"));
    }

    // ========== Tree builder ==========

    /** Build a nested tree from a flat list of categories. */
    private List<Map<String, Object>> buildTree(List<ProductCategory> all) {
        // Map: id -> node (with children list)
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (ProductCategory cat : all) {
            Long catId = cat.getId();
            if (catId == null) continue;
            Map<String, Object> node = new HashMap<>();
            node.put("id", catId);
            node.put("name", cat.getName());
            node.put("parentId", cat.getParentId());
            node.put("icon", cat.getIcon());
            node.put("sortOrder", cat.getSortOrder());
            node.put("createdAt", cat.getCreatedAt());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(catId, node);
        }

        for (ProductCategory cat : all) {
            Long catId = cat.getId();
            if (catId == null) continue;
            Map<String, Object> node = nodeMap.get(catId);
            Long parentId = cat.getParentId();
            if (parentId != null && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }

        return roots;
    }
}
