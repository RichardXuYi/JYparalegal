package com.jyfc.backend.module.news.controller;

import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.dashboard.entity.FavoriteEntity;
import com.jyfc.backend.module.dashboard.repository.FavoriteRepository;
import com.jyfc.backend.module.news.entity.NewsLike;
import com.jyfc.backend.module.news.repository.NewsLikeRepository;
import com.jyfc.backend.module.news.repository.NewsRepository;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/app/news")
public class NewsAppController {
    private final NewsRepository newsRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final NewsLikeRepository newsLikeRepository;

    public NewsAppController(NewsRepository newsRepository, FavoriteRepository favoriteRepository, UserRepository userRepository, NewsLikeRepository newsLikeRepository) {
        this.newsRepository = newsRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.newsLikeRepository = newsLikeRepository;
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

    @PostMapping("/{id}/like")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<Map<String, Object>> like(@PathVariable long id) {
        return newsRepository.findById(id).map(news -> {
            Long userId = getCurrentUserId();
            Optional<NewsLike> existing = newsLikeRepository.findByUserIdAndNewsId(userId, id);

            int currentLikes = news.getLikeCount() != null ? news.getLikeCount() : 0;
            boolean liked;
            if (existing.isPresent()) {
                newsLikeRepository.delete(existing.get());
                news.setLikeCount(Math.max(0, currentLikes - 1));
                liked = false;
            } else {
                NewsLike like = new NewsLike();
                like.setUserId(userId);
                like.setNewsId(id);
                newsLikeRepository.save(like);
                news.setLikeCount(currentLikes + 1);
                liked = true;
            }
            newsRepository.save(news);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("liked", liked);
            result.put("likeCount", news.getLikeCount());
            return ApiResponse.success("操作成功", result);
        }).orElse(ApiResponse.error(404, "新闻不存在"));
    }

    @PostMapping("/{id}/favorite")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    @Transactional
    public ApiResponse<Map<String, Object>> favorite(@PathVariable long id) {
        return newsRepository.findById(id).map(news -> {
            Long userId = getCurrentUserId();
            Optional<FavoriteEntity> existing = favoriteRepository.findByUserIdAndTypeAndTargetId(userId, "NEWS", id);

            boolean favorited;
            if (existing.isPresent()) {
                favoriteRepository.delete(existing.get());
                favorited = false;
            } else {
                FavoriteEntity fav = new FavoriteEntity();
                fav.setUserId(userId);
                fav.setType("NEWS");
                fav.setTargetId(id);
                fav.setTitle(news.getTitle());
                fav.setImage(news.getCoverImage());
                favoriteRepository.save(fav);
                favorited = true;
            }

            long favoriteCount = favoriteRepository.countByTypeAndTargetId("NEWS", id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("favorited", favorited);
            result.put("favoriteCount", favoriteCount);
            return ApiResponse.success("操作成功", result);
        }).orElse(ApiResponse.error(404, "新闻不存在"));
    }

    @PostMapping("/{id}/share")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<Map<String, Object>> share(@PathVariable long id) {
        return newsRepository.findById(id).map(news -> {
            int currentShares = news.getShareCount() != null ? news.getShareCount() : 0;
            news.setShareCount(currentShares + 1);
            newsRepository.save(news);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("shareCount", currentShares + 1);
            return ApiResponse.success("操作成功", result);
        }).orElse(ApiResponse.error(404, "新闻不存在"));
    }
}
