package com.jyfc.cp.esign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * e签宝 SaaS API V3 签署业务封装。
 * <p>
 * 将 DP 的 chokepoint 请求翻译成 e签宝 V3 API 调用。
 */
@Service
public class EsignSigningService {

    private static final Logger log = LoggerFactory.getLogger(EsignSigningService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EsignClient client;

    public EsignSigningService(EsignClient client) {
        this.client = client;
    }

    /**
     * 基于文件创建签署流程（对应 DP 的 execute）。
     * <p>若带文件字节，则先走 e签宝文件上传链路（file-upload-url → PUT → 轮询就绪）拿到 fileId，
     * 再以 docs:[{fileId}] + signers 创建流程；无文件时退化为空流程（仅联调用）。
     *
     * @param fileBytes 合同文件字节；可为 null（退化空流程）
     * @param fileName  文件名（决定 e签宝侧展示名与格式识别）
     * @param signers   签署人描述：[{signerType: PERSON|ORG, account, orgId, signOrder}]；可为 null/空
     * @return {providerTaskId, provider, esignMode, fileId}
     */
    public Map<String, Object> createSignFlow(String taskRef, String title, String docSha256,
                                              byte[] fileBytes, String fileName,
                                              List<Map<String, Object>> signers) {
        List<Map<String, Object>> docs = new ArrayList<>();
        String fileId = null;
        if (fileBytes != null && fileBytes.length > 0) {
            fileId = uploadFile(fileBytes, fileName);
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("fileId", fileId);
            if (fileName != null && !fileName.isBlank()) doc.put("fileName", fileName);
            docs.add(doc);
        }

        List<Map<String, Object>> esignSigners = new ArrayList<>();
        if (fileId != null && signers != null) {
            int idx = 0;
            for (Map<String, Object> s : signers) {
                esignSigners.add(buildSigner(s, fileId, idx++));
            }
        }

        Map<String, Object> flowConfig = new LinkedHashMap<>();
        flowConfig.put("signFlowTitle", title != null ? title : "签署任务-" + taskRef);
        flowConfig.put("autoFinish", true);
        flowConfig.put("chargeConfig", Map.of("chargeMode", 0));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("docs", docs);
        body.put("signFlowConfig", flowConfig);
        body.put("signers", esignSigners);

        JsonNode resp = client.post("/v3/sign-flow/create-by-file", body);
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            throw new EsignApiException("创建签署流程失败: " + msg);
        }

        JsonNode data = EsignClient.data(resp);
        String flowId = data.path("signFlowId").asText(null);
        log.info("e签宝签署流程已创建: taskRef={} flowId={} docs={} signers={}", taskRef, flowId, docs.size(), esignSigners.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerTaskId", flowId);
        result.put("provider", "esign-saas-v3");
        result.put("esignMode", "NORMAL");
        if (fileId != null) result.put("fileId", fileId);
        return result;
    }

    /**
     * 构造单个 e签宝签署人（镜像官方 demo 结构：noticeConfig + signerInfo + signConfig + signFields）。
     * 个人 signerType=0 用 psnAccount（手机号/邮箱）；机构 signerType=1 用 orgId（需已完成机构实名）。
     */
    private Map<String, Object> buildSigner(Map<String, Object> s, String fileId, int idx) {
        boolean org = "ORG".equalsIgnoreCase(String.valueOf(s.getOrDefault("signerType", "PERSON")));
        int order = 1;
        try {
            if (s.get("signOrder") != null) order = Integer.parseInt(String.valueOf(s.get("signOrder")));
        } catch (NumberFormatException ignore) {
            // 保留默认 1
        }

        Map<String, Object> signer = new LinkedHashMap<>();
        signer.put("noticeConfig", Map.of("noticeTypes", "1"));
        if (org) {
            String orgId = trimToNull(s.get("orgId"));
            if (orgId == null) {
                throw new EsignApiException("机构签署人缺少 e签宝 orgId：需先完成机构实名后再送签");
            }
            signer.put("orgSignerInfo", Map.of("orgId", orgId));
            signer.put("signerType", 1);
        } else {
            String account = trimToNull(s.get("account"));
            if (account == null) account = trimToNull(s.get("phone"));
            if (account == null) account = trimToNull(s.get("email"));
            if (account == null) {
                throw new EsignApiException("个人签署人缺少 e签宝账号：需提供手机号或邮箱");
            }
            Map<String, Object> psn = new LinkedHashMap<>();
            psn.put("psnAccount", account);
            signer.put("psnSignerInfo", psn);
            signer.put("signerType", 0);
        }

        Map<String, Object> signConfig = new LinkedHashMap<>();
        signConfig.put("signOrder", order);
        signer.put("signConfig", signConfig);
        signer.put("signFields", List.of(buildSignField(fileId, idx, org)));
        return signer;
    }

    /** 签署区：镜像官方 demo（第 1 页固定坐标；多签署人横向错开）。 */
    private Map<String, Object> buildSignField(String fileId, int idx, boolean org) {
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("positionPage", "1");
        pos.put("positionX", 120 + idx * 200);
        pos.put("positionY", 460);

        Map<String, Object> normal = new LinkedHashMap<>();
        normal.put("assignedSealId", "");
        normal.put("autoSign", org);           // 机构章自动，个人手签
        normal.put("freeMode", false);
        normal.put("movableSignField", false);
        normal.put("orgSealBizTypes", "");
        normal.put("psnSealStyles", "");
        normal.put("signFieldPosition", pos);
        normal.put("signFieldStyle", 1);

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fileId", fileId);
        field.put("normalSignFieldConfig", normal);
        field.put("signFieldType", 0);
        return field;
    }

    private static String trimToNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() ? null : s;
    }

