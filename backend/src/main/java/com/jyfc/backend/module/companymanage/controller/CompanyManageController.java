package com.jyfc.backend.module.companymanage.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.auth.entity.CompanyEntity;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.shared.dto.ApiResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 企业管理只读切片（复用现有 companies/users/departments 表，不新增仓库）。
 * 直连 EntityManager（同 MootController 风格）：公司/成员走 JPQL（实体已映射 tenant_id），
 * 部门表无 tenant_id，用 native 查询 JOIN companies 以 tenant_id 兜底防跨租户。
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyManageController {

    @PersistenceContext
    private EntityManager em;

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    @GetMapping("")
    public ApiResponse<List<CompanyEntity>> list() {
        return ApiResponse.success(em.createQuery(
                "SELECT c FROM CompanyEntity c WHERE c.tenantId = :t ORDER BY c.id DESC", CompanyEntity.class)
                .setParameter("t", tenant()).getResultList());
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<UserEntity>> members(@PathVariable Long id) {
        requireCompanyInTenant(id);
        return ApiResponse.success(em.createQuery(
                "SELECT u FROM UserEntity u WHERE u.companyId = :cid AND u.tenantId = :t ORDER BY u.id", UserEntity.class)
                .setParameter("cid", id).setParameter("t", tenant()).getResultList());
    }

    @GetMapping("/{id}/departments")
    public ApiResponse<List<Map<String, Object>>> departments(@PathVariable Long id) {
        requireCompanyInTenant(id);
        List<Object[]> rows = em.createNativeQuery(
                "SELECT d.id, d.company_id, d.parent_id, d.name, d.code, d.leader_user_id, d.sort_order, d.status " +
                "FROM departments d JOIN companies c ON d.company_id = c.id " +
                "WHERE d.company_id = :cid AND c.tenant_id = :t ORDER BY d.sort_order, d.id")
                .setParameter("cid", id).setParameter("t", tenant()).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("companyId", r[1]);
            m.put("parentId", r[2]);
            m.put("name", r[3]);
            m.put("code", r[4]);
            m.put("leaderUserId", r[5]);
            m.put("sortOrder", r[6]);
            m.put("status", r[7]);
            out.add(m);
        }
        return ApiResponse.success(out);
    }

    /**
     * 绑定/更新企业 e签宝机构号（机构实名后回填），用于企业章签署（signers[].orgSignerInfo.orgId）。
     * 传空字符串可解绑（回退为个人签署）。
     */
    @PutMapping("/{id}/esign-org-id")
    public ApiResponse<CompanyEntity> bindEsignOrgId(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CompanyEntity c = requireCompanyInTenant(id);
        Object raw = body.get("esignOrgId");
        String orgId = raw == null ? null : String.valueOf(raw).trim();
        c.setEsignOrgId(orgId == null || orgId.isBlank() ? null : orgId);
        em.merge(c);
        return ApiResponse.success(c);
    }

    /** 跨租户防护：目标公司必须属于当前租户。 */
    private CompanyEntity requireCompanyInTenant(Long id) {
        CompanyEntity c = em.find(CompanyEntity.class, id);
        if (c == null || c.getTenantId() == null || !c.getTenantId().equals(tenant())) {
            throw new BusinessException("公司不存在或跨租户");
        }
        return c;
    }
}
