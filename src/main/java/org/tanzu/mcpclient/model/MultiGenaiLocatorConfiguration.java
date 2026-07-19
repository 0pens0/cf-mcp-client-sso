package org.tanzu.mcpclient.model;

import io.pivotal.cfenv.boot.genai.DefaultGenaiLocator;
import io.pivotal.cfenv.boot.genai.GenaiLocator;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.tanzu.mcpclient.bindings.ServiceBindingReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates {@link GenaiLocator} beans from Cloud Foundry VCAP services, Kubernetes
 * service bindings, and/or explicit {@code GENAI_CHAT_*} / {@code GENAI_EMBEDDING_*}
 * environment variables.
 */
@Configuration
@ConditionalOnProperty(name = "app.multigenai.enabled", havingValue = "true")
public class MultiGenaiLocatorConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MultiGenaiLocatorConfiguration.class);

    /**
     * Creates GenaiLocator beans from VCAP, Kubernetes bindings, and GENAI_* env vars.
     * Uses a standalone {@link RestClient.Builder} (not the Spring-managed bean) to avoid
     * Actuator ObservationRegistry circular dependencies.
     */
    @Bean
    public List<GenaiLocator> manualGenaiLocators(Environment environment) {
        RestClient.Builder builder = RestClient.builder();
        List<GenaiLocator> locators = new ArrayList<>();

        locators.addAll(fromVcapServices(builder));
        locators.addAll(fromServiceBindings(builder));
        fromEnvVars(builder, environment, "GENAI_CHAT", "chat").ifPresent(locators::add);
        fromEnvVars(builder, environment, "GENAI_EMBEDDING", "embedding").ifPresent(locators::add);

        logger.info("Configured {} GenaiLocator bean(s) (VCAP + service bindings + env)", locators.size());
        return locators;
    }

    private List<GenaiLocator> fromVcapServices(RestClient.Builder builder) {
        try {
            CfEnv cfEnv = new CfEnv();
            return cfEnv.findAllServices().stream()
                    .filter(this::isGenaiService)
                    .map(service -> createGenaiLocator(service, builder))
                    .toList();
        } catch (Exception e) {
            logger.debug("VCAP_SERVICES GenAI discovery skipped: {}", e.getMessage());
            return List.of();
        }
    }

    private List<GenaiLocator> fromServiceBindings(RestClient.Builder builder) {
        List<GenaiLocator> locators = new ArrayList<>();
        for (ServiceBindingReader.Binding binding : ServiceBindingReader.readAll()) {
            if (!binding.isGenai()) {
                continue;
            }
            Optional<String> configUrl = binding.genaiConfigUrl();
            Optional<String> apiKey = binding.genaiApiKey();
            Optional<String> apiBase = binding.genaiApiBase();

            if (configUrl.isPresent() && apiKey.isPresent() && apiBase.isPresent()) {
                logger.info("Creating GenaiLocator from service binding '{}'", binding.name());
                locators.add(new DefaultGenaiLocator(builder, configUrl.get(), apiKey.get(), apiBase.get()));
            } else if (apiKey.isPresent() && apiBase.isPresent()) {
                // OpenAI-compatible binding without GenAI config endpoint: synthesize config URL path
                String base = trimTrailingSlash(apiBase.get());
                String synthesizedConfig = base + "/config/v1/endpoint";
                logger.info("Creating GenaiLocator from service binding '{}' with synthesized config URL",
                        binding.name());
                locators.add(new DefaultGenaiLocator(builder, synthesizedConfig, apiKey.get(), base));
            } else {
                logger.debug("Skipping GenAI-like binding '{}': missing config_url/api_key/api_base", binding.name());
            }
        }
        return locators;
    }

    private Optional<GenaiLocator> fromEnvVars(RestClient.Builder builder,
                                               Environment environment,
                                               String prefix,
                                               String label) {
        String configUrl = firstNonBlank(
                environment.getProperty(prefix + "_CONFIG_URL"),
                environment.getProperty(prefix.toLowerCase().replace('_', '.') + ".config-url"));
        String apiKey = firstNonBlank(
                environment.getProperty(prefix + "_API_KEY"),
                environment.getProperty(prefix.toLowerCase().replace('_', '.') + ".api-key"));
        String apiBase = firstNonBlank(
                environment.getProperty(prefix + "_API_BASE"),
                environment.getProperty(prefix.toLowerCase().replace('_', '.') + ".api-base"));

        if (configUrl == null || apiKey == null || apiBase == null) {
            logger.debug("No complete {} env configuration found for GenaiLocator", prefix);
            return Optional.empty();
        }

        logger.info("Creating GenaiLocator from {} environment variables ({})", prefix, label);
        return Optional.of(new DefaultGenaiLocator(builder, configUrl, apiKey, trimTrailingSlash(apiBase)));
    }

    private boolean isGenaiService(CfService service) {
        boolean hasGenaiTag = service.existsByTagIgnoreCase("genai") ||
                service.existsByLabelStartsWith("genai");
        boolean hasEndpoint = service.getCredentials().getMap().containsKey("endpoint");
        return hasGenaiTag && hasEndpoint;
    }

    private GenaiLocator createGenaiLocator(CfService service, RestClient.Builder builder) {
        Map<String, Object> credentials = service.getCredentials().getMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> endpoint = (Map<String, Object>) credentials.get("endpoint");

        String configUrl = (String) endpoint.get("config_url");
        String apiKey = (String) endpoint.get("api_key");
        String apiBase = (String) endpoint.get("api_base");

        logger.info("Creating GenaiLocator from CF service '{}'", service.getName());
        return new DefaultGenaiLocator(builder, configUrl, apiKey, apiBase);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
