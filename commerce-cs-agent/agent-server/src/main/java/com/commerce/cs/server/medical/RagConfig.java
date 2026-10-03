package com.commerce.cs.server.medical;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RagProperties.class, WebSearchProperties.class})
public class RagConfig {
}
