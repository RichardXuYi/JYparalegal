package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import com.jyfc.backend.module.integration.wecom.entity.WeComApprovalMapping;
import com.jyfc.backend.module.integration.wecom.repository.WeComApprovalMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 企业微信 OA 审批对接服务
 *
 * 企业微信审批 API 与 OA 系统深度绑定，比钉钉/飞书更复杂。
 * 主要差异：
 * - 审批模板需要先在管理后台创建
 * - 审批表单控件类型丰富（文本、日期、金额、附件等）
 * - 需通过模板 ID 发起审批
 * - 事件回调需在管理后台配置回调 URL
 *
 * 使用独立的 approvalSecret（审批专用 Secret）。
 */
@Service
public class WeComApprovalService {

    private static final Logger log = LoggerFactory.getLogger(WeComApprovalService.class);

    private final WeComProperties weComProperties;
    private final WeComTokenService tokenService;
    private final WebClient.Builder webClientBuilder;
    private final WeComApprovalMappingRepository approvalMappingRepository;

    public WeComApprovalService(WeComProperties weComProperties,
                                WeComTokenService tokenService,
                                WebClient.Builder webClientBuilder,
                                WeComApprovalMappingRepository approvalMappingRepository) {
        this.weComProperties = weComProperties;
        this.tokenService = tokenService;
        this.webClientBuilder = webClientBuilder;
        this.approvalMappingRepository = approvalMappingRepository;
    }

    /**
     * 获取审批模板列表
     *
     * @return 审批模板列表
     */
    public List<Map<String, Object>> getApprovalTemplateList() {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.APPROVAL);
        String url = weComProperties.getBaseUrl() + "/oa/gettemplatelist?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("获取审批模板列表返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("获取审批模板列表失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        List<Map<String, Object>> templateList = castTemplateList(response.get("template_list"));
        log.info("获取企业微信审批模板列表成功, 共 {} 个", templateList != null ? templateList.size() : 0);
        return templateList;
    }

    /**
     * 安全地将 Object 转换为 {@code List<Map<String, Object>>}。
     * <p>先做 {@code instanceof List<?>} 运行时检查，避免 unchecked cast 导致 ClassCastException。</p>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castTemplateList(Object raw) {
        if (raw instanceof List<?> list) {
            return (List<Map<String, Object>>) (List<?>) list;
        }
        return null;
    }

    /**
     * 发起审批申请
     *
     * @param templateId 审批模板 ID
     * @param applicant  申请人 UserID
     * @param formData   表单数据（模板控件的 name 与 value）
     * @param summary    审批摘要（显示在通知卡片上）
     * @return 企业微信 API 响应，包含 sp_no（审批编号）
     */
    public Map<String, Object> createApproval(String templateId, String applicant,
                                              List<Map<String, Object>> formData, String summary) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.APPROVAL);
        String url = weComProperties.getBaseUrl() + "/oa/applyevent?access_token=" + accessToken;

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("creator_userid", applicant);
        body.put("template_id", templateId);
        body.put("use_template_approver", 1);
        Map<String, Object> applyData = new java.util.HashMap<>();
        applyData.put("contents", formData);
        body.put("apply_data", applyData);
        Map<String, Object> summaryItem = new java.util.HashMap<>();
        summaryItem.put("text", summary);
        summaryItem.put("lang", "zh_CN");
        body.put("summary_list", List.of(Map.of("summary_info", List.of(summaryItem))));

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("发起审批返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("发起审批失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        String spNo = (String) response.get("sp_no");
        log.info("企业微信审批发起成功: sp_no={}", spNo);
        return response;
    }

    /**
     * 发起审批并保存映射记录
     *
     * @param contractId  合同 ID
     * @param templateId  审批模板 ID
     * @param applicant   申请人 UserID
     * @param formData    表单数据
     * @param summary     审批摘要
     * @return 企业微信 API 响应
     */
    @Transactional
    public Map<String, Object> createApprovalWithMapping(Long contractId, String templateId, String applicant,
                                                          List<Map<String, Object>> formData, String summary) {
        Map<String, Object> response = createApproval(templateId, applicant, formData, summary);
        String spNo = (String) response.get("sp_no");

        WeComApprovalMapping mapping = new WeComApprovalMapping();
        mapping.setContractId(contractId);
        mapping.setWecomSpNo(spNo);
        mapping.setTemplateId(templateId);
        mapping.setStatus("审批中");
        mapping.setApplicantUserid(applicant);
        approvalMappingRepository.save(mapping);

        log.info("审批映射保存成功: contractId={}, sp_no={}", contractId, spNo);
        return response;
    }

    /**
     * 获取审批详情
     *
     * @param spNo 审批编号
     * @return 审批详情
     */
    public Map<String, Object> getApprovalDetail(String spNo) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.APPROVAL);
        String url = weComProperties.getBaseUrl() + "/oa/getapprovaldetail?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of("sp_no", spNo != null ? spNo : ""))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("获取审批详情返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("获取审批详情失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        // 更新本地状态
        String status = resolveApprovalStatus(response);
        if (status != null) {
            Optional<WeComApprovalMapping> mappingOpt = approvalMappingRepository.findByWecomSpNo(spNo);
            mappingOpt.ifPresent(mapping -> {
                mapping.setStatus(status);
                approvalMappingRepository.save(mapping);
            });
        }

        log.info("获取审批详情成功: sp_no={}", spNo);
        return response;
    }

    /**
     * 获取审批申请数据
     *
     * @param spNo 审批编号
     * @return 审批申请数据
     */
    public Map<String, Object> getApprovalData(String spNo) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.APPROVAL);
        String url = weComProperties.getBaseUrl() + "/oa/getapprovaldata?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of("sp_no", spNo != null ? spNo : ""))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("获取审批数据返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("获取审批数据失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.debug("获取审批数据成功: sp_no={}", spNo);
        return response;
    }

    /**
     * 从审批详情中解析审批状态
     *
     * @param detail 审批详情响应
     * @return 审批状态文本
     */
    private String resolveApprovalStatus(Map<String, Object> detail) {
        Object rawInfo = detail.get("info");
        if (rawInfo instanceof Map<?, ?> info) {
            if (info.containsKey("sp_status")) {
                Integer spStatus = (Integer) info.get("sp_status");
                return switch (spStatus) {
                    case 1 -> "审批中";
                    case 2 -> "已通过";
                    case 3 -> "已驳回";
                    case 4 -> "已撤销";
                    case 6 -> "通过中";
                    default -> "未知";
                };
            }
        }
        return null;
    }
}
