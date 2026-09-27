package com.jyfc.backend.module.integration.wecom.service;

import com.jyfc.backend.module.integration.wecom.config.WeComProperties;
import com.jyfc.backend.module.integration.wecom.entity.WeComDepartmentMapping;
import com.jyfc.backend.module.integration.wecom.repository.WeComDepartmentMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 企业微信通讯录同步服务
 *
 * 企业微信的通讯录管理使用独立的"通讯录同步"接口，
 * 需要配置 contactSecret（不同于应用 Secret）。
 *
 * 主要功能：
 * - 同步部门列表（递归）
 * - 同步部门成员
 * - 增删改用户
 * - 全量覆盖
 *
 * 注意：通讯录同步 API 有频率限制，建议定时任务执行。
 *
 * 安全提示：企业微信 API 要求 access_token 作为 URL 查询参数传递（不支持 Header 方式）。
 * access_token 可能被代理/网关日志记录，请确保 HTTP 客户端日志不记录完整 URL。
 * 切勿在应用日志中输出 access_token 或包含 token 的完整 URL。
 */
@Service
public class WeComContactService {

    private static final Logger log = LoggerFactory.getLogger(WeComContactService.class);

    private final WeComProperties weComProperties;
    private final WeComTokenService tokenService;
    private final WebClient.Builder webClientBuilder;
    private final WeComDepartmentMappingRepository departmentMappingRepository;

    public WeComContactService(WeComProperties weComProperties,
                               WeComTokenService tokenService,
                               WebClient.Builder webClientBuilder,
                               WeComDepartmentMappingRepository departmentMappingRepository) {
        this.weComProperties = weComProperties;
        this.tokenService = tokenService;
        this.webClientBuilder = webClientBuilder;
        this.departmentMappingRepository = departmentMappingRepository;
    }

    // ==================== 部门同步 ====================

    /**
     * 同步部门列表（递归获取所有子部门）
     *
     * @return 同步的部门列表
     */
    @Transactional
    public List<Map<String, Object>> syncDepartmentList() {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);

        // 调用获取部门列表 API（id=1 表示根部门）
        List<Map<String, Object>> allDepartments = fetchDepartmentsRecursive(accessToken, 1L);

        // 保存到数据库
        for (Map<String, Object> dept : allDepartments) {
            Long deptId = Long.valueOf(dept.get("id").toString());
            String name = (String) dept.get("name");
            Long parentId = dept.get("parentid") != null
                    ? Long.valueOf(dept.get("parentid").toString())
                    : null;
            Integer order = dept.get("order") != null
                    ? Integer.valueOf(dept.get("order").toString())
                    : 0;

            Optional<WeComDepartmentMapping> existing = departmentMappingRepository.findByWecomDeptId(deptId);
            if (existing.isPresent()) {
                WeComDepartmentMapping mapping = existing.get();
                mapping.setWecomDeptName(name);
                mapping.setParentId(parentId);
                mapping.setOrderNum(order);
                departmentMappingRepository.save(mapping);
            } else {
                WeComDepartmentMapping mapping = new WeComDepartmentMapping();
                mapping.setWecomDeptId(deptId);
                mapping.setWecomDeptName(name);
                mapping.setParentId(parentId);
                mapping.setOrderNum(order);
                departmentMappingRepository.save(mapping);
            }
        }

