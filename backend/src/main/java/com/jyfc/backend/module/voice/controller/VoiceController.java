package com.jyfc.backend.module.voice.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.voice.adapter.VoiceAdapter;
import com.jyfc.backend.module.voice.entity.VoiceSessionEntity;
import com.jyfc.backend.module.voice.entity.VoiceTranscriptSegmentEntity;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 语音会话接口层（M3'）。⚠️ 真实 RTC 需腾讯云 TRTC 凭据（外部凭据未到位）；
 * 本切片交付：会话生命周期 + 转写双轨留痕 + 录音元数据骨架（prd12），供凭据到位后接 RTC。
 * 会话为私有视野：仅会话归属人可读写。
 */
@RestController
@RequestMapping("/api/voice")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class VoiceController {

    @PersistenceContext
    private EntityManager em;

    private final UserContextUtil userContextUtil;

    /** RTC 适配器（stub/trtc 二选一，由 jy.voice.provider 决定；缺省 stub 留痕骨架）。 */
    private final ObjectProvider<VoiceAdapter> voiceAdapter;

    public VoiceController(UserContextUtil userContextUtil, ObjectProvider<VoiceAdapter> voiceAdapter) {
        this.userContextUtil = userContextUtil;
        this.voiceAdapter = voiceAdapter;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    /** 会话私有视野：存在 + 同租户 + 归属人本人。 */
    private VoiceSessionEntity sessionForOwner(Long id) {
        VoiceSessionEntity s = em.find(VoiceSessionEntity.class, id);
        if (s == null || !s.getTenantId().equals(tenant())) throw new BusinessException("会话不存在或跨租户");
        if (!Objects.equals(s.getUserId(), userContextUtil.getCurrentUserId())) {
            throw new BusinessException("仅会话归属人可操作");
        }
        return s;
    }

    @PostMapping("/sessions")
    @Transactional
    public ApiResponse<VoiceSessionEntity> start(@RequestBody(required = false) Map<String, Object> body) {
        VoiceSessionEntity s = new VoiceSessionEntity();
        s.setTenantId(tenant());
        s.setUserId(userContextUtil.getCurrentUserId());
        if (body != null) {
            s.setBizType(String.valueOf(body.getOrDefault("bizType", "AGENT_CHAT")));
            if (body.get("bizId") != null) s.setBizId(Long.valueOf(String.valueOf(body.get("bizId"))));
        }
        em.persist(s);
        return ApiResponse.success(s);
    }

    @PostMapping("/sessions/{id}/transcript")
    @Transactional
    public ApiResponse<VoiceTranscriptSegmentEntity> transcript(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sessionForOwner(id);
        VoiceTranscriptSegmentEntity t = new VoiceTranscriptSegmentEntity();
        t.setTenantId(tenant());
        t.setSessionId(id);
        t.setSpeaker(body.getOrDefault("speaker", "user"));
        t.setContent(body.getOrDefault("content", ""));
        t.setStartedAt(LocalDateTime.now());
        em.persist(t);
        return ApiResponse.success(t);
    }

    @PostMapping("/sessions/{id}/stop")
    @Transactional
    public ApiResponse<VoiceSessionEntity> stop(@PathVariable Long id) {
        VoiceSessionEntity s = sessionForOwner(id);
        s.setStatus("ENDED");
        s.setEndedAt(LocalDateTime.now());
        return ApiResponse.success(s);
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        VoiceSessionEntity s = sessionForOwner(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", s);
        out.put("segments", em.createQuery(
                "SELECT t FROM VoiceTranscriptSegmentEntity t WHERE t.sessionId = :id ORDER BY t.id",
                VoiceTranscriptSegmentEntity.class).setParameter("id", id).getResultList());
        out.put("rtc", voiceAdapter.stream().findFirst()
                .<Object>map(a -> a.startRoom(id, String.valueOf(s.getUserId())))
                .orElse("TRTC 凭据未到位：本会话为留痕骨架（prd12 双轨留痕已落表）"));
        return ApiResponse.success(out);
    }
}
