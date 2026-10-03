package com.commerce.cs.server.medical;

import com.commerce.cs.domain.entity.MedArticle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把已入库资料整理成知识库浏览结构：分类、摘要、阅读时长。
 * 文案来自资料本身，不另写一套医学结论。
 */
public final class KnowledgeCatalog {
    private static final List<Category> CATEGORIES = List.of(
            new Category("all", "全部分类", "按疾病、症状、用药和检查浏览"),
            new Category("disease", "常见疾病", "了解常见疾病的症状与护理"),
            new Category("symptom", "症状表现", "各类症状的可能原因与应对建议"),
            new Category("drug", "用药指南", "药品使用方法与注意事项"),
            new Category("exam", "检查检验", "常见检查项目解读与准备事项"),
            new Category("health", "健康科普", "日常健康知识与生活方式"),
            new Category("emergency", "急救知识", "紧急情况处理与就医提醒"),
            new Category("tcm", "中医调理", "中医养生与调理、体质改善建议")
    );

    private KnowledgeCatalog() {
    }

    public static Map<String, Object> build(List<MedArticle> articles) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (articles != null) {
            for (MedArticle article : articles) {
                if (article == null || article.getTitle() == null || article.getTitle().isBlank()) {
                    continue;
                }
                String category = categoryOf(article);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", article.getId());
                entry.put("title", article.getTitle());
                entry.put("summary", summary(article.getBody()));
                entry.put("category", category);
                entry.put("categoryLabel", labelOf(category));
                entry.put("minutes", minutes(article.getBody()));
                entry.put("sourceName", article.getSourceName() == null ? "" : article.getSourceName());
                entry.put("keywords", article.getKeywords() == null ? "" : article.getKeywords());
                entries.add(entry);
            }
        }
        markHot(entries);
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Category category : CATEGORIES) {
            long count = "all".equals(category.id)
                    ? entries.size()
                    : entries.stream().filter(item -> category.id.equals(item.get("category"))).count();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", category.id);
            view.put("label", category.label);
            view.put("blurb", category.blurb);
            view.put("count", count);
            categories.add(view);
        }
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("categories", categories);
        catalog.put("entries", entries);
        return catalog;
    }

    static String categoryOf(MedArticle article) {
        String title = text(article.getTitle());
        String code = text(article.getDiseaseCode());
        if (contains(title, "中医", "经络", "体质", "调理")) {
            return "tcm";
        }
        if (contains(title, "检查", "化验", "血常规", "检验") || "EXAM".equals(code)) {
            return "exam";
        }
        if (contains(title, "中毒", "急救") || "FOOD".equals(code) || "URTICARIA".equals(code)) {
            return "emergency";
        }
        if (contains(title, "用药", "退热药", "布洛芬", "抗生素的") || "DRUG".equals(code)) {
            return "drug";
        }
        if (contains(title, "湿疹", "便秘", "口腔", "龋", "牙痛", "饮食", "免疫", "偏头痛")) {
            return "health";
        }
        if (contains(title, "区别", "不要混", "咳嗽", "鼻炎", "风团")) {
            return "symptom";
        }
        return "disease";
    }

    private static void markHot(List<Map<String, Object>> entries) {
        List<String> prefer = List.of("disease", "drug", "exam", "health", "symptom", "emergency", "tcm");
        int marked = 0;
        for (String category : prefer) {
            if (marked >= 4) {
                return;
            }
            for (Map<String, Object> entry : entries) {
                if (category.equals(entry.get("category")) && !Boolean.TRUE.equals(entry.get("hot"))) {
                    entry.put("hot", true);
                    marked++;
                    break;
                }
            }
        }
        for (Map<String, Object> entry : entries) {
            entry.putIfAbsent("hot", false);
        }
    }

    private static String labelOf(String id) {
        for (Category category : CATEGORIES) {
            if (category.id.equals(id)) {
                return category.label;
            }
        }
        return "常见疾病";
    }

    private static String summary(String body) {
        String text = body == null ? "" : body.replace('\n', ' ').trim();
        if (text.length() <= 42) {
            return text;
        }
        return text.substring(0, 42) + "…";
    }

    private static int minutes(String body) {
        int length = body == null ? 0 : body.length();
        return Math.min(8, Math.max(3, (length + 79) / 80));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private record Category(String id, String label, String blurb) {
    }
}
