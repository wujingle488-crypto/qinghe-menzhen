package com.commerce.cs.server.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekClient {
    private static final Logger LOG = LoggerFactory.getLogger(DeepSeekClient.class);
    private final LlmProperties properties;
    private final RestClient restClient;

    public DeepSeekClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(8));
        factory.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder()
                .baseUrl(trimSlash(properties.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    public boolean available() {
        return properties.isEnabled() && properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (!available()) {
            return "";
        }
        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new ChatRequest(
                            properties.getModel(),
                            List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                            0.2))
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return "";
            }
            ChatMessage message = response.choices().get(0).message();
            return message == null || message.content() == null ? "" : message.content().trim();
        } catch (Exception ex) {
            LOG.warn("DeepSeek 调用失败: {}", ex.getMessage());
            return "";
        }
    }

    /** 只描述画面。失败时返回空串，由问诊侧说明看不清，不编造体征。 */
    public String describeImage(String mime, String base64) {
        if (!available() || base64 == null || base64.isBlank()) {
            return "";
        }
        String type = mime == null || mime.isBlank() ? "image/jpeg" : mime;
        try {
            ChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(new VisionRequest(
                            properties.getVisionModel(),
                            List.of(
                                    new VisionMessage("system", """
                                            你是教学用门诊助手的观察员。只描述图片里能直接看见的体征，例如部位、颜色、范围、是否有渗出或肿胀。
                                            不要下诊断，不要写药品名称，不要说已经确诊。看不清就说看不清。最多三句中文。"""),
                                    new VisionMessage("user", List.of(
                                            Map.of("type", "text", "text", "请只做观察。"),
                                            Map.of("type", "image_url", "image_url",
                                                    Map.of("url", "data:" + type + ";base64," + base64))
                                    ))),
                            0.2))
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return "";
            }
            ChatMessage message = response.choices().get(0).message();
            return message == null || message.content() == null ? "" : message.content().trim();
        } catch (Exception ex) {
            LOG.warn("图片观察失败: {}", ex.getMessage());
            return "";
        }
    }

    private String trimSlash(String url) {
        if (url == null) {
            return "https://api.deepseek.com";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record VisionRequest(String model, List<VisionMessage> messages, double temperature) {
    }

    public record VisionMessage(String role, Object content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(ChatMessage message) {
    }
}
