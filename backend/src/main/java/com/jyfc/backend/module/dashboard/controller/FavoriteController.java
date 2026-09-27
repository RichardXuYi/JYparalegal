package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.dashboard.entity.FavoriteEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.dashboard.repository.FavoriteRepository;
import com.jyfc.backend.module.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/app/favorites")
public class FavoriteController {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    @Autowired
    public FavoriteController(FavoriteRepository favoriteRepository, UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
             throw new RuntimeException("Unauthorized");
        }
        String username = auth.getName();
        Long userId = userRepository.findByUsername(username).map(UserEntity::getId).orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        if (userId == null) {
            throw new RuntimeException("User ID is null");
        }
        return userId;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<List<FavoriteEntity>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<FavoriteEntity> pageResult = favoriteRepository.findByUserId(userId, pageable);
        
        // Return list to match frontend expectation
        return ApiResponse.success(pageResult.getContent());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> addFavorite(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String type = (String) body.get("type");
        Object targetIdObj = body.get("targetId");
        if (targetIdObj == null) {
            return ApiResponse.error(400, "targetId is required");
        }
        Long targetId = Long.valueOf(String.valueOf(targetIdObj));
        String title = (String) body.getOrDefault("title", "");
        String image = (String) body.getOrDefault("image", "");

        Optional<FavoriteEntity> existing = favoriteRepository.findByUserIdAndTypeAndTargetId(userId, type, targetId);
        if (existing.isPresent()) {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("id", existing.get().getId());
            result.put("message", "Already favorited");
            return ApiResponse.success(result);
        }

        FavoriteEntity fav = new FavoriteEntity();
        fav.setUserId(userId);
        fav.setType(type);
        fav.setTargetId(targetId);
        fav.setTitle(title);
        fav.setImage(image);
        
        fav = favoriteRepository.save(fav);
        Map<String, Object> resultMap = new java.util.HashMap<>();
        resultMap.put("id", fav.getId());
        return ApiResponse.success(resultMap);
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<Void> removeFavorite(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String type = (String) body.get("type");
        Object idObj = body.get("id");
        if (idObj == null) {
            return ApiResponse.error(400, "id is required");
        }
        Long targetId = Long.valueOf(String.valueOf(idObj));
        
        favoriteRepository.deleteByUserIdAndTypeAndTargetId(userId, type, targetId);
        return ApiResponse.success(null);
    }
    
    @PostMapping("/toggle")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<Map<String, Object>> toggleFavorite(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        String type = (String) body.get("type");
        Object idObj = body.get("id");
        if (idObj == null) {
            return ApiResponse.error(400, "id is required");
        }
        Long targetId = Long.valueOf(String.valueOf(idObj));
        
        Optional<FavoriteEntity> existing = favoriteRepository.findByUserIdAndTypeAndTargetId(userId, type, targetId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return ApiResponse.success(Map.of("isFavorited", false));
        } else {
            FavoriteEntity fav = new FavoriteEntity();
            fav.setUserId(userId);
            fav.setType(type);
            fav.setTargetId(targetId);
            fav.setTitle((String) body.getOrDefault("title", ""));
            fav.setImage((String) body.getOrDefault("image", ""));
            favoriteRepository.save(fav);
            return ApiResponse.success(Map.of("isFavorited", true));
        }
    }
}
