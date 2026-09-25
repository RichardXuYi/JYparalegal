package com.jyfc.backend.module.dashboard.controller;

import com.jyfc.backend.shared.dto.ApiResponse;
import com.jyfc.backend.module.dashboard.entity.ConsultationEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.dashboard.repository.ConsultationRepository;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app/consultations")
public class ConsultationController {

    private final ConsultationRepository consultationRepository;
    private final UserRepository userRepository;

    @Autowired
    public ConsultationController(ConsultationRepository consultationRepository, UserRepository userRepository) {
        this.consultationRepository = consultationRepository;
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

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<ConsultationEntity> createConsultation(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        
        ConsultationEntity consultation = new ConsultationEntity();
        consultation.setUserId(userId);
        consultation.setSubject(body.getOrDefault("subject", "General Inquiry"));
        consultation.setDescription(body.get("description"));
        consultation.setStatus("REQUESTED");
        
        consultation = consultationRepository.save(consultation);
        
        return ApiResponse.success(consultation);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ApiResponse<List<ConsultationEntity>> listMyConsultations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        Long userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<ConsultationEntity> pageResult = consultationRepository.findByUserId(userId, pageable);
        
        // Return just the list to match frontend expectation
        return ApiResponse.success(pageResult.getContent());
    }
}
