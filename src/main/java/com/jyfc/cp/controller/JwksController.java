package com.jyfc.cp.controller;

import com.jyfc.cp.config.CpRsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CP 的公钥集（JWKS）。数据平面用它<b>离线</b>验 passport（{@code CpTokenVerifier}
 * 拉 {@code {cpBaseUrl}/cp/.well-known/jwks.json}），因此本端点必须公网可读、
 * 且只含公钥。
 * <p>
 * 公开的是"谁能验票"，不是"谁能开票"：私钥永不离开 CP，所以客户即便完全控制 DP
 * 机器也造不出合法 passport。
 */
@RestController
public class JwksController {

    private final CpRsaKeyProvider keyProvider;

    public JwksController(CpRsaKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @GetMapping("/cp/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keyProvider.jwks();
    }
}
