package com.jyfc.backend.module.compare.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同比对服务（compare 切片）。diff：双文本按 [。\n；] 切句（保留分隔符）→ 句级 LCS 动态规划表 →
 * 回溯生成 same/add/del 段序列（相邻同型合并）+ 计数 {added, deleted, sameCount}。
 * 纯计算无状态；口径为句子级近似比对，字符级高亮由后续切片细化。
 */
@Service
public class CompareService {

    /** 切句：在 。 \n ； 之后断开，分隔符保留在句尾。 */
    private static List<String> sentences(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : text.split("(?<=[。\n；])")) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public Map<String, Object> diff(String textA, String textB) {
        List<String> a = sentences(textA);
        List<String> b = sentences(textB);
        int n = a.size();
        int m = b.size();

        // LCS 表：lcs[i][j] = a[i..] 与 b[j..] 的最长公共子序列长度
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a.get(i).equals(b.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<Map<String, String>> segments = new ArrayList<>();
        int added = 0;
        int deleted = 0;
        int sameCount = 0;
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                append(segments, "same", a.get(i));
                sameCount++;
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                append(segments, "del", a.get(i));
                deleted++;
                i++;
            } else {
                append(segments, "add", b.get(j));
                added++;
                j++;
            }
        }
        while (i < n) {
            append(segments, "del", a.get(i));
            deleted++;
            i++;
        }
        while (j < m) {
            append(segments, "add", b.get(j));
            added++;
            j++;
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("added", added);
        counts.put("deleted", deleted);
        counts.put("sameCount", sameCount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("segments", segments);
        out.put("counts", counts);
        return out;
    }

    /** 相邻同型段合并，避免碎片化输出。 */
    private static void append(List<Map<String, String>> segments, String type, String text) {
        if (!segments.isEmpty()) {
            Map<String, String> last = segments.get(segments.size() - 1);
            if (type.equals(last.get("type"))) {
                last.put("text", last.get("text") + text);
                return;
            }
        }
        Map<String, String> seg = new LinkedHashMap<>();
        seg.put("type", type);
        seg.put("text", text);
        segments.add(seg);
    }
}
