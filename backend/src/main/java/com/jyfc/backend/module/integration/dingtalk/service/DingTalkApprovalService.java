package com.jyfc.backend.module.integration.dingtalk.service;

import com.jyfc.backend.module.integration.dingtalk.entity.DingTalkApprovalMapping;
import com.jyfc.backend.module.integration.dingtalk.repository.DingTalkApprovalMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉审批流对接服务。
 * <p>
 * 功能：
 * <ul>
 *   <li>创建钉钉审批实例 - 将 JYFC 合同审批映射到钉钉审批</li>
 *   <li>查询审批状态</li>
 *   <li>注册审批回调</li>
 * </ul>
 * <p>
 * 注意：钉钉审批流程模板需要在钉钉管理后台预先创建，并获取 process_code。
 */
@Service
public class DingTalkApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkApprovalService.class);

    private final DingTalkAuthService authService;
    private final DingTalkApprovalMappingRepository mappingRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DingTalkApprovalService(DingTalkAuthService authService,
                                   DingTalkApprovalMappingRepository mappingRepository,
                                   ObjectMapper objectMapper) {
        this.authService = authService;
        this.mappingRepository = mappingRepository;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.dingtalk.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 创建钉钉审批实例。
     * <p>
     * 将 JYFC 合同审批流程映射到钉钉审批，并保存映射关系。
     *
     * @param contractId  JYFC 合同 ID
     * @param userId      发起审批的钉钉 userId
     * @param processCode 钉钉审批流程模板 code
     * @param formData    审批表单数据（字段名 → 值）
     * @param title       审批实例标题
     * @return 审批实例信息（包含 approvalId）
     */
    public Map<String, Object> createApprovalProcess(Long contractId, String userId,
                                                      String processCode, Map<String, Object> formData,
                                                      String title) {
        log.info("创建钉钉审批实例，合同ID: {}, 流程code: {}", contractId, processCode);

        String accessToken = authService.getAccessToken();

        // 构建审批表单组件列表
        List<Map<String, Object>> formComponentValues = formData.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> component = new HashMap<>();
                    component.put("name", entry.getKey());
                    component.put("value", entry.getValue().toString());
                    return component;
                })
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("process_code", processCode);
        body.put("originator_user_id", userId);
        body.put("form_component_values", formComponentValues);
        body.put("title", title != null ? title : "JYFC 合同审批");

        try {
            String response = webClient.post()
                    .uri("/v1.0/workflow/processInstances")
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String approvalId = json.path("processInstanceId").asText();

            if (approvalId == null || approvalId.isEmpty()) {
                log.error("创建审批实例失败: {}", response);
                throw new RuntimeException("创建审批实例失败: " + response);
            }

            // 保存映射关系
            DingTalkApprovalMapping mapping = new DingTalkApprovalMapping();
            mapping.setContractId(contractId);
            mapping.setDingtalkApprovalId(approvalId);
            mapping.setDingtalkProcessCode(processCode);
            mapping.setStatus("RUNNING");
            mappingRepository.save(mapping);

            log.info("审批实例创建成功，approvalId: {}", approvalId);
            return Map.of(
                    "success", true,
                    "approvalId", approvalId,
                    "contractId", contractId
            );
        } catch (Exception e) {
            log.error("创建审批实例异常", e);
            throw new RuntimeException("创建审批实例失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询钉钉审批实例状态。
     *
     * @param approvalId 钉钉审批实例 ID
     * @return 审批状态信息
     */
    public Map<String, Object> getApprovalStatus(String approvalId) {
        log.info("查询审批状态: {}", approvalId);

        String accessToken = authService.getAccessToken();

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1.0/workflow/processInstances/" + approvalId)
                            .build())
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(response);
            String status = json.path("status").asText();

            // 更新本地映射状态
            mappingRepository.findByDingtalkApprovalId(approvalId).ifPresent(mapping -> {
                mapping.setStatus(status);
                mappingRepository.save(mapping);
            });

            return Map.of(
                    "success", true,
                    "approvalId", approvalId,
                    "status", status,
                    "result", json.path("result").asText(""),
                    "originatorId", json.path("originatorUserId").asText("")
            );
        } catch (Exception e) {
            log.error("查询审批状态异常", e);
            throw new RuntimeException("查询审批状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据合同 ID 查询对应的钉钉审批状态。
     *
     * @param contractId JYFC 合同 ID
     * @return 审批状态信息，如果未找到映射则返回 null
     */
    public Map<String, Object> getApprovalStatusByContractId(Long contractId) {
        return mappingRepository.findByContractId(contractId)
                .map(mapping -> {
                    Map<String, Object> result = getApprovalStatus(mapping.getDingtalkApprovalId());
                    result.put("contractId", contractId);
                    return result;
                })
                .orElse(null);
    }

    /**
     * 处理审批回调通知。
     * <p>
     * 钉钉在审批状态变更时会调用此接口。
     *
     * @param callbackData 回调数据
     * @return 处理结果
     */
    public Map<String, Object> handleApprovalCallback(Map<String, Object> callbackData) {
        String approvalId = (String) callbackData.get("processInstanceId");
        String status = (String) callbackData.get("type");

        log.info("收到审批回调: approvalId={}, status={}", approvalId, status);

        if (approvalId != null) {
            mappingRepository.findByDingtalkApprovalId(approvalId).ifPresent(mapping -> {
                mapping.setStatus(status);
                mappingRepository.save(mapping);
                log.info("更新审批映射状态: contractId={}, status={}", mapping.getContractId(), status);
            });
        }

        return Map.of("success", true);
    }

    /**
     * 撤销审批实例。
     *
     * @param approvalId 钉钉审批实例 ID
     * @param userId     操作人钉钉 userId
     * @param remark     撤销原因
     * @return 操作结果
     */
    public Map<String, Object> cancelApproval(String approvalId, String userId, String remark) {
        log.info("撤销审批实例: {}", approvalId);

        String accessToken = authService.getAccessToken();

        Map<String, Object> body = new HashMap<>();
        body.put("processInstanceId", approvalId);
        body.put("operating_user_id", userId);
        body.put("remark", remark != null ? remark : "JYFC 系统撤销");

        try {
            String response = webClient.post()
                    .uri("/v1.0/workflow/processInstances/cancel")
                    .header("x-acs-dingtalk-access-token", accessToken)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("撤销审批实例响应: {}", response);

            // 更新本地状态
            mappingRepository.findByDingtalkApprovalId(approvalId).ifPresent(mapping -> {
                mapping.setStatus("CANCELLED");
                mappingRepository.save(mapping);
            });

            return Map.of("success", true);
        } catch (Exception e) {
            log.error("撤销审批异常", e);
            throw new RuntimeException("撤销审批失败: " + e.getMessage(), e);
        }
    }
}
