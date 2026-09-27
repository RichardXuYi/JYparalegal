package com.jyfc.backend.module.moot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyfc.backend.core.exception.ArchiveUnavailableException;
import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.core.security.UserContextUtil;
import com.jyfc.backend.core.tenant.JyTenantContext;
import com.jyfc.backend.module.moot.config.ArchiveProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 档案和庭审记录都放在本机目录里，不连公司数据库。 */
@Service
public class MootService {

    private static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final Pattern CLAUSE = Pattern.compile("第\\s*\\d+\\s*条");
    private static final Pattern MONEY = Pattern.compile("(?:￥|¥)\\s*\\d+(?:,\\d{3})*(?:\\.\\d+)?|\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:万元|元)");
    private static final Pattern DATE = Pattern.compile("\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日|\\d{4}-\\d{2}-\\d{2}|\\d{4}/\\d{1,2}/\\d{1,2}");
    private static final Set<String> ALLOWED = Set.of(
            "doc", "docx", "wps", "pdf", "xls", "xlsx", "jpg", "jpeg", "bmp", "png", "rtf", "txt", "md", "csv");
    private static final String[][] SLOTS = {
            {"STATEMENT", "原告陈述诉请与事实"},
            {"STATEMENT", "被告答辩"},
            {"QUESTION", "审判长询问"},
            {"STATEMENT", "我方回应审判长"},
            {"WRAP", "书记员收束"}
    };
    private static final Map<String, String> AGENTS = Map.of(
            "JUDGE", "moot-judge",
            "JUROR_A", "moot-juror-a",
            "JUROR_B", "moot-juror-b",
            "OURS", "moot-ours",
            "OPPONENT", "moot-opponent",
            "CLERK", "moot-clerk");

    private final ObjectMapper json = new ObjectMapper();
    private final ArchiveProperties props;
    private final UserContextUtil users;

    public MootService(ArchiveProperties props, UserContextUtil users) {
        this.props = props;
        this.users = users;
    }

    public String folder() {
        return root().toString();
    }

