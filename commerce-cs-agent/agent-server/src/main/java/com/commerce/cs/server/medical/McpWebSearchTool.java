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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(McpWebSearchTool.class);
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern RSS_ITEM = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RSS_TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RSS_LINK = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern RSS_DESC = Pattern.compile("<description>(.*?)</description>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final WebSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
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
            String q = query.trim();
            String provider = properties.getProvider() == null ? "bing" : properties.getProvider().trim().toLowerCase();
            List<RagFacade.Hit> hits = switch (provider) {
                case "wikipedia" -> fromWikipedia(q);
                case "mcp" -> fromMcp(q);
                case "duckduckgo" -> mergeUnique(fromDuckDuckGo(q), fromWikipedia(q));
                default -> mergeUnique(fromPublicSnapshot(q), relatedHits(fromBing(q), q));
            };
            if (hits.isEmpty() && !"wikipedia".equals(provider) && !"mcp".equals(provider)) {
                hits = mergeUnique(fromWikipedia(q), fromDuckDuckGo(q));
            }
            if (hits.isEmpty()) {
                LOG.warn("联网检索无结果 query={}", clip(q, 40));
            }
            return hits.stream().limit(properties.getMaxResults()).toList();
        } catch (Exception ex) {
            LOG.warn("联网检索失败: {}", ex.getMessage());
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

    /** 国内可访问的公开检索：必应 RSS。 */
    private List<RagFacade.Hit> fromBing(String query) throws Exception {
        String url = "https://cn.bing.com/search?format=rss&q=" + encode(query);
        String body = getText(url, BROWSER_UA);
        if (body.isBlank() || !body.contains("<item>")) {
            return List.of();
        }
        List<RagFacade.Hit> hits = new ArrayList<>();
        Matcher items = RSS_ITEM.matcher(body);
        while (items.find() && hits.size() < properties.getMaxResults()) {
            String item = items.group(1);
            String title = unescapeXml(first(RSS_TITLE, item));
            String link = unescapeXml(first(RSS_LINK, item));
            String desc = stripTags(unescapeXml(first(RSS_DESC, item)));
            if (title.isBlank() && desc.isBlank()) {
                continue;
            }
            hits.add(new RagFacade.Hit("联网", title.isBlank() ? clip(desc, 80) : title,
                    desc.isBlank() ? title : desc, "必应", link, ""));
        }
        return hits;
    }

    /**
     * 公开实时页。地名从问句里抽出后走同一数据源，避免 wttr/必应各报一个气温。
     */
    private List<RagFacade.Hit> fromPublicSnapshot(String query) {
        String guessed = guessPlace(query);
        List<RagFacade.Hit> meteo = fromOpenMeteo(guessed.isBlank() ? query : guessed);
        if (!meteo.isEmpty()) {
            return meteo;
        }
        List<String> places = new ArrayList<>();
        if (!guessed.isBlank()) {
            places.add(guessed);
        }
        places.add(query);
        for (String place : places) {
            try {
                String url = "https://wttr.in/" + encode(place) + "?format=3&lang=zh";
                String body = getText(url, "curl/8.0");
                if (body != null && (body.contains("°C") || body.contains("℃") || body.contains("°F"))) {
                    String text = body.trim();
                    return List.of(new RagFacade.Hit("联网", clip(text, 80), text, "公开网页", url, ""));
                }
            } catch (Exception ignored) {
                // 换下一个地名再试
            }
        }
        return List.of();
    }

    private List<RagFacade.Hit> fromOpenMeteo(String place) {
        if (place == null || place.isBlank() || place.length() > 20) {
            return List.of();
        }
        try {
            JsonNode geo = getJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=zh&name="
                    + encode(place));
            JsonNode first = geo.path("results").isArray() && geo.path("results").size() > 0
                    ? geo.path("results").get(0) : null;
            if (first == null) {
                return List.of();
            }
            String name = first.path("name").asText(place);
            String lat = first.path("latitude").asText("");
            String lon = first.path("longitude").asText("");
            if (lat.isBlank() || lon.isBlank()) {
                return List.of();
            }
            String forecastUrl = "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,wind_speed_10m&timezone=Asia/Shanghai"
                    + "&latitude=" + lat + "&longitude=" + lon;
            JsonNode cur = getJson(forecastUrl).path("current");
            if (cur.isMissingNode() || cur.path("temperature_2m").isMissingNode()) {
                return List.of();
            }
            String text = name + "当前气温 " + cur.path("temperature_2m").asText() + "°C，风速 "
                    + cur.path("wind_speed_10m").asText() + " km/h";
            return List.of(new RagFacade.Hit("联网", text, text, "公开网页", forecastUrl, ""));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String guessPlace(String query) {
        if (query == null) {
            return "";
        }
        String loc = query.replaceAll("(?i)(今天|现在|目前|今日|这会儿|today|now)", "");
        loc = loc.replaceAll("(?i)(的)?(天气|气温|气候|预报|温度|weather|forecast|怎么样|如何|怎样|多少度).*$", "");
        return loc.replaceAll("[\\s?？。！!，,：:]+", "").trim();
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
        String body = getText(url, "QingHeClinicTeachingBot/1.0");
        if (body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private String getText(String url, String userAgent) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1500)))
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            return "";
        }
        return response.body() == null ? "" : response.body();
    }

    private static List<RagFacade.Hit> relatedHits(List<RagFacade.Hit> hits, String query) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RagFacade.Hit> kept = new ArrayList<>();
        for (RagFacade.Hit hit : hits) {
            String hay = ((hit.title() == null ? "" : hit.title()) + " " + (hit.text() == null ? "" : hit.text()))
                    .replace(" ", "");
            if (hay.contains("°C") || hay.contains("℃") || hay.contains("气温") || hay.contains("天气")) {
                kept.add(hit);
                continue;
            }
            String q = query == null ? "" : query.replaceAll("(?i)(今天|现在|目前|如何|怎样|怎么样|什么|多少|的|了|吗|呢)", "");
            boolean related = false;
            for (String token : q.split("[\\s，,。？?！!]+")) {
                if (token.length() >= 2 && hay.contains(token)) {
                    related = true;
                    break;
                }
            }
            if (related) {
                kept.add(hit);
            }
        }
        return kept;
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

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String unescapeXml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&").replace("<![CDATA[", "").replace("]]>", "");
    }

    private static String stripTags(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
