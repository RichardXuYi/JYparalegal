package com.jyfc.backend.core.exception;

import com.jyfc.backend.shared.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * F3 修复：异常信息不再直接泄露给客户端。
 * - BusinessException / IllegalArgumentException：使用 safeMessage（业务层可控 -> 透传）；
 *   其它 framework 异常：返回固定友好提示，详情只写日志。
 * - catch-all `handleAny` 永远返回 "系统繁忙，请稍后重试"。
 * - 统一使用 ApiResponse.error() 格式返回错误，与控制器保持一致。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    /** 未捕获异常对外固定消息。 */
    private static final String SAFE_UNCAUGHT_MSG = "系统繁忙，请稍后重试";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, WebRequest req) {
        // 业务异常：按规范返回 400 + 业务层 userMessage（不视为内部泄露）。
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ArchiveUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleArchiveDown(ArchiveUnavailableException ex, WebRequest req) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(RequiresPurchaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequiresPurchase(RequiresPurchaseException ex, WebRequest req) {
        // 套餐未解锁：HTTP 402 + code 402，前端据此弹「请购买」。
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, WebRequest req) {
        // IllegalArgumentException 通常来自业务校验，透传可读，但仍然避免直接吐异常 detail 字段
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception ex, WebRequest req) {
        return build(HttpStatus.FORBIDDEN, "没有权限做这个操作。");
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(Exception ex, WebRequest req) {
        // 验证错误不暴露 ex.getMessage()（可能含字段细节 -> 信息泄露/枚举攻击）
        return build(HttpStatus.BAD_REQUEST, "填写的内容没通过检查，请修改后再试。");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(Exception ex, WebRequest req) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小超过限制，最大支持 50MB");
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMedia(Exception ex, WebRequest req) {
        // 不暴露 ex.getMessage()（可能含 server-internal header 细节）
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的媒体类型");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest req) {
        // 不暴露 ex.getCause().getMessage()（可能含 JSON 路径/类名/字段结构 -> 信息泄露）
        log.warn("Request body parse failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "提交的内容不完整，请检查后再试。");
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex, WebRequest req) {
        // 缺少参数：返回参数名（不视为敏感）
        log.warn("Missing parameter: {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "还有必填项没填。");
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex, WebRequest req) {
        // 类型不匹配：仅返回字段名，不暴露 ex.getMessage()（可能含完整 type path）
        log.warn("Parameter type mismatch: {}", ex.getName());
        return build(HttpStatus.BAD_REQUEST, "填写的内容格式不对，请修改后再试。");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(Exception ex, WebRequest req) {
        // 不暴露 ex.getMessage()（含 supported methods 列表，通常无害但保持一致策略）
        return build(HttpStatus.METHOD_NOT_ALLOWED, "这个操作现在不能用。");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, WebRequest req) {
        // 资源路径通常属于公开信息，可以透传；但仍然收敛为通用 404
        log.warn("Resource not found: {}", ex.getResourcePath());
        return build(HttpStatus.NOT_FOUND, "这个功能暂时打不开，请稍后再试。");
    }

    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<ApiResponse<Void>> handleIO(Exception ex, WebRequest req) {
        // F3 修复：不暴露 ex.getMessage()（可能含文件系统路径/原因）
        log.error("IO error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "读写文件失败，请稍后再试。");
    }

    @ExceptionHandler(java.sql.SQLException.class)
    public ResponseEntity<ApiResponse<Void>> handleSQL(Exception ex, WebRequest req) {
        // F3 修复：SQL 异常永远不要透传详情（可能含表名/SQL片段/敏感字段名）
        log.error("SQL error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "数据暂时读不出来，请稍后再试。");
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(Exception ex, WebRequest req) {
        // F3 修复：同上
        log.error("Data access error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "数据暂时读不出来，请稍后再试。");
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<Void>> handleAny(Throwable ex, WebRequest req) {
        // F3 修复：未捕获异常永远只返回固定消息，详情只写 log
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, SAFE_UNCAUGHT_MSG);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