    /**
     * e签宝文件上传链路：file-upload-url → PUT 字节 → 轮询 fileStatus 就绪 → 返回 fileId。
     */
    private String uploadFile(byte[] bytes, String fileName) {
        String contentMd5 = EsignSignatureUtil.contentMd5(bytes);
        String name = (fileName == null || fileName.isBlank()) ? "contract.pdf" : fileName;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentMd5", contentMd5);
        body.put("fileName", name);
        body.put("fileSize", bytes.length);
        body.put("convertToPDF", !name.toLowerCase().endsWith(".pdf")); // 非 PDF 交 e签宝转 PDF 才能落签署域
        body.put("contentType", "application/octet-stream");

        JsonNode resp = client.post("/v3/files/file-upload-url", body);
        if (!EsignClient.isSuccess(resp)) {
            throw new EsignApiException("获取文件上传地址失败: " + resp.path("message").asText("未知错误"));
        }
        JsonNode data = EsignClient.data(resp);
        String fileId = data.path("fileId").asText(null);
        String uploadUrl = data.path("fileUploadUrl").asText(null);
        if (fileId == null || uploadUrl == null) {
            throw new EsignApiException("文件上传地址响应缺少 fileId / fileUploadUrl");
        }

        JsonNode up = client.putRaw(uploadUrl, bytes, contentMd5, "application/octet-stream");
        String errCode = up.path("errCode").asText("0");
        if (!"0".equals(errCode)) {
            throw new EsignApiException("文件上传失败: errCode=" + errCode);
        }

        waitFileReady(fileId);
        log.info("e签宝文件已上传: fileId={} name={} size={}", fileId, name, bytes.length);
        return fileId;
    }

    /** 轮询文件状态至就绪（fileStatus 2/5）；超时/异常则放行由建流程侧报错。 */
    private void waitFileReady(String fileId) {
        for (int i = 0; i < 5; i++) {
            try {
                JsonNode resp = client.get("/v3/files/" + fileId);
                if (EsignClient.isSuccess(resp)) {
                    String status = EsignClient.data(resp).path("fileStatus").asText("");
                    if ("2".equals(status) || "5".equals(status)) return;
                }
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("查询文件状态失败（放行，交由建流程报错）: {}", e.getMessage());
                return;
            }
        }
    }

    /**
     * 获取签署页面 URL。
     */
    public Map<String, Object> getSignUrl(String flowId, String account) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientType", "ALL");
        body.put("needLogin", false);
        body.put("urlType", 1);
        if (account != null && !account.isBlank()) {
            body.put("operator", Map.of("psnAccount", account));
        }

