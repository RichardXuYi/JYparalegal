package com.jyfc.cp.controller;

import com.jyfc.cp.esign.EsignApiException;
import com.jyfc.cp.esign.EsignSigningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CP 签署 chokepoint 接口（/cp/v1/signing）。
 * <p>
 * 这些接口的契约由 DP 的 {@code CpSigningClient} 定义。
 * CP 接收请求 → 调用 e签宝 SaaS API V3 → 返回统一格式。
 */
@RestController
@RequestMapping("/cp/v1/signing")
public class CpSigningController {

    private static final Logger log = LoggerFactory.getLogger(CpSigningController.class);

    private final EsignSigningService esignService;

    public CpSigningController(EsignSigningService esignService) {
        this.esignService = esignService;
    }

    /**
     * 送签 chokepoint：创建 e签宝签署流程。
     * <p>DP 调用: POST /cp/v1/signing/execute {taskRef, title, docSha256, fileName, fileContentBase64}
     */
    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        String taskRef = str(body.get("taskRef"));
        String title = str(body.get("title"));
        String docSha256 = str(body.get("docSha256"));
        String fileName = str(body.get("fileName"));
        String fileContentBase64 = str(body.get("fileContentBase64"));

        byte[] fileBytes = null;
        if (fileContentBase64 != null && !fileContentBase64.isBlank()) {
            try {
                fileBytes = java.util.Base64.getDecoder().decode(fileContentBase64);
            } catch (IllegalArgumentException e) {
                return error(3006, "fileContentBase64 不是合法 base64");
            }
        }

        java.util.List<java.util.Map<String, Object>> signers = new java.util.ArrayList<>();
        if (body.get("signers") instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o instanceof java.util.Map<?, ?> m) {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    m.forEach((k, v) -> row.put(String.valueOf(k), v));
                    signers.add(row);
                }
            }
        }

        log.info("CP chokepoint execute: taskRef={} title={} file={} bytes={} signers={}",
                taskRef, title, fileName, fileBytes == null ? 0 : fileBytes.length, signers.size());

        try {
            Map<String, Object> data = esignService.createSignFlow(taskRef, title, docSha256, fileBytes, fileName, signers);
            return success(data);
        } catch (EsignApiException e) {
            log.error("e签宝创建流程失败: taskRef={}", taskRef, e);
            return error(3001, e.getMessage());
        }
    }

    /**
     * 获取签署页面 URL。
     * <p>DP 调用: POST /cp/v1/signing/sign-url {flowId, account}
     */
    @PostMapping("/sign-url")
    public Map<String, Object> signUrl(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));
        String account = str(body.get("account"));

        log.info("CP sign-url: flowId={} account={}", flowId, account);

        try {
            Map<String, Object> data = esignService.getSignUrl(flowId, account);
            return success(data);
        } catch (EsignApiException e) {
            log.error("e签宝获取签署链接失败: flowId={}", flowId, e);
            return error(3002, e.getMessage());
        }
    }

    /**
     * 撤销签署流程。
     * <p>DP 调用: POST /cp/v1/signing/revoke {flowId, reason}
     */
    @PostMapping("/revoke")
    public Map<String, Object> revoke(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));
        String reason = str(body.get("reason"));

        log.info("CP revoke: flowId={} reason={}", flowId, reason);
        esignService.revokeFlow(flowId, reason);
        return success(null);
    }

    /**
     * 延期签署截止时间。
     * <p>DP 调用: POST /cp/v1/signing/extend {flowId, expireAtEpochMs}
     */
    @PostMapping("/extend")
    public Map<String, Object> extend(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));
        long expireAt = body.get("expireAtEpochMs") != null
                ? Long.parseLong(String.valueOf(body.get("expireAtEpochMs"))) : 0L;

        log.info("CP extend: flowId={} expireAt={}", flowId, expireAt);
        esignService.extendFlow(flowId, expireAt);
        return success(null);
    }

    /**
     * 下载已签署文件。
     * <p>DP 调用: POST /cp/v1/signing/download {flowId}
     */
    @PostMapping("/download")
    public Map<String, Object> download(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));

        log.info("CP download: flowId={}", flowId);

        try {
            Map<String, Object> data = esignService.downloadFlow(flowId);
            return success(data);
        } catch (EsignApiException e) {
            log.error("e签宝下载失败: flowId={}", flowId, e);
            return error(3003, e.getMessage());
        }
    }

    /**
     * 解约/作废。
     * <p>DP 调用: POST /cp/v1/signing/rescind {flowId, reason}
     */
    @PostMapping("/rescind")
    public Map<String, Object> rescind(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));
        String reason = str(body.get("reason"));

        log.info("CP rescind: flowId={} reason={}", flowId, reason);
        esignService.rescindFlow(flowId, reason);
        return success(null);
    }

    /**
     * 获取签署流程的蚂蚁链存证信息。
     * <p>DP 调用: POST /cp/v1/signing/antchain-info {flowId}
     */
    @PostMapping("/antchain-info")
    public Map<String, Object> antchainInfo(@RequestBody Map<String, Object> body) {
        String flowId = str(body.get("flowId"));
        log.info("CP antchain-info: flowId={}", flowId);
        try {
            return success(esignService.antchainInfo(flowId));
        } catch (EsignApiException e) {
            log.error("e签宝获取存证失败: flowId={}", flowId, e);
            return error(3004, e.getMessage());
        }
    }

    /**
     * 核验蚂蚁链存证文件。
     * <p>DP 调用: POST /cp/v1/signing/antchain-verify {fileHash, antTxHash}
     */
    @PostMapping("/antchain-verify")
    public Map<String, Object> antchainVerify(@RequestBody Map<String, Object> body) {
        String fileHash = str(body.get("fileHash"));
        String antTxHash = str(body.get("antTxHash"));
        log.info("CP antchain-verify: antTxHash={}", antTxHash);
        try {
            return success(esignService.antchainVerify(fileHash, antTxHash));
        } catch (EsignApiException e) {
            log.error("e签宝核验存证失败", e);
            return error(3005, e.getMessage());
        }
    }

    // ---- helpers ----

    private Map<String, Object> success(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", 0);
        r.put("msg", "success");
        r.put("data", data);
        return r;
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", code);
        r.put("msg", msg);
        r.put("data", null);
        return r;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
