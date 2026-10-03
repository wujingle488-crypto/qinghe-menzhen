package com.commerce.cs.server.web;

import com.commerce.cs.domain.entity.MedArticle;
import com.commerce.cs.server.medical.KnowledgeCatalog;
import com.commerce.cs.server.medical.KnowledgeLedger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KbController {
    private final KnowledgeLedger ledger;

    public KbController(KnowledgeLedger ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/api/kb")
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("diseases", ledger.diseases());
        data.put("drugs", ledger.drugs());
        data.put("redFlags", ledger.redFlags());
        data.put("articles", ledger.articles());
        data.put("catalog", KnowledgeCatalog.build(ledger.articles()));
        return data;
    }

    @PostMapping("/api/kb/articles")
    public MedArticle add(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String text = body.getOrDefault("body", "").trim();
        if (title.isBlank() || text.isBlank()) {
            throw new IllegalArgumentException("标题和正文不能为空");
        }
        return ledger.addArticle(title, text, body.get("keywords"), body.get("sourceUrl"), body.get("diseaseCode"));
    }
}