    public synchronized List<Map<String, Object>> listCases() {
        List<Map<String, Object>> all = new ArrayList<>();
        Path dir = tenantDir();
        if (!Files.isDirectory(dir)) return all;
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                Map<String, Object> row = readMap(path.resolve("case.json"));
                if (!row.isEmpty()) all.add(publicCase(row));
            });
        } catch (Exception ex) {
            throw new BusinessException("档案目录读不出来");
        }
        all.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("id"))).reversed());
        return all;
    }

    public synchronized Map<String, Object> createCase(Long signTaskId, String stance, String summary) {
        if (!"PLAINTIFF".equals(stance) && !"DEFENDANT".equals(stance)) {
            throw new BusinessException("立场只能是原告或被告");
        }
        String text = summary == null ? "" : summary.trim();
        if (text.isEmpty() || text.length() > 1500) throw new BusinessException("案情摘要需要 1 到 1500 字");
        long id = nextId();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("signTaskId", signTaskId);
        row.put("stance", stance);
        row.put("summary", text);
        row.put("status", "DRAFT");
        row.put("createdBy", users.getCurrentUserId());
        row.put("createdAt", LocalDateTime.now().toString());
        write(caseDir(id).resolve("case.json"), row);
        write(caseDir(id).resolve("files.json"), new ArrayList<>());
        return publicCase(row);
    }

    public synchronized Map<String, Object> upload(long caseId, String fileName, byte[] bytes) {
        Map<String, Object> caseRow = requireCase(caseId);
        if (fileName == null || fileName.isBlank()) throw new BusinessException("文件名不能为空");
        String ext = ext(fileName);
        if (!ALLOWED.contains(ext)) throw new BusinessException("不支持的文件类型");
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new BusinessException("文件不能为空，且不能超过 50MB");
        String safe = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        long fileId = nextId();
        String relative = tenant() + "/" + caseId + "/files/" + fileId + "-" + safe;
        Path target = root().resolve(relative).normalize();
        if (!target.startsWith(root())) throw new BusinessException("非法路径");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (Exception ex) {
            throw new BusinessException("文件没有写入档案目录");
        }
        ArchiveText.ExtractResult extracted = ArchiveText.extract(fileName, bytes);
        String status = extracted.error() != null ? "FAILED" : (extracted.text().isBlank() ? "NO_TEXT" : "PARSED");
        List<String> parts = chunks(extracted.text());
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("id", fileId);
        file.put("fileName", safe);
        file.put("relativePath", relative.replace('\\', '/'));
        file.put("parseStatus", status);
        file.put("parseError", extracted.error());
        file.put("sha256", sha256(bytes));
        file.put("sizeBytes", bytes.length);
        file.put("chunks", parts);
        List<Map<String, Object>> all = files(caseId);
        all.add(file);
        write(caseDir(caseId).resolve("files.json"), all);
        if ("DRAFT".equals(caseRow.get("status")) && "PARSED".equals(status)) {
            caseRow.put("status", "READY");
            write(caseDir(caseId).resolve("case.json"), caseRow);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", fileId);
        out.put("fileName", safe);
        out.put("parseStatus", status);
        out.put("chunks", parts.size());
        out.put("sha256", file.get("sha256"));
        return out;
    }

    public synchronized List<Map<String, Object>> listFiles(long caseId) {
        requireCase(caseId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> file : files(caseId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", file.get("id"));
            row.put("fileName", file.get("fileName"));
            row.put("parseStatus", file.get("parseStatus"));
            row.put("parseError", file.get("parseError"));
            row.put("sha256", file.get("sha256"));
            row.put("sizeBytes", file.get("sizeBytes"));
            row.put("chunks", chunkList(file).size());
            out.add(row);
        }
        return out;
    }

    public synchronized Map<String, Object> filePreview(long caseId, long fileId, boolean raw) {
        Map<String, Object> file = requireFile(caseId, fileId);
        byte[] bytes = readBytes(String.valueOf(file.get("relativePath")));
        ArchiveText.ExtractResult extracted = ArchiveText.extract(String.valueOf(file.get("fileName")), bytes);
        String storedError = file.get("parseError") == null ? "" : String.valueOf(file.get("parseError"));
        String text;
        if ("FAILED".equals(file.get("parseStatus")) || extracted.error() != null) {
            text = !storedError.isBlank() ? storedError : (extracted.error() == null ? "解析失败" : extracted.error());
        } else if (extracted.text().isBlank()) {
            text = "此文件没有文字层，只能查看原件，不能被检索。";
        } else {
            text = extracted.text();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fileName", file.get("fileName"));
        row.put("parseStatus", file.get("parseStatus"));
        row.put("sha256", file.get("sha256"));
        row.put("text", text);
        if (raw) row.put("contentBase64", Base64.getEncoder().encodeToString(bytes));
        return row;
    }

    public synchronized List<Map<String, Object>> search(long caseId, String q) {
        requireCase(caseId);
        return hits(caseId, q == null ? "" : q);
    }

    public synchronized Map<String, Object> openHearing(long caseId, List<String> humanRoles) {
        Map<String, Object> caseRow = requireCase(caseId);
        if ("HEARING".equals(caseRow.get("status")) && caseRow.get("hearingId") != null) {
            try {
                Map<String, Object> existing = requireHearing(num(caseRow.get("hearingId")));
                if ("OPEN".equals(existing.get("status"))) return view(existing);
            } catch (BusinessException ignored) {
                // 编号还在，但庭审文件已经不在，下面另开一场。
            }
        }
        boolean parsed = files(caseId).stream().anyMatch(file -> "PARSED".equals(file.get("parseStatus")));
        if (!parsed) throw new BusinessException("没有可检索的文字，不能开庭");
        long hearingId = nextId();
        Map<String, Object> hearing = new LinkedHashMap<>();
        hearing.put("id", hearingId);
        hearing.put("case_id", caseId);
        hearing.put("status", "OPEN");
        hearing.put("human_roles", humanRoles != null && humanRoles.contains("OURS") ? "OURS" : "");
        hearing.put("started_at", LocalDateTime.now().toString());
        hearing.put("closed_at", null);
        List<Map<String, Object>> roles = new ArrayList<>();
        for (var entry : AGENTS.entrySet()) {
            Map<String, Object> role = new LinkedHashMap<>();
            role.put("role_code", entry.getKey());
            role.put("agent_id", entry.getValue());
            role.put("session_key", null);
            roles.add(role);
        }
        hearing.put("roles", roles);
        String plaintiff = "PLAINTIFF".equals(caseRow.get("stance")) ? "OURS" : "OPPONENT";
        String defendant = "PLAINTIFF".equals(caseRow.get("stance")) ? "OPPONENT" : "OURS";
        String[] roleOrder = {plaintiff, defendant, "JUDGE", "OURS", "CLERK"};
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < SLOTS.length; i++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("id", nextId());
            slot.put("seq", i + 1);
            slot.put("phase", SLOTS[i][0]);
            slot.put("role_code", roleOrder[i]);
            slot.put("status", "PENDING");
            slot.put("plan_text", SLOTS[i][1]);
            slots.add(slot);
        }
        hearing.put("slots", slots);
        hearing.put("utterances", new ArrayList<>());
        hearing.put("retrievals", new ArrayList<>());
        write(caseDir(caseId).resolve("hearing-" + hearingId + ".json"), hearing);
        caseRow.put("status", "HEARING");
        caseRow.put("hearingId", hearingId);
        write(caseDir(caseId).resolve("case.json"), caseRow);
        return view(hearing);
    }

    public synchronized void saveSessions(long hearingId, Map<String, String> sessions) {
        Map<String, Object> hearing = requireHearing(hearingId);
        for (Map<String, Object> role : listOf(hearing.get("roles"))) {
            String key = sessions.get(String.valueOf(role.get("role_code")));
            if (key != null) role.put("session_key", key);
        }
        saveHearing(hearing);
    }

    public synchronized Map<String, Object> hearing(long hearingId) {
        return view(requireHearing(hearingId));
    }

    public synchronized Map<String, Object> prepare(long hearingId, boolean sessionLost) {
        Map<String, Object> hearing = requireHearing(hearingId);
        if (!"OPEN".equals(hearing.get("status"))) throw new BusinessException("这场庭审已经结束");
        Map<String, Object> slot = currentSlot(hearing);
        if (slot == null) throw new BusinessException("五个槽已经结束");
        if (!"SPEAKING".equals(slot.get("status"))) slot.put("status", "SPEAKING");
        saveHearing(hearing);
        String role = String.valueOf(slot.get("role_code"));
        String humans = String.valueOf(hearing.get("human_roles"));
        boolean human = ("OURS".equals(humans) && "OURS".equals(role))
                || ("4".equals(humans) && num(slot.get("seq")) == 4);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("slotId", slot.get("id"));
        result.put("role", role);
        result.put("plan", slot.get("plan_text"));
        if (human) {
            result.put("awaitHuman", true);
            return result;
        }
        long caseId = num(hearing.get("case_id"));
        List<Map<String, Object>> snips = retrievalsFor(hearing, num(slot.get("id")));
        if (snips.isEmpty()) {
            snips = hits(caseId, slot.get("plan_text") + " " + tail(lastUtterance(hearing)));
            for (Map<String, Object> hit : snips) {
                Map<String, Object> saved = new LinkedHashMap<>(hit);
                saved.put("slotId", slot.get("id"));
                listOf(hearing.get("retrievals")).add(saved);
            }
            saveHearing(hearing);
        }
        String sessionKey = null;
        String agentId = AGENTS.get(role);
        for (Map<String, Object> item : listOf(hearing.get("roles"))) {
            if (role.equals(item.get("role_code"))) sessionKey = item.get("session_key") == null ? null : String.valueOf(item.get("session_key"));
        }
        result.put("awaitHuman", false);
        result.put("agentId", agentId);
        result.put("sessionKey", sessionKey);
        result.put("message", message(hearing, role, String.valueOf(slot.get("plan_text")), snips, sessionLost));
        result.put("snippets", snips);
        return result;
    }

    public synchronized Map<String, Object> commit(long hearingId, long slotId, String body, String speaker) {
        Map<String, Object> hearing = requireHearing(hearingId);
        Map<String, Object> slot = slot(hearing, slotId);
        if (!"SPEAKING".equals(slot.get("status"))) throw new BusinessException("这一槽不能重复提交");
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new BusinessException("发言不能为空");
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", nextId());
        line.put("slot_id", slotId);
        line.put("role_code", slot.get("role_code"));
        line.put("speaker", "HUMAN".equals(speaker) ? "HUMAN" : "AGENT");
        line.put("body", text);
        line.put("char_count", text.length());
        listOf(hearing.get("utterances")).add(line);
        slot.put("status", "DONE");
        saveHearing(hearing);
        return view(hearing);
    }

    public synchronized Map<String, Object> skip(long hearingId, long slotId) {
        Map<String, Object> hearing = requireHearing(hearingId);
        Map<String, Object> slot = slot(hearing, slotId);
        if (!"SPEAKING".equals(slot.get("status"))) throw new BusinessException("这一槽不能跳过");
        slot.put("status", "SKIPPED");
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", nextId());
        line.put("slot_id", slotId);
        line.put("role_code", slot.get("role_code"));
        line.put("speaker", "AGENT");
        line.put("body", "本槽未发言");
        line.put("char_count", 6);
        listOf(hearing.get("utterances")).add(line);
        saveHearing(hearing);
        return view(hearing);
    }

    public synchronized Map<String, Object> interject(long hearingId, String body) {
        Map<String, Object> hearing = requireHearing(hearingId);
        if (!"OPEN".equals(hearing.get("status"))) throw new BusinessException("这场庭审已经结束");
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new BusinessException("发言不能为空");
        Map<String, Object> lastDone = null;
        for (Map<String, Object> item : listOf(hearing.get("slots"))) {
            if ("SPEAKING".equals(item.get("status"))) throw new BusinessException("当前槽还没结束，不能插话");
            if ("DONE".equals(item.get("status"))) lastDone = item;
        }
        if (lastDone == null) throw new BusinessException("还没有已结束的发言");
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", nextId());
        line.put("slot_id", lastDone.get("id"));
        line.put("role_code", "OURS");
        line.put("speaker", "HUMAN");
        line.put("body", text);
        line.put("char_count", text.length());
        listOf(hearing.get("utterances")).add(line);
        saveHearing(hearing);
        return view(hearing);
    }

    public synchronized Map<String, Object> close(long hearingId) {
        Map<String, Object> hearing = requireHearing(hearingId);
        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map<String, Object> role : listOf(hearing.get("roles"))) {
            if (role.get("session_key") != null) {
                Map<String, Object> key = new LinkedHashMap<>();
                key.put("role_code", role.get("role_code"));
                key.put("session_key", role.get("session_key"));
                keys.add(key);
                role.put("session_key", null);
            }
        }
        hearing.put("status", "CLOSED");
        hearing.put("closed_at", LocalDateTime.now().toString());
        saveHearing(hearing);
        Map<String, Object> caseRow = requireCase(num(hearing.get("case_id")));
        caseRow.put("status", "CLOSED");
        write(caseDir(num(hearing.get("case_id"))).resolve("case.json"), caseRow);
        Map<String, Object> out = view(hearing);
        out.put("closedSessions", keys);
        return out;
    }

    private String message(Map<String, Object> hearing, String role, String plan, List<Map<String, Object>> snips, boolean sessionLost) {
        Map<String, Object> caseRow = requireCase(num(hearing.get("case_id")));
        String name = switch (role) {
            case "JUDGE" -> "审判长";
            case "OPPONENT" -> "对方";
            case "CLERK" -> "书记员";
            default -> "我方";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("你是").append(name).append("。现在只说你这一段。不要代替其他角色发言。\n");
        sb.append("档案片段里没有的条款，写「档案未检索到」，不要写成已查明。\n\n【案情摘要】\n").append(caseRow.get("summary")).append("\n\n【庭审记录】\n");
        List<Map<String, Object>> said = new ArrayList<>(listOf(hearing.get("utterances")));
        if (!sessionLost) {
            int cut = 0;
            for (int i = 0; i < said.size(); i++) {
                if (role.equals(said.get(i).get("role_code"))) cut = i + 1;
            }
            if (cut > 0) said = said.subList(cut, said.size());
        }
        if (said.isEmpty()) sb.append("（还没有人发言）\n");
        for (Map<String, Object> line : said) sb.append(line.get("role_code")).append("：").append(line.get("body")).append('\n');
        sb.append("\n【本槽档案片段】\n");
        if (snips.isEmpty()) sb.append("本槽档案未检索到\n");
        for (Map<String, Object> snip : snips) {
            sb.append(snip.get("fileName")).append(" · 段 ").append(snip.get("seq")).append('\n').append(snip.get("excerpt")).append('\n');
        }
        sb.append("\n【现在轮到你】\n").append(plan);
        return sb.toString();
    }

    private List<Map<String, Object>> hits(long caseId, String query) {
        List<String> needles = markers(query);
        if (needles.isEmpty()) return List.of();
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> file : files(caseId)) {
            if (!"PARSED".equals(file.get("parseStatus"))) continue;
            List<String> parts = chunkList(file);
            for (int i = 0; i < parts.size() && found.size() < 5; i++) {
                if (!matchesAny(parts.get(i), needles)) continue;
                String body = parts.get(i);
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("chunkId", file.get("id") + "-" + i);
                hit.put("fileName", file.get("fileName"));
                hit.put("seq", i);
                hit.put("excerpt", body.length() > 800 ? body.substring(0, 800) : body);
                found.add(hit);
            }
        }
        return found;
    }

    private Map<String, Object> view(Map<String, Object> hearing) {
        Map<String, Object> hearingRow = new LinkedHashMap<>();
        hearingRow.put("id", hearing.get("id"));
        hearingRow.put("case_id", hearing.get("case_id"));
        hearingRow.put("status", hearing.get("status"));
        hearingRow.put("human_roles", hearing.get("human_roles"));
        hearingRow.put("started_at", hearing.get("started_at"));
        hearingRow.put("closed_at", hearing.get("closed_at"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hearing", hearingRow);
        out.put("roles", hearing.get("roles"));
        out.put("slots", hearing.get("slots"));
        out.put("utterances", hearing.get("utterances"));
        out.put("retrievals", hearing.get("retrievals"));
        return out;
    }

    private Map<String, Object> currentSlot(Map<String, Object> hearing) {
        Map<String, Object> pending = null;
        for (Map<String, Object> slot : listOf(hearing.get("slots"))) {
            if ("SPEAKING".equals(slot.get("status"))) return slot;
            if (pending == null && "PENDING".equals(slot.get("status"))) pending = slot;
        }
        return pending;
    }

    private Map<String, Object> slot(Map<String, Object> hearing, long slotId) {
        for (Map<String, Object> slot : listOf(hearing.get("slots"))) {
            if (num(slot.get("id")) == slotId) return slot;
        }
        throw new BusinessException("发言槽不存在");
    }

    private List<Map<String, Object>> retrievalsFor(Map<String, Object> hearing, long slotId) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> row : listOf(hearing.get("retrievals"))) {
            if (num(row.get("slotId")) == slotId) found.add(row);
        }
        return found;
    }

    private String lastUtterance(Map<String, Object> hearing) {
        List<Map<String, Object>> lines = listOf(hearing.get("utterances"));
        if (lines.isEmpty()) return "";
        return String.valueOf(lines.get(lines.size() - 1).get("body"));
    }

    private Map<String, Object> requireCase(long id) {
        Map<String, Object> row = readMap(caseDir(id).resolve("case.json"));
        if (row.isEmpty() || num(row.get("id")) != id) throw new BusinessException("案卷不存在");
        return row;
    }

    private Map<String, Object> requireFile(long caseId, long fileId) {
        for (Map<String, Object> file : files(caseId)) {
            if (num(file.get("id")) == fileId) return file;
        }
        throw new BusinessException("文件不存在");
    }

    private Map<String, Object> requireHearing(long hearingId) {
        Path dir = tenantDir();
        if (!Files.isDirectory(dir)) throw new BusinessException("庭审不存在");
        try (var cases = Files.list(dir)) {
            for (Path casePath : cases.filter(Files::isDirectory).toList()) {
                Path file = casePath.resolve("hearing-" + hearingId + ".json");
                if (Files.isRegularFile(file)) return readMap(file);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("庭审读不出来");
        }
        throw new BusinessException("庭审不存在");
    }

    private void saveHearing(Map<String, Object> hearing) {
        write(caseDir(num(hearing.get("case_id"))).resolve("hearing-" + hearing.get("id") + ".json"), hearing);
    }

    private List<Map<String, Object>> files(long caseId) {
        return readList(caseDir(caseId).resolve("files.json"));
    }

    private Map<String, Object> publicCase(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("signTaskId", row.get("signTaskId"));
        out.put("stance", row.get("stance"));
        out.put("summary", row.get("summary"));
        out.put("status", row.get("status"));
        out.put("createdAt", row.get("createdAt"));
        if (row.get("hearingId") != null) out.put("hearingId", row.get("hearingId"));
        return out;
    }

    private Path root() {
        Path path = Path.of(props.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
        } catch (Exception ex) {
            throw new ArchiveUnavailableException("档案目录建不起来");
        }
        return path;
    }

    private Path tenantDir() {
        Path path = root().resolve(String.valueOf(tenant())).normalize();
        if (!path.startsWith(root())) throw new BusinessException("非法路径");
        try {
            Files.createDirectories(path);
        } catch (Exception ex) {
            throw new ArchiveUnavailableException("档案目录建不起来");
        }
        return path;
    }

    private Path caseDir(long caseId) {
        Path path = tenantDir().resolve(String.valueOf(caseId)).normalize();
        if (!path.startsWith(root())) throw new BusinessException("非法路径");
        try {
            Files.createDirectories(path);
        } catch (Exception ex) {
            throw new ArchiveUnavailableException("案卷目录建不起来");
        }
        return path;
    }

    private long tenant() {
        Long id = JyTenantContext.get();
        if (id == null || id == 0L) throw new BusinessException("当前账号没有租户，不能使用档案");
        return id;
    }

    private long nextId() {
        Path file = root().resolve("next-id.txt");
        long id = 1;
        try {
            if (Files.isRegularFile(file)) id = Long.parseLong(Files.readString(file).trim()) + 1;
            Files.writeString(file, Long.toString(id));
        } catch (Exception ex) {
            throw new BusinessException("编号没有写下来");
        }
        return id;
    }

    private Map<String, Object> readMap(Path file) {
        if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
        try {
            return json.readValue(file.toFile(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BusinessException("档案记录读不出来");
        }
    }

    private List<Map<String, Object>> readList(Path file) {
        if (!Files.isRegularFile(file)) return new ArrayList<>();
        try {
            return json.readValue(file.toFile(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BusinessException("档案记录读不出来");
        }
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
        } catch (Exception ex) {
            throw new BusinessException("档案记录没有写下来");
        }
    }

    private byte[] readBytes(String relative) {
        Path target = root().resolve(relative).normalize();
        if (!target.startsWith(root())) throw new BusinessException("非法路径");
        try {
            return Files.readAllBytes(target);
        } catch (Exception ex) {
            throw new BusinessException("原件读不出来");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Object value) {
        if (value instanceof List<?> list) return (List<Map<String, Object>>) list;
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<String> chunkList(Map<String, Object> file) {
        Object value = file.get("chunks");
        if (value instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    private static long num(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static List<String> chunks(String text) {
        if (text == null || text.isBlank()) return List.of();
        String norm = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> paragraphs = new ArrayList<>();
        for (String part : norm.split("\\n\\s*\\n")) {
            String paragraph = part.trim();
            if (!paragraph.isEmpty()) paragraphs.add(paragraph);
        }
        if (paragraphs.isEmpty()) return List.of();
        List<String> units = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.length() <= 800) units.add(paragraph);
            else units.addAll(splitLong(paragraph));
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            int extra = current.isEmpty() ? unit.length() : unit.length() + 1;
            if (!current.isEmpty() && current.length() + extra > 700) {
                out.add(current.toString());
                String carry = overlapTail(current.toString(), 100);
                current = new StringBuilder(carry);
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(unit);
        }
        if (!current.isEmpty()) out.add(current.toString());
        return out;
    }

    private static List<String> splitLong(String paragraph) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + 700);
            if (end < paragraph.length()) {
                int breakAt = lastBreak(paragraph, start, end);
                if (breakAt > start + 200) end = breakAt;
            }
            String piece = paragraph.substring(start, end).trim();
            if (!piece.isEmpty()) parts.add(piece);
            if (end >= paragraph.length()) break;
            int next = Math.max(start + 1, end - 100);
            start = next;
        }
        return parts;
    }

    private static int lastBreak(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            char c = text.charAt(i - 1);
            if (c == '。' || c == '！' || c == '？' || c == '\n' || c == '；') return i;
        }
        return end;
    }

    private static String overlapTail(String text, int overlap) {
        if (text.length() <= overlap) return text;
        return text.substring(text.length() - overlap);
    }

    private static List<String> markers(String query) {
        if (query == null || query.isBlank()) return List.of();
        LinkedHashSet<String> found = new LinkedHashSet<>();
        collect(CLAUSE, query, found, true);
        collect(MONEY, query, found, false);
        collect(DATE, query, found, true);
        return new ArrayList<>(found);
    }

    private static void collect(Pattern pattern, String query, Set<String> found, boolean stripSpace) {
        Matcher matcher = pattern.matcher(query);
        while (matcher.find()) {
            String token = stripSpace ? matcher.group().replace(" ", "").replace("\u3000", "") : matcher.group().trim();
            if (!token.isBlank()) found.add(token);
        }
    }

    private static boolean matchesAny(String body, List<String> needles) {
        String folded = body.replace(" ", "").replace("\u3000", "");
        for (String needle : needles) {
            if (body.contains(needle) || folded.contains(needle.replace(" ", "").replace(",", ""))) return true;
            if (needle.indexOf(',') >= 0 && body.contains(needle.replace(",", ""))) return true;
        }
        return false;
    }

    private static String tail(String body) {
        if (body == null) return "";
        return body.length() <= 80 ? body : body.substring(body.length() - 80);
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new BusinessException("指纹计算失败");
        }
    }
}
