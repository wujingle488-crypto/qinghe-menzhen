package com.commerce.cs.server.medical;

import com.commerce.cs.server.llm.DeepSeekClient;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 把联网检索结果整理成科普短文，异步写入知识底账（可再被关键词检索命中）。
 * 不阻塞本轮问诊；向量库全量重切由离线 index_kb 负责。
 */
@Service
public class WebKnowledgeIngest {
    private final KnowledgeLedger ledger;
    private final DeepSeekClient llm;

    public WebKnowledgeIngest(KnowledgeLedger ledger, DeepSeekClient llm) {
        this.ledger = ledger;
        this.llm = llm;
    }

    public void ingestLater(String query, List<RagFacade.Hit> hits, String diseaseCode) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        List<RagFacade.Hit> copy = List.copyOf(hits);
        CompletableFuture.runAsync(() -> ingestNow(query, copy, diseaseCode));
    }

    void ingestNow(String query, List<RagFacade.Hit> hits, String diseaseCode) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        String evidence = hits.stream()
                .map(hit -> "- " + hit.title() + "\n  " + clip(hit.text(), 280)
                        + (hit.sourceUrl() == null || hit.sourceUrl().isBlank() ? "" : "\n  来源：" + hit.sourceUrl()))
                .collect(Collectors.joining("\n"));
        String title = "联网补充：" + clip(query == null || query.isBlank() ? hits.get(0).title() : query, 40);
        String body;
        if (llm.available()) {
            String drafted = llm.chat("""
                    你在整理教学门诊助手的知识底账。根据下列公开检索摘要，写一篇不超过 400 字的中文科普短文。
                    要求：只用事实陈述；不要处方抗生素；不要声称确诊；用 Markdown 小标题。
                    只输出正文。
                    """, evidence);
            body = drafted == null || drafted.isBlank() ? fallbackBody(query, hits) : drafted.trim();
        } else {
            body = fallbackBody(query, hits);
        }
        String keywords = (query == null ? "" : query) + " " + hits.stream()
                .map(RagFacade.Hit::title)
                .collect(Collectors.joining(" "));
        String sourceUrl = hits.stream()
                .map(RagFacade.Hit::sourceUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse("");
        ledger.addArticle(title, body, keywords.trim(), sourceUrl,
                diseaseCode == null ? "" : diseaseCode, "联网检索");
    }

    private static String fallbackBody(String query, List<RagFacade.Hit> hits) {
        StringBuilder text = new StringBuilder();
        text.append("## ").append(query == null || query.isBlank() ? "联网资料摘要" : query).append("\n\n");
        for (RagFacade.Hit hit : hits) {
            text.append("### ").append(hit.title()).append("\n\n");
            text.append(clip(hit.text(), 220)).append("\n\n");
            if (hit.sourceUrl() != null && !hit.sourceUrl().isBlank()) {
                text.append("来源：").append(hit.sourceUrl()).append("\n\n");
            }
        }
        text.append("*以上为公开网页摘要，仅供教学参考。*");
        return text.toString();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
