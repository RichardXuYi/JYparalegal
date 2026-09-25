package com.jyfc.backend.module.compare.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.compare.service.CompareService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 合同比对 REST（compare 切片，挂 /api/review 审查域）。POST /compare 接收 {textA, textB}，
 * 返回句级 LCS diff（segments + counts）。租户应用层强制（D12）：tenant() 守卫，缺失即拒。
 */
@RestController
@RequestMapping("/api/review")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    private Long tenant() {
        Long t = JyTenantContext.get();
        if (t == null || t == 0L) throw new BusinessException("租户上下文缺失");
        return t;
    }

    @PostMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestBody Map<String, String> body) {
        tenant();
        return ApiResponse.success(compareService.diff(body.get("textA"), body.get("textB")));
    }
}
