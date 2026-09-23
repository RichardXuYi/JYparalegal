package com.jyfc.backend.module.voice.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音适配器缺省桩实现（S1'）：TRTC 凭据未到位时返回留痕骨架 payload（prd12）。
 * ⚠️ 非生产：jy.voice.provider=trtc 且配置腾讯云凭据后由 {@link TrtcVoiceAdapter} 接管。
 */
@Component
@ConditionalOnProperty(name = "jy.voice.provider", havingValue = "stub", matchIfMissing = true)
public class StubVoiceAdapter implements VoiceAdapter {

    @Override
    public Map<String, Object> startRoom(Long sessionId, String userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "stub");
        m.put("sessionId", sessionId);
        m.put("userId", userId);
        m.put("note", "TRTC 凭据未到位：本会话为留痕骨架（prd12 双轨留痕已落表）");
        return m;
    }

    @Override
    public String genUserSig(String userId, int expireSec) {
        return "";
    }
}
