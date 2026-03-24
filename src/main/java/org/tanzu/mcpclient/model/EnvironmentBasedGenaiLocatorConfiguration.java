package org.tanzu.mcpclient.model;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Allows configuring GenAI locator beans via environment variables instead of VCAP bindings.
 * Useful for local development and non-CF deployments.
 *
 * Example environment variables:
 *   GENAI_CHAT_CONFIG_URL=https://genai-proxy.example.com/chat-model/config/v1/endpoint
 *   GENAI_CHAT_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 *   GENAI_CHAT_API_BASE=https://genai-proxy.example.com/chat-model
 *
 *   GENAI_EMBEDDING_CONFIG_URL=https://genai-proxy.example.com/embed-model/config/v1/endpoint
 *   GENAI_EMBEDDING_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 *   GENAI_EMBEDDING_API_BASE=https://genai-proxy.example.com/embed-model
 */
@Configuration
public class EnvironmentBasedGenaiLocatorConfiguration {

    @Bean
    @ConditionalOnProperty("genai.embedding.config-url")
    public GenaiLocator embeddingGenaiLocator(
            @Value("${genai.embedding.config-url}") String configUrl,
            @Value("${genai.embedding.api-key}") String apiKey,
            @Value("${genai.embedding.api-base}") String apiBase) {
        return new DefaultGenaiLocator(RestClient.builder(), configUrl, apiKey, apiBase);
    }

    @Bean
    @ConditionalOnProperty("genai.chat.config-url")
    public GenaiLocator chatGenaiLocator(
            @Value("${genai.chat.config-url}") String configUrl,
            @Value("${genai.chat.api-key}") String apiKey,
            @Value("${genai.chat.api-base}") String apiBase) {
        return new DefaultGenaiLocator(RestClient.builder(), configUrl, apiKey, apiBase);
    }
}
