package com.jyfc.backend.module.sign.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.sign.service.EsignCallbackService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 只接受控制平面转发的 e签宝事件。不对外给浏览器当「我已签完」。 */
@RestController
@RequestMapping("/api/sign/callbacks/esign")
public class EsignCallbackController {

    private final EsignCallbackService callbacks;
    private final String token;

    public EsignCallbackController(EsignCallbackService callbacks,
                                   @Value("${jy.sign.callback-token:}") String token) {
        this.callbacks = callbacks;
        this.token = token;
    }

    @PostMapping
    public ApiResponse<Void> receive(@RequestHeader(value = "X-Esign-Callback-Token", required = false) String header,
                                     @RequestBody Map<String, Object> body) {
        if (token == null || token.isBlank() || header == null || !token.equals(header)) {
            throw new BusinessException("回调令牌无效");
        }
        callbacks.apply(str(body.get("eventKey")), str(body.get("flowId")), str(body.get("action")),
                str(body.get("account")), str(body.get("reason")));
        return ApiResponse.success(null);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
