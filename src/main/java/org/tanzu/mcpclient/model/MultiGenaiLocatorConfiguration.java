package org.tanzu.mcpclient.model;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manual configuration for multiple GenaiLocator beans
 * This bypasses the java-cfenv limitation by directly reading VCAP_SERVICES
 * and creating GenaiLocator beans for each GenAI service found.
 */
@Configuration
public class MultiGenaiLocatorConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultiGenaiLocatorConfiguration.class);

    /**
     * Creates multiple GenaiLocator beans by directly parsing VCAP_SERVICES.
     * Uses a standalone RestClient.Builder (not the Spring-managed auto-configured one)
     * to avoid the Actuator observation circular dependency on ObservationRegistry.
     */
    @Bean
    public List<GenaiLocator> manualGenaiLocators() {
        RestClient.Builder builder = RestClient.builder();
        CfEnv cfEnv = new CfEnv();

        return cfEnv.findAllServices().stream()
                .filter(this::isGenaiService)
                .sorted(Comparator.comparing(this::genaiServiceSortKey))
                .map(service -> createGenaiLocator(service, builder))
                .toList();
    }

    /**
     * Prefer chat-capable instances before embedding-only so {@link org.tanzu.mcpclient.model.MultiGenaiLocatorAggregator#getFirstAvailableChatModel()}
     * tries the correct binding first.
     */
    private String genaiServiceSortKey(CfService service) {
        String name = service.getName() != null ? service.getName().toLowerCase(Locale.ROOT) : "";
        if (name.contains("chat")) {
            return "0-" + name;
        }
        if (name.contains("embed")) {
            return "2-" + name;
        }
        return "1-" + name;
    }

    /**
     * Checks if a service is a GenAI service
     */
    private boolean isGenaiService(CfService service) {
        boolean hasGenaiTag = service.existsByTagIgnoreCase("genai") ||
                service.existsByLabelStartsWith("genai");
        if (!hasGenaiTag) {
            return false;
        }
        Map<String, Object> credentials = service.getCredentials().getMap();
        if (credentials == null) {
            return false;
        }
        return hasResolvableGenaiCredentials(credentials);
    }

    private boolean hasResolvableGenaiCredentials(Map<String, Object> credentials) {
        Object endpoint = credentials.get("endpoint");
        if (endpoint instanceof Map<?, ?> endpointMap) {
            return endpointMap.containsKey("api_key") && endpointMap.containsKey("api_base");
        }
        return credentials.containsKey("api_key") && credentials.containsKey("api_base");
    }

    /**
     * Creates a GenaiLocator from a CfService
     */
    private GenaiLocator createGenaiLocator(CfService service, RestClient.Builder builder) {
        Map<String, Object> credentials = service.getCredentials().getMap();
        String configUrl;
        String apiKey;
        String apiBase;

        Object endpointObj = credentials.get("endpoint");
        if (endpointObj instanceof Map<?, ?> endpointMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> endpoint = (Map<String, Object>) endpointMap;
            configUrl = stringVal(endpoint.get("config_url"));
            apiKey = stringVal(endpoint.get("api_key"));
            apiBase = stringVal(endpoint.get("api_base"));
        } else {
            configUrl = stringVal(credentials.get("config_url"));
            apiKey = stringVal(credentials.get("api_key"));
            apiBase = stringVal(credentials.get("api_base"));
        }

        if (apiKey == null || apiKey.isEmpty() || apiBase == null || apiBase.isEmpty()) {
            logger.warn("GenAI service '{}' missing api_key or api_base after parsing; locator may be non-functional",
                    service.getName());
        }

        return new DefaultGenaiLocator(builder, configUrl, apiKey, apiBase);
    }

    private static String stringVal(Object o) {
        return o == null ? null : o.toString();
    }
}

/*
 * If you want to set these manually via environment variables or application.yml:
 *
 * Environment variables example:
 * GENAI_EMBEDDING_CONFIG_URL=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/prod-embedding-nomic-text-97b9b92/config/v1/endpoint
 * GENAI_EMBEDDING_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 * GENAI_EMBEDDING_API_BASE=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/prod-embedding-nomic-text-97b9b92
 *
 * GENAI_CHAT_CONFIG_URL=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/local-mistral-nemo-instruct-2407-5c3c88c/config/v1/endpoint
 * GENAI_CHAT_API_KEY=eyJhbGciOiJIUzI1NiJ9...
 * GENAI_CHAT_API_BASE=https://genai-proxy.sys.tas-ndc.kuhn-labs.com/local-mistral-nemo-instruct-2407-5c3c88c
 */