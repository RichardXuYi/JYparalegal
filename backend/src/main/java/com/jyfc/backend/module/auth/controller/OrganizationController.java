package com.jyfc.backend.module.auth.controller;

import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.module.auth.dto.OrganizationMemberDTO;
import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.DepartmentEntity;
import com.jyfc.backend.module.auth.entity.PositionEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.service.CompanyService;
import com.jyfc.backend.module.auth.service.DepartmentService;
import com.jyfc.backend.module.auth.service.PositionService;
import com.jyfc.backend.module.auth.service.UserService;
import com.jyfc.backend.module.tenant.service.TenantProvisioningService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 企业组织管理控制器
 * 负责企业管理、部门管理、职位管理
 *
 * <p>权限策略：所有接口必须登录；写操作要求当前用户是公司 owner 或被分配到该公司；
 * 管理员 (AdminEntity) 拥有全部访问权限。userId / ownerUserId 一律从 SecurityContext 解析，
 * 不再信任前端传入的字段（仅作为兼容字段做一致性校验）。</p>
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    private static final Logger log = LoggerFactory.getLogger(OrganizationController.class);

    private final CompanyService companyService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final UserService userService;
    private final UserContextUtil userContextUtil;
    private final TenantProvisioningService tenantProvisioningService;

    public OrganizationController(
            CompanyService companyService,
            DepartmentService departmentService,
            PositionService positionService,
            UserService userService,
            UserContextUtil userContextUtil,
            TenantProvisioningService tenantProvisioningService) {
        this.companyService = companyService;
        this.departmentService = departmentService;
        this.positionService = positionService;
        this.userService = userService;
        this.userContextUtil = userContextUtil;
        this.tenantProvisioningService = tenantProvisioningService;
    }

    // ==================== 权限辅助 ====================

    /**
     * 当前用户是否对指定 companyId 有访问权限。
     * 规则：管理员直接放行；普通用户必须是该公司的 owner 或被分配（user.companyId == companyId）。
     * 无 company_members 关联表，采用最简校验：取当前用户作为 owner 的所有公司，若 companyId 不在其中，
     * 再回退检查 user.companyId。
     */
    private boolean hasCompanyAccess(Long currentUserId, Long companyId) {
        if (currentUserId == null || companyId == null) return false;
        if (userContextUtil.isAdmin()) return true;
        List<CompanyEntity> owned = companyService.findByUserId(currentUserId);
        for (CompanyEntity c : owned) {
            if (c.getId() != null && c.getId().equals(companyId)) return true;
        }
        Optional<UserEntity> userOpt = userService.findById(currentUserId);
        if (userOpt.isPresent() && companyId.equals(userOpt.get().getCompanyId())) {
            return true;
        }
        return false;
    }

    /**
     * 高敏组织操作（分配/改部门/删成员）的更严格校验：仅管理员或企业 owner 放行，
     * 普通成员不放行。{@link #hasCompanyAccess} 会把"任意同公司成员"也判为有权，
     * 用于只读访问尚可，但用于"把任意 userId 划进本企业租户"会造成跨租户绑架。
     */
    private boolean isCompanyOwnerOrAdmin(Long currentUserId, Long companyId) {
        if (currentUserId == null || companyId == null) return false;
        if (userContextUtil.isAdmin()) return true;
        for (CompanyEntity c : companyService.findByUserId(currentUserId)) {
            if (c.getId() != null && c.getId().equals(companyId)) return true;
        }
        return false;
    }

    /**
     * 拒绝时统一返回 403。泛型为 Object 以便在多种返回类型中复用。
     */
    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<ApiResponse<T>> forbidden(String msg) {
        return (ResponseEntity<ApiResponse<T>>) (ResponseEntity<?>) ResponseEntity
                .status(403)
                .body((ApiResponse<Object>) (ApiResponse<?>) ApiResponse.error(403, msg));
    }

    // ==================== 企业管理 ====================

    /**
     * 创建企业
     * ownerUserId 强制从会话读取，前端可传入作为兼容性校验（必须与当前用户一致）。
     */
    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<CompanyEntity>> createCompany(
            @RequestBody Map<String, Object> body,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (ownerUserId != null && !ownerUserId.equals(currentUserId)) {
                log.warn("越权创建企业: currentUserId={}, requestedOwnerUserId={}", currentUserId, ownerUserId);
                throw new AccessDeniedException("无权以该用户身份创建企业");
            }
            log.info("创建企业: ownerUserId={}, name={}", currentUserId, body.get("name"));
            CompanyEntity company = new CompanyEntity();
            company.setName((String) body.get("name"));
            company.setUnifiedCreditCode((String) body.get("unifiedCreditCode"));
            company.setLegalPerson((String) body.get("legalPerson"));
            company.setContactPhone((String) body.get("contactPhone"));
            company.setContactEmail((String) body.get("contactEmail"));
            company.setAddress((String) body.get("address"));

            CompanyEntity created = companyService.createCompany(company, currentUserId);
            return ResponseEntity.ok(ApiResponse.success("企业创建成功", created));
        } catch (AccessDeniedException e) {
            log.error("创建企业被拒绝: {}", e.getMessage(), e);
            return ResponseEntity.status(403).body(ApiResponse.error(403, e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("创建企业失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("创建企业异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取当前登录用户的企业。
     * 若前端传入 userId，必须与当前登录用户一致（兼容旧调用），否则返回 403。
     */
    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<CompanyEntity>>> getUserCompanies(
            @RequestParam(value = "userId", required = false) Long userId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (userId != null && !userId.equals(currentUserId)) {
                log.warn("越权查询企业列表: currentUserId={}, requestedUserId={}", currentUserId, userId);
                return ResponseEntity.status(403).body(ApiResponse.error(403, "无权查询该用户的企业"));
            }
            // 管理员可看全部，普通用户只看自己的
            List<CompanyEntity> companies = userContextUtil.isAdmin()
                    ? companyService.findAllActive()
                    : companyService.findByUserId(currentUserId);
            return ResponseEntity.ok(ApiResponse.success(companies));
        } catch (Exception e) {
            log.error("获取企业列表异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取企业详情
     */
    @GetMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyEntity>> getCompany(@PathVariable Long id) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, id)) {
                return forbidden("无权访问该企业");
            }
            return companyService.findById(id)
                    .map(company -> ResponseEntity.ok(ApiResponse.success(company)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("获取企业详情异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 更新企业信息
     */
    @PutMapping("/companies/{id}")
    public ResponseEntity<ApiResponse<CompanyEntity>> updateCompany(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, id)) {
                return forbidden("无权更新该企业");
            }
            log.info("更新企业: id={}", id);
            return companyService.findById(id).map(company -> {
                if (body.get("name") != null) company.setName((String) body.get("name"));
                if (body.get("legalPerson") != null) company.setLegalPerson((String) body.get("legalPerson"));
                if (body.get("contactPhone") != null) company.setContactPhone((String) body.get("contactPhone"));
                if (body.get("contactEmail") != null) company.setContactEmail((String) body.get("contactEmail"));
                if (body.get("address") != null) company.setAddress((String) body.get("address"));

                CompanyEntity updated = companyService.updateCompany(company);
                return ResponseEntity.ok(ApiResponse.success("企业信息更新成功", updated));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("更新企业异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    // ==================== 部门管理 ====================

    /**
     * 创建部门
     */
    @PostMapping("/departments")
    public ResponseEntity<ApiResponse<DepartmentEntity>> createDepartment(
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Object companyIdObj = body.get("companyId");
            if (companyIdObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "companyId is required"));
            }
            Long companyId = Long.parseLong(companyIdObj.toString());
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权在该企业下创建部门");
            }
            log.info("创建部门: companyId={}, name={}", companyId, body.get("name"));
            DepartmentEntity department = new DepartmentEntity();
            department.setName((String) body.get("name"));
            department.setCode((String) body.get("code"));
            department.setParentId(body.get("parentId") != null ?
                Long.parseLong(body.get("parentId").toString()) : null);

            DepartmentEntity created = departmentService.createDepartment(department, companyId);
            return ResponseEntity.ok(ApiResponse.success("部门创建成功", created));
        } catch (IllegalArgumentException e) {
            log.error("创建部门失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("创建部门异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取企业部门列表
     */
    @GetMapping("/departments")
    public ResponseEntity<ApiResponse<List<DepartmentEntity>>> getDepartments(
            @RequestParam("companyId") Long companyId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权访问该企业的部门");
            }
            List<DepartmentEntity> departments = departmentService.findByCompanyId(companyId);
            return ResponseEntity.ok(ApiResponse.success(departments));
        } catch (Exception e) {
            log.error("获取部门列表异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取子部门
     */
    @GetMapping("/departments/{parentId}/children")
    public ResponseEntity<ApiResponse<List<DepartmentEntity>>> getChildDepartments(
            @PathVariable Long parentId,
            @RequestParam("companyId") Long companyId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权访问该企业的部门");
            }
            List<DepartmentEntity> children = departmentService.findChildren(companyId, parentId);
            return ResponseEntity.ok(ApiResponse.success(children));
        } catch (Exception e) {
            log.error("获取子部门异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 更新部门
     */
    @PutMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<DepartmentEntity>> updateDepartment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Optional<DepartmentEntity> deptOpt = departmentService.findById(id);
            if (deptOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!hasCompanyAccess(currentUserId, deptOpt.get().getCompanyId())) {
                return forbidden("无权更新该部门");
            }
            return deptOpt.map(department -> {
                if (body.get("name") != null) department.setName((String) body.get("name"));
                if (body.get("code") != null) department.setCode((String) body.get("code"));
                if (body.get("leaderUserId") != null) {
                    department.setLeaderUserId(Long.parseLong(body.get("leaderUserId").toString()));
                }
                if (body.get("sortOrder") != null) {
                    department.setSortOrder(Integer.parseInt(body.get("sortOrder").toString()));
                }

                DepartmentEntity updated = departmentService.updateDepartment(department);
                return ResponseEntity.ok(ApiResponse.success("部门更新成功", updated));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("更新部门异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/departments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(@PathVariable Long id) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Optional<DepartmentEntity> deptOpt = departmentService.findById(id);
            if (deptOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!hasCompanyAccess(currentUserId, deptOpt.get().getCompanyId())) {
                return forbidden("无权删除该部门");
            }
            log.info("删除部门: id={}", id);
            departmentService.deleteDepartment(id);
            return ResponseEntity.ok(ApiResponse.success("部门已删除", null));
        } catch (Exception e) {
            log.error("删除部门异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    // ==================== 职位管理 ====================

    /**
     * 创建职位
     */
    @PostMapping("/positions")
    public ResponseEntity<ApiResponse<PositionEntity>> createPosition(
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Object companyIdObj = body.get("companyId");
            if (companyIdObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "companyId is required"));
            }
            Long companyId = Long.parseLong(companyIdObj.toString());
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权在该企业下创建职位");
            }
            log.info("创建职位: companyId={}, departmentId={}, name={}", companyId, body.get("departmentId"), body.get("name"));
            PositionEntity position = new PositionEntity();
            position.setName((String) body.get("name"));
            position.setCode((String) body.get("code"));
            position.setLevel(body.get("level") != null ?
                Integer.parseInt(body.get("level").toString()) : 1);
            position.setDescription((String) body.get("description"));

            Long departmentId = body.get("departmentId") != null ?
                Long.parseLong(body.get("departmentId").toString()) : null;
            if (departmentId == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "departmentId is required"));
            }

            PositionEntity created = positionService.createPosition(position, companyId, departmentId);
            return ResponseEntity.ok(ApiResponse.success("职位创建成功", created));
        } catch (IllegalArgumentException e) {
            log.error("创建职位失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("创建职位异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取企业职位列表
     */
    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<PositionEntity>>> getPositions(
            @RequestParam("companyId") Long companyId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权访问该企业的职位");
            }
            List<PositionEntity> positions = positionService.findByCompanyId(companyId);
            return ResponseEntity.ok(ApiResponse.success(positions));
        } catch (Exception e) {
            log.error("获取职位列表异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取部门职位列表
     */
    @GetMapping("/positions/department/{departmentId}")
    public ResponseEntity<ApiResponse<List<PositionEntity>>> getDepartmentPositions(
            @PathVariable Long departmentId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Optional<DepartmentEntity> deptOpt = departmentService.findById(departmentId);
            if (deptOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!hasCompanyAccess(currentUserId, deptOpt.get().getCompanyId())) {
                return forbidden("无权访问该部门的职位");
            }
            List<PositionEntity> positions = positionService.findByDepartmentId(departmentId);
            return ResponseEntity.ok(ApiResponse.success(positions));
        } catch (Exception e) {
            log.error("获取部门职位异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 更新职位
     */
    @PutMapping("/positions/{id}")
    public ResponseEntity<ApiResponse<PositionEntity>> updatePosition(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Optional<PositionEntity> posOpt = positionService.findById(id);
            if (posOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!hasCompanyAccess(currentUserId, posOpt.get().getCompanyId())) {
                return forbidden("无权更新该职位");
            }
            return posOpt.map(position -> {
                if (body.get("name") != null) position.setName((String) body.get("name"));
                if (body.get("code") != null) position.setCode((String) body.get("code"));
                if (body.get("level") != null) {
                    position.setLevel(Integer.parseInt(body.get("level").toString()));
                }
                if (body.get("description") != null) {
                    position.setDescription((String) body.get("description"));
                }

                PositionEntity updated = positionService.updatePosition(position);
                return ResponseEntity.ok(ApiResponse.success("职位更新成功", updated));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("更新职位异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 删除职位
     */
    @DeleteMapping("/positions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePosition(@PathVariable Long id) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Optional<PositionEntity> posOpt = positionService.findById(id);
            if (posOpt.isEmpty()) return ResponseEntity.notFound().build();
            if (!hasCompanyAccess(currentUserId, posOpt.get().getCompanyId())) {
                return forbidden("无权删除该职位");
            }
            log.info("删除职位: id={}", id);
            positionService.deletePosition(id);
            return ResponseEntity.ok(ApiResponse.success("职位已删除", null));
        } catch (Exception e) {
            log.error("删除职位异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    // ==================== 用户企业关联 ====================

    /**
     * 将用户分配到企业（设置部门职位）
     */
    @PostMapping("/users/assign")
    public ResponseEntity<ApiResponse<UserEntity>> assignUserToCompany(
            @RequestBody Map<String, Object> body) {

        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            Object companyIdObj = body.get("companyId");
            if (companyIdObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "companyId is required"));
            }
            Long companyId = Long.parseLong(companyIdObj.toString());
            // 只有该企业的 owner（或平台管理员）才能分配用户；普通成员不放行。
            if (!isCompanyOwnerOrAdmin(currentUserId, companyId)) {
                return forbidden("无权在该企业下分配用户");
            }
            log.info("分配用户到企业: userId={}, companyId={}, departmentId={}, positionId={}",
                    body.get("userId"), companyId, body.get("departmentId"), body.get("positionId"));
            Object userIdObj = body.get("userId");
            Object deptIdObj = body.get("departmentId");
            Object posIdObj = body.get("positionId");
            if (userIdObj == null || deptIdObj == null || posIdObj == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error(400, "userId, departmentId, and positionId are required"));
            }
            Long userId = Long.parseLong(userIdObj.toString());
            Long departmentId = Long.parseLong(deptIdObj.toString());
            Long positionId = Long.parseLong(posIdObj.toString());
            String employeeNo = (String) body.get("employeeNo");

            UserEntity user = userService.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));

            // 拒绝跨租户"绑架"：目标用户若已属于其它非空租户，禁止划入本企业租户。
            Long companyTenantId = companyService.findById(companyId).map(CompanyEntity::getTenantId).orElse(null);
            Long userTenantId = user.getTenantId();
            boolean userBound = userTenantId != null && userTenantId != 0L;
            boolean sameTenant = companyTenantId != null && companyTenantId.equals(userTenantId);
            if (userBound && !sameTenant) {
                log.warn("拒绝跨租户分配用户: targetUserId={}, userTenantId={}, companyId={}, companyTenantId={}",
                        userId, userTenantId, companyId, companyTenantId);
                return forbidden("目标用户已属于其它租户，禁止跨租户划入");
            }

            user.setUserType("ENTERPRISE");
            user.setCompanyId(companyId);
            user.setDepartmentId(departmentId);
            user.setPositionId(positionId);
            if (employeeNo != null) {
            }

            // 运行时租户供给：成员挂到该企业的 ENTERPRISE 租户（幂等）
            tenantProvisioningService.bindUserToCompany(user, companyId);

            UserEntity updated = userService.updateUser(user);
            return ResponseEntity.ok(ApiResponse.success("用户已分配到企业", updated));
        } catch (IllegalArgumentException e) {
            log.error("分配用户到企业失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("分配用户到企业异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    // ==================== 组织成员 ====================

    /**
     * 获取企业成员列表
     *
     * <p>成员组成：企业 owner（创建者）+ 已分配到该企业的用户（users.company_id == companyId）。
     * 当前数据模型未引入 company_members 关联表，采用 user.companyId 反查 + owner 合并的方案。</p>
     *
     * @param companyId 企业 ID（query）
     * @return OrganizationMemberDTO 列表，包含 userId / username / realName / role / positionName / departmentName / joinedAt
     */
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<OrganizationMemberDTO>>> getMembers(
            @RequestParam("companyId") Long companyId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权访问该企业的成员");
            }

            Optional<CompanyEntity> companyOpt = companyService.findById(companyId);
            if (companyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            CompanyEntity company = companyOpt.get();

            List<OrganizationMemberDTO> members = new ArrayList<>();

            // 1) 企业 owner（可能 owner 未被显式分配到 users.company_id）
            if (company.getOwnerUserId() != null) {
                Optional<UserEntity> ownerOpt = userService.findById(company.getOwnerUserId());
                ownerOpt.ifPresent(owner -> {
                    OrganizationMemberDTO dto = new OrganizationMemberDTO();
                    dto.setUserId(owner.getId());
                    dto.setUsername(owner.getUsername());
                    dto.setRealName(owner.getUsername()); // 数据模型未存 realName，回退 username
                    dto.setRole("OWNER");
                    dto.setJoinedAt(company.getCreatedAt());
                    members.add(dto);
                });
            }

            // 2) 已分配到该企业的成员（基于 users.company_id 反查）
            List<UserEntity> assigned = userService.findByCompanyId(companyId);
            for (UserEntity u : assigned) {
                // 去重：若 owner 也在该列表中，跳过 owner 的重复条目
                if (u.getId() != null && u.getId().equals(company.getOwnerUserId())) {
                    continue;
                }
                OrganizationMemberDTO dto = new OrganizationMemberDTO();
                dto.setUserId(u.getId());
                dto.setUsername(u.getUsername());
                dto.setRealName(u.getUsername()); // 数据模型未存 realName，回退 username
                dto.setRole("MEMBER");
                dto.setJoinedAt(u.getCreatedAt());

                // 关联部门名称
                if (u.getDepartmentId() != null) {
                    departmentService.findById(u.getDepartmentId())
                            .ifPresent(dept -> dto.setDepartmentName(dept.getName()));
                }
                // 关联职位名称
                if (u.getPositionId() != null) {
                    positionService.findById(u.getPositionId())
                            .ifPresent(pos -> dto.setPositionName(pos.getName()));
                }
                members.add(dto);
            }

            log.info("获取企业成员列表: companyId={}, size={}", companyId, members.size());
            return ResponseEntity.ok(ApiResponse.success(members));
        } catch (Exception e) {
            log.error("获取企业成员列表异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }

    /**
     * 获取企业成员数（用于前端卡片快速展示，避免拉取完整列表）。
     *
     * @param companyId 企业 ID（query）
     * @return { "count": number }，count 包含 owner + 分配的成员
     */
    @GetMapping("/members/count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberCount(
            @RequestParam("companyId") Long companyId) {
        try {
            Long currentUserId = userContextUtil.getCurrentUserId();
            if (!hasCompanyAccess(currentUserId, companyId)) {
                return forbidden("无权访问该企业的成员数");
            }

            Optional<CompanyEntity> companyOpt = companyService.findById(companyId);
            if (companyOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            CompanyEntity company = companyOpt.get();

            long assigned = userService.countByCompanyId(companyId);
            // 成员数 = owner（1 个）+ 已分配用户；若 owner 同时也在 users.company_id 列表里需去重
            long count;
            if (company.getOwnerUserId() != null) {
                Optional<UserEntity> ownerOpt = userService.findById(company.getOwnerUserId());
                boolean ownerAlreadyCounted = ownerOpt
                        .map(o -> o.getCompanyId() != null && o.getCompanyId().equals(companyId))
                        .orElse(false);
                count = ownerAlreadyCounted ? assigned : assigned + 1;
            } else {
                count = assigned;
            }

            Map<String, Object> data = Map.of("count", count);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (Exception e) {
            log.error("获取企业成员数异常: {}", e.getMessage(), e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "未登录或会话已失效"));
        }
    }
}