        log.info("企业微信部门同步完成, 共 {} 个部门", allDepartments.size());
        return allDepartments;
    }

    /**
     * 递归获取所有子部门
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchDepartmentsRecursive(String accessToken, Long deptId) {
        List<Map<String, Object>> result = new ArrayList<>();
        String url = weComProperties.getBaseUrl() + "/department/list"
                + "?access_token=" + accessToken
                + "&id=" + deptId;

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            log.warn("获取部门列表返回空响应, deptId={}", deptId);
            return result;
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            log.error("获取部门列表失败: errcode={}, errmsg={}", errcode, errmsg);
            return result;
        }

        List<Map<String, Object>> departments = (List<Map<String, Object>>) response.get("department");
        if (departments == null || departments.isEmpty()) {
            return result;
        }

        result.addAll(departments);

        // 递归获取子部门
        for (Map<String, Object> dept : departments) {
            Long childDeptId = Long.valueOf(dept.get("id").toString());
            // 避免无限递归：跳过根节点（id=1）
            if (!childDeptId.equals(deptId)) {
                result.addAll(fetchDepartmentsRecursive(accessToken, childDeptId));
            }
        }

        return result;
    }

    // ==================== 成员同步 ====================

    /**
     * 同步指定部门的成员详情
     *
     * @param deptId    部门 ID
     * @param fetchChild 是否递归获取子部门成员
     * @return 成员列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> syncDepartmentUsers(Long deptId, boolean fetchChild) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        List<Map<String, Object>> allUsers = new ArrayList<>();
        int cursor = 0;

        // 企业微信分页获取成员
        while (true) {
            String url = weComProperties.getBaseUrl() + "/user/list"
                    + "?access_token=" + accessToken
                    + "&department_id=" + deptId
                    + "&fetch_child=" + (fetchChild ? "1" : "0")
                    + (cursor > 0 ? "&cursor=" + cursor : "");

            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                log.warn("获取部门成员返回空响应, deptId={}", deptId);
                break;
            }

            if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
                Integer errcode = (Integer) response.get("errcode");
                String errmsg = (String) response.getOrDefault("errmsg", "unknown");
                log.error("获取部门成员失败: errcode={}, errmsg={}", errcode, errmsg);
                break;
            }

            List<Map<String, Object>> userlist = (List<Map<String, Object>>) response.get("userlist");
            if (userlist != null && !userlist.isEmpty()) {
                allUsers.addAll(userlist);
            }

            // 检查是否有更多数据
            if (response.containsKey("has_more") && Integer.valueOf(1).equals(response.get("has_more"))) {
                cursor = Integer.parseInt(response.get("next_cursor").toString());
            } else {
                break;
            }
        }

        log.info("部门 {} 成员同步完成, 共 {} 人", deptId, allUsers.size());
        return allUsers;
    }

    // ==================== 用户管理 ====================

    /**
     * 创建用户
     */
    public Map<String, Object> createUser(Map<String, Object> userInfo) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        String url = weComProperties.getBaseUrl() + "/user/create?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(userInfo)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("创建用户返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("创建用户失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("企业微信用户创建成功: {}", userInfo.get("userid"));
        return response;
    }

    /**
     * 更新用户
     */
    public Map<String, Object> updateUser(Map<String, Object> userInfo) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        String url = weComProperties.getBaseUrl() + "/user/update?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(userInfo)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("更新用户返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("更新用户失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("企业微信用户更新成功: {}", userInfo.get("userid"));
        return response;
    }

    /**
     * 删除用户
     */
    public Map<String, Object> deleteUser(String userId) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        String url = weComProperties.getBaseUrl() + "/user/delete?access_token=" + accessToken + "&userid=" + userId;

        Map<String, Object> response = webClientBuilder.build()
                .get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("删除用户返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("删除用户失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("企业微信用户删除成功: {}", userId);
        return response;
    }

    // ==================== 全量覆盖 ====================

    /**
     * 全量覆盖部门成员
     *
     * ⚠️ 注意：该操作会覆盖指定部门下的所有成员。
     * 调用前需要确保 users 列表包含该部门所有应该存在的成员。
     *
     * @param users  成员列表
     * @param deptId 部门 ID
     */
    public Map<String, Object> batchReplaceParty(List<Map<String, Object>> users, Long deptId) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        String url = weComProperties.getBaseUrl() + "/batch/replaceparty?access_token=" + accessToken;

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("partyid", deptId);
        body.put("userlist", users);

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("全量覆盖部门成员返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("全量覆盖部门成员失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("全量覆盖部门 {} 成员完成, 共 {} 人", deptId, users.size());
        return response;
    }

    /**
     * 全量覆盖成员
     *
     * ⚠️ 注意：该操作会覆盖整个企业的成员数据，请谨慎使用。
     *
     * @param users 全量成员列表
     */
    public Map<String, Object> batchReplaceUser(List<Map<String, Object>> users) {
        String accessToken = tokenService.getAccessToken(WeComTokenService.SecretType.CONTACT);
        String url = weComProperties.getBaseUrl() + "/batch/replaceuser?access_token=" + accessToken;

        Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(url)
                .bodyValue(Map.of("userlist", users != null ? users : List.of()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        if (response == null) {
            throw new RuntimeException("全量覆盖成员返回空响应");
        }

        if (response.containsKey("errcode") && !response.get("errcode").equals(0)) {
            Integer errcode = (Integer) response.get("errcode");
            String errmsg = (String) response.getOrDefault("errmsg", "unknown");
            throw new RuntimeException("全量覆盖成员失败: errcode=" + errcode + ", errmsg=" + errmsg);
        }

        log.info("全量覆盖成员完成, 共 {} 人", users.size());
        return response;
    }
}
