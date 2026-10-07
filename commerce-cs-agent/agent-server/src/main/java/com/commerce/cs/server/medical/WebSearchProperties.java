package com.commerce.cs.server.medical;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "web-search")
public class WebSearchProperties {
    /** 总开关：关闭时前端打开联网也不会发请求。 */
    private boolean enabled = true;
    /** bing | duckduckgo | wikipedia | mcp */
    private String provider = "bing";
    /** 可选：兼容 MCP tools/call 的 HTTP 端点。 */
    private String mcpUrl = "";
    private String mcpToolName = "web_search";
    private int timeoutMs = 8000;
    private int maxResults = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getMcpUrl() { return mcpUrl; }
    public void setMcpUrl(String mcpUrl) { this.mcpUrl = mcpUrl; }
    public String getMcpToolName() { return mcpToolName; }
    public void setMcpToolName(String mcpToolName) { this.mcpToolName = mcpToolName; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
}
