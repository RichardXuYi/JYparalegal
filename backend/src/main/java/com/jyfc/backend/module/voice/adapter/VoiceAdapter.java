package com.jyfc.backend.module.voice.adapter;

import java.util.Map;

/**
 * 语音 RTC 适配器契约（M3'，prd12 双轨留痕之外的实时通道）。
 * <p>
 * 生产实现 = 腾讯云 TRTC（{@link TrtcVoiceAdapter}，需 sdk-app-id / secret-key 凭据）；
 * 缺省为 {@link StubVoiceAdapter} 留痕骨架（jy.voice.provider=stub 或未配置）。
 */
public interface VoiceAdapter {

    /**
     * 开启实时房间：返回客户端加入房间所需的 payload
     * （roomId / sdkAppId / userId / userSig 等，键集由实现方定义）。
     *
     * @param sessionId 语音会话 id（留痕表主键，用于归因）
     * @param userId    业务用户 id（作为 TRTC userId）
     */
    Map<String, Object> startRoom(Long sessionId, String userId);

    /**
     * 生成 TRTC UserSig（TLSSigAPIv2 算法）。
     *
     * @param userId    TRTC 用户 id
     * @param expireSec 有效期（秒）
     */
    String genUserSig(String userId, int expireSec);
}
