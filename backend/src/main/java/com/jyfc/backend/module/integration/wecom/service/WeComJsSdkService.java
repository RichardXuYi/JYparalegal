package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 企业微信 JS-SDK 集成服务
 *
 * 为前端页面提供调用企业微信 JS-SDK 所需的配置参数。
 * 用于在 JYFC 网页中调用企业微信原生能力：
 * - 拍照/选择图片
 * - 获取地理位置
 * - 扫码
 * - 语音录制
 * - 分享
 *
 * 使用步骤：
 * 1. 后端调用 getJsSdkConfig(url) 获取配置
 * 2. 前端注入 wx.config(config)
 * 3. wx.ready() 后调用具体 API
 *
 * 注意：企业微信 JS-SDK 需要配置 "可信域名"（JS接口安全域名）。
 */
@Service
public class WeComJsSdkService {

    private static final Logger log = LoggerFactory.getLogger(WeComJsSdkService.class);

    private final WeComProperties weComProperties;
    private final WeComTokenService tokenService;

    public WeComJsSdkService(WeComProperties weComProperties,
                             WeComTokenService tokenService) {
        this.weComProperties = weComProperties;
        this.tokenService = tokenService;
    }

    /**
     * 生成 JS-SDK 配置参数
     *
     * @param url 当前页面完整 URL（包括 URL 参数和 hash，# 之后的部分需保留）
     * @return wx.config 所需的参数字典
     */
    public Map<String, Object> getJsSdkConfig(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        Map<String, Object> signature = tokenService.generateSignature(url);

        Map<String, Object> config = Map.of(
                "corpid", weComProperties.getCorpId(),
                "agentid", weComProperties.getAgentId(),
                "timestamp", signature.get("timestamp"),
                "nonceStr", signature.get("nonceStr"),
                "signature", signature.get("signature"),
                "url", url
        );

        log.debug("JS-SDK 配置生成成功");
        return config;
    }

    /**
     * 检查 JS-SDK 配置是否可用
     *
     * @return true 如果企业微信集成已启用且配置完整
     */
    public boolean isAvailable() {
        if (!weComProperties.isEnabled()) {
            return false;
        }
        if (weComProperties.getCorpId() == null || weComProperties.getCorpId().isBlank()) {
            return false;
        }
        if (weComProperties.getAgentId() == null || weComProperties.getAgentId().isBlank()) {
            return false;
        }
        if (weComProperties.getCorpSecret() == null || weComProperties.getCorpSecret().isBlank()) {
            return false;
        }
        return true;
    }
}
