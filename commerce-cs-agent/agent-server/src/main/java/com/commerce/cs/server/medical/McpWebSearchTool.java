package com.commerce.cs.server.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 联网检索工具，形态对齐 MCP tools/call：
 * name=web_search，入参 query，出参标题/摘要/链接。
 * 本地默认走 DuckDuckGo Instant Answer + 维基百科中文；
 * 配置 web-search.mcp-url 后改为转发到外部 MCP HTTP 端点。
 */
@Component
public class McpWebSearchTool {
    public static final String TOOL_NAME = "web_search";

    private final WebSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public McpWebSearchTool(WebSearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String name() {
        return TOOL_NAME;
    }

    public String description() {
        return "在公开网页检索与症状/疾病相关的科普资料，返回标题、摘要和链接。";
    }

    public List<RagFacade.Hit> search(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            if (properties.getMcpUrl() != null && !properties.getMcpUrl().isBlank()) {
                return fromMcp(query.trim());
            }
            String provider = properties.getProvider() == null ? "duckduckgo" : properties.getProvider().trim().toLowerCase();
            return switch (provider) {
                case "wikipedia" -> fromWikipedia(query.trim());
                case "mcp" -> fromMcp(query.trim());
                default -> mergeUnique(fromDuckDuckGo(query.trim()), fromWikipedia(query.trim()));
            };
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<RagFacade.Hit> fromMcp(String query) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", "web-search");
        body.put("method", "tools/call");
        body.put("params", Map.of(
                "name", properties.getMcpToolName() == null || properties.getMcpToolName().isBlank()
                        ? TOOL_NAME : properties.getMcpToolName(),
                "arguments", Map.of("query", query)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getMcpUrl().trim()))
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 500)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("result").path("content");
        if (!content.isArray()) {
            content = root.path("content");
        }
        List<RagFacade.Hit> hits = new ArrayList<>();
        if (content.isArray()) {
            for (JsonNode node : content) {
                String text = textOf(node);
                if (text.isBlank()) {
                    continue;
                }
                hits.add(new RagFacade.Hit("联网", clip(text, 80), text, "MCP联网", "", ""));
                if (hits.size() >= properties.getMaxResults()) {
                    break;
                }
            }
        }
        JsonNode results = root.path("result").path("results");
        if (results.isArray()) {
            for (JsonNode node : results) {
                String title = node.path("title").asText("");
                String snippet = node.path("snippet").asText(node.path("text").asText(""));
                String url = node.path("url").asText(node.path("link").asText(""));
                if (title.isBlank() && snippet.isBlank()) {
                    continue;
                }
                hits.add(new RagFacade.Hit("联网", title.isBlank() ? clip(snippet, 80) : title,
                        snippet.isBlank() ? title : snippet, "MCP联网", url, ""));
                if (hits.size() >= properties.getMaxResults()) {
                    break;
                }
            }
        }
        return hits.stream().limit(properties.getMaxResults()).toList();
    }

    private List<RagFacade.Hit> fromDuckDuckGo(String query) throws Exception {
        String url = "https://api.duckduckgo.com/?q=" + encode(query)
                + "&format=json&no_html=1&skip_disambig=1";
        JsonNode root = getJson(url);
        List<RagFacade.Hit> hits = new ArrayList<>();
        String abstractText = root.path("AbstractText").asText("");
        String abstractUrl = root.path("AbstractURL").asText("");
        String heading = root.path("Heading").asText(query);
        if (!abstractText.isBlank()) {
            hits.add(new RagFacade.Hit("联网", heading, abstractText, "DuckDuckGo", abstractUrl, ""));
        }
        JsonNode related = root.path("RelatedTopics");
        if (related.isArray()) {
            for (JsonNode node : related) {
                JsonNode topic = node.has("Topics") && node.path("Topics").isArray() && !node.path("Topics").isEmpty()
                        ? node.path("Topics").get(0) : node;
                String text = topic.path("Text").asText("");
                String link = topic.path("FirstURL").asText("");
                if (text.isBlank()) {
                    continue;
                }
                hits.add(new RagFacade.Hit("联网", clip(text, 60), text, "DuckDuckGo", link, ""));
                if (hits.size() >= properties.getMaxResults()) {
                    break;
                }
            }
        }
        return hits;
    }

    private List<RagFacade.Hit> fromWikipedia(String query) throws Exception {
        String searchUrl = "https://zh.wikipedia.org/w/api.php?action=opensearch&limit="
                + Math.max(properties.getMaxResults(), 1)
                + "&namespace=0&format=json&search=" + encode(query);
        JsonNode arr = getJson(searchUrl);
        if (!arr.isArray() || arr.size() < 4) {
            return List.of();
        }
        JsonNode titles = arr.get(1);
        JsonNode descs = arr.get(2);
        JsonNode links = arr.get(3);
        List<RagFacade.Hit> hits = new ArrayList<>();
        int n = Math.min(titles.size(), Math.min(descs.size(), links.size()));
        for (int i = 0; i < n && hits.size() < properties.getMaxResults(); i++) {
            String title = titles.get(i).asText("");
            String desc = descs.get(i).asText("");
            String link = links.get(i).asText("");
            if (title.isBlank()) {
                continue;
            }
            hits.add(new RagFacade.Hit("联网", title,
                    desc.isBlank() ? title : desc, "维基百科", link, ""));
        }
        return hits;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 500)))
                .header("User-Agent", "QingHeClinicTeachingBot/1.0")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(response.body());
    }

    private static List<RagFacade.Hit> mergeUnique(List<RagFacade.Hit> first, List<RagFacade.Hit> second) {
        Map<String, RagFacade.Hit> map = new LinkedHashMap<>();
        for (RagFacade.Hit hit : first) {
            map.putIfAbsent(key(hit), hit);
        }
        for (RagFacade.Hit hit : second) {
            map.putIfAbsent(key(hit), hit);
        }
        return List.copyOf(map.values());
    }

    private static String key(RagFacade.Hit hit) {
        String url = hit.sourceUrl() == null ? "" : hit.sourceUrl().trim();
        if (!url.isBlank()) {
            return url;
        }
        return (hit.title() == null ? "" : hit.title().trim());
    }

    private static String textOf(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        return node.path("text").asText(node.path("content").asText(""));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
