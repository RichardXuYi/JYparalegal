package com.jyfc.backend.module.template.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.template.entity.ContractTemplateEntity;
import com.jyfc.backend.module.template.repository.ContractTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同模板服务（V140）。list/create/get 走租户隔离仓库；render 把 body 内 {{varName}} 占位符
 * 用入参 Map 替换，回渲染文本 + 未填变量清单（声明变量与正文占位符取并集，去重保序）。
 */
@Service
public class ContractTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}}");

    private final ContractTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public ContractTemplateService(ContractTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<ContractTemplateEntity> list(Long tenantId) {
        return repository.findAllByTenantIdOrderByIdDesc(tenantId);
    }

    public ContractTemplateEntity get(Long tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("模板不存在或跨租户"));
    }

    public ContractTemplateEntity create(Long tenantId, Long createdBy, String title,
                                         String category, String body, List<String> variables) {
        if (title == null || title.isBlank()) throw new BusinessException("模板标题不能为空");
        ContractTemplateEntity e = new ContractTemplateEntity();
        e.setTenantId(tenantId);
        e.setCreatedBy(createdBy);
        e.setTitle(title);
        e.setCategory(category);
        e.setBody(body);
        e.setStatus("ACTIVE");
        e.setVariables(writeVariables(variables));
        return repository.save(e);
    }

    /** 渲染：替换 body 中 {{varName}}，回 rendered + unfilled（缺值的声明变量与正文占位符）。 */
    public Map<String, Object> render(Long tenantId, Long id, Map<String, Object> values) {
        ContractTemplateEntity tpl = get(tenantId, id);
        Map<String, Object> vars = values == null ? Map.of() : values;

        Set<String> declared = new LinkedHashSet<>(readVariables(tpl.getVariables()));
        Set<String> inBody = new LinkedHashSet<>();
        String body = tpl.getBody() == null ? "" : tpl.getBody();
        Matcher scan = PLACEHOLDER.matcher(body);
        while (scan.find()) inBody.add(scan.group(1));

        Set<String> all = new LinkedHashSet<>(declared);
        all.addAll(inBody);

        StringBuffer sb = new StringBuffer();
        Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) {
            String key = m.group(1);
            Object val = vars.get(key);
            String replacement = val == null ? m.group(0) : String.valueOf(val);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        List<String> unfilled = new ArrayList<>();
        for (String key : all) {
            Object val = vars.get(key);
            if (val == null || String.valueOf(val).isBlank()) unfilled.add(key);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rendered", sb.toString());
        out.put("unfilled", unfilled);
        return out;
    }

    private String writeVariables(List<String> variables) {
        if (variables == null || variables.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            throw new BusinessException("变量清单序列化失败");
        }
    }

    private List<String> readVariables(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
