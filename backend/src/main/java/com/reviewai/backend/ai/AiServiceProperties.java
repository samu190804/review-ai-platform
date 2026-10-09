package com.reviewai.backend.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración con prefijo "ai-service" en application.properties. */
@ConfigurationProperties(prefix = "ai-service")
public record AiServiceProperties(String url, Duration connectTimeout, Duration readTimeout) {
}