        JsonNode resp = client.post("/v3/sign-flow/" + flowId + "/sign-url", body);
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            throw new EsignApiException("获取签署链接失败: " + msg);
        }

        JsonNode data = EsignClient.data(resp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signUrl", data.path("shortUrl").asText(data.path("url").asText(null)));
        result.put("provider", "esign-saas-v3");
        return result;
    }

    /**
     * 撤销签署流程。
     */
    public void revokeFlow(String flowId, String reason) {
        JsonNode resp = client.post("/v3/sign-flow/" + flowId + "/revoke", Map.of());
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            log.warn("撤销签署流程失败: flowId={} msg={}", flowId, msg);
        } else {
            log.info("签署流程已撤销: flowId={}", flowId);
        }
    }

    /**
     * 延期签署截止时间。
     */
    public void extendFlow(String flowId, long expireAtEpochMs) {
        // e签宝 delay 接口目前不接受自定义时间，使用默认延期
        JsonNode resp = client.post("/v3/sign-flow/" + flowId + "/delay", Map.of());
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            log.warn("延期签署失败: flowId={} msg={}", flowId, msg);
        } else {
            log.info("签署流程已延期: flowId={}", flowId);
        }
    }

    /**
     * 下载已签署文件。
     */
    public Map<String, Object> downloadFlow(String flowId) {
        JsonNode resp = client.get("/v3/sign-flow/" + flowId + "/file-download-url");
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            throw new EsignApiException("获取下载链接失败: " + msg);
        }

        JsonNode data = EsignClient.data(resp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("downloadUrl", data.path("fileDownloadUrl").asText(null));
        result.put("provider", "esign-saas-v3");
        return result;
    }

    /**
     * 解约（获取解约链接）。
     */
    public void rescindFlow(String flowId, String reason) {
        // 先尝试获取解约链接
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signFlowConfig", Map.of(
                "chargeConfig", Map.of("chargeMode", 0),
                "noticeConfig", Map.of("noticeTypes", "1,2")
        ));

        JsonNode resp = client.post("/v3/sign-flow/" + flowId + "/rescission-url", body);
        if (!EsignClient.isSuccess(resp)) {
            String msg = resp.path("message").asText("未知错误");
            log.warn("获取解约链接失败: flowId={} msg={}", flowId, msg);
            // fallback: 直接撤销
            revokeFlow(flowId, reason);
        } else {
            log.info("解约链接已生成: flowId={}", flowId);
        }
    }

    /**
     * 获取签署流程的蚂蚁链存证信息（e签宝 /v3/antchain-file-info）。
     */
    public Map<String, Object> antchainInfo(String signFlowId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signFlowId", signFlowId == null ? "" : signFlowId);
        JsonNode resp = client.post("/v3/antchain-file-info", body);
        if (!EsignClient.isSuccess(resp)) {
            throw new EsignApiException("获取区块链存证失败: " + resp.path("message").asText("未知错误"));
        }
        log.info("蚂蚁链存证信息已获取: signFlowId={}", signFlowId);
        return toMap(EsignClient.data(resp));
    }

    /**
     * 核验区块链存证文件（e签宝 /v3/antchain-file-info/verify）。
     */
    public Map<String, Object> antchainVerify(String fileHash, String antTxHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileHash", fileHash);
        body.put("antTxHash", antTxHash);
        JsonNode resp = client.post("/v3/antchain-file-info/verify", body);
        if (!EsignClient.isSuccess(resp)) {
            throw new EsignApiException("核验区块链存证失败: " + resp.path("message").asText("未知错误"));
        }
        log.info("蚂蚁链存证核验完成: antTxHash={}", antTxHash);
        return toMap(EsignClient.data(resp));
    }

    /** data 节点 → Map；附 provider 标识。 */
    private static Map<String, Object> toMap(JsonNode data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", "esign-saas-v3");
        if (data != null && data.isObject()) {
            data.fields().forEachRemaining(e -> out.put(e.getKey(), MAPPER.convertValue(e.getValue(), Object.class)));
        }
        return out;
    }

    /**
     * 查询签署流程详情。
     */
    public JsonNode getFlowDetail(String flowId) {
        return client.get("/v3/sign-flow/" + flowId + "/detail");
    }
}
