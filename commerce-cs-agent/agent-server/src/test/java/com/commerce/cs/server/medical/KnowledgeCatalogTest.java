package com.commerce.cs.server.medical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.commerce.cs.domain.entity.MedArticle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KnowledgeCatalogTest {
    @Test
    void classifiesRealArticlesIntoBrowseCategories() {
        MedArticle flu = article("普通感冒和流感不要混为一谈", "普通感冒多为鼻病毒引起，避免滥用抗生素。", "感冒,咽痛");
        MedArticle poison = article("食物中毒先停食并留样", "进食不洁食物后短时间出现呕吐。", "食物中毒");
        Map<String, Object> catalog = KnowledgeCatalog.build(List.of(flu, poison));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) catalog.get("entries");
        assertEquals("symptom", entries.get(0).get("category"));
        assertEquals("emergency", entries.get(1).get("category"));
        assertEquals("症状表现", entries.get(0).get("categoryLabel"));
        assertFalse(String.valueOf(entries.get(0).get("summary")).isBlank());
    }

    private static MedArticle article(String title, String body, String keywords) {
        MedArticle article = new MedArticle();
        article.setTitle(title);
        article.setBody(body);
        article.setKeywords(keywords);
        article.setDiseaseCode("URI");
        article.setSourceName("教学摘编");
        article.setSourceUrl("https://www.chinacdc.cn/");
        return article;
    }
}
