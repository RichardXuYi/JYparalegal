package com.jyfc.backend.module.moot.controller;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.moot.service.MootService;
import com.jyfc.backend.shared.dto.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/moot")
@PreAuthorize("hasAuthority('ROLE_USER')")
public class MootController {

    private final MootService moot;

    public MootController(MootService moot) {
        this.moot = moot;
    }

    @GetMapping("/archive")
    public ApiResponse<Map<String, Object>> archiveStatus() {
        moot.folder();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", true);
        return ApiResponse.success(status);
    }

    @GetMapping("/cases")
    public ApiResponse<List<Map<String, Object>>> cases() {
        return ApiResponse.success(moot.listCases());
    }

    @PostMapping("/cases")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long signTaskId = body.get("signTaskId") instanceof Number n ? n.longValue() : null;
        return ApiResponse.success(moot.createCase(signTaskId, str(body.get("stance")), str(body.get("summary"))));
    }

    @PostMapping(value = "/cases/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new BusinessException("文件没有读出来");
        }
        return ApiResponse.success(moot.upload(id, file.getOriginalFilename(), bytes));
    }

    @GetMapping("/cases/{id}/files")
    public ApiResponse<List<Map<String, Object>>> files(@PathVariable long id) {
        return ApiResponse.success(moot.listFiles(id));
    }

    @GetMapping("/cases/{id}/files/{fileId}/content")
    public ApiResponse<Map<String, Object>> preview(@PathVariable long id, @PathVariable long fileId,
                                                     @RequestParam(defaultValue = "false") boolean raw) {
        return ApiResponse.success(moot.filePreview(id, fileId, raw));
    }

    @GetMapping("/cases/{id}/search")
    public ApiResponse<List<Map<String, Object>>> search(@PathVariable long id, @RequestParam String q) {
        return ApiResponse.success(moot.search(id, q));
    }

    @PostMapping("/cases/{id}/hearings")
    public ApiResponse<Map<String, Object>> open(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> humans = body != null && body.get("humanRoles") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return ApiResponse.success(moot.openHearing(id, humans));
    }

    @PutMapping("/hearings/{id}/sessions")
    public ApiResponse<Map<String, Object>> sessions(@PathVariable long id, @RequestBody Map<String, String> body) {
        moot.saveSessions(id, body);
        return ApiResponse.success(moot.hearing(id));
    }

    @GetMapping("/hearings/{id}")
    public ApiResponse<Map<String, Object>> hearing(@PathVariable long id) {
        return ApiResponse.success(moot.hearing(id));
    }

    @PostMapping("/hearings/{id}/turns/prepare")
    public ApiResponse<Map<String, Object>> prepare(@PathVariable long id, @RequestParam(defaultValue = "false") boolean sessionLost) {
        return ApiResponse.success(moot.prepare(id, sessionLost));
    }

    @PostMapping("/hearings/{id}/turns/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long slotId = body.get("slotId") instanceof Number n ? n.longValue() : 0L;
        return ApiResponse.success(moot.commit(id, slotId, str(body.get("body")), str(body.get("speaker"))));
    }

    @PostMapping("/hearings/{id}/turns/skip")
    public ApiResponse<Map<String, Object>> skip(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long slotId = body.get("slotId") instanceof Number n ? n.longValue() : 0L;
        return ApiResponse.success(moot.skip(id, slotId));
    }

    @PostMapping("/hearings/{id}/turns/interject")
    public ApiResponse<Map<String, Object>> interject(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(moot.interject(id, str(body.get("body"))));
    }

    @PostMapping("/hearings/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable long id) {
        return ApiResponse.success(moot.close(id));
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
