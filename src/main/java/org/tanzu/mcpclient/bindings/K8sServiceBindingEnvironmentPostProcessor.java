package org.tanzu.mcpclient.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Kubernetes service bindings into Spring properties early enough for datasource
 * availability checks and Spring AI property-based configuration.
 * <p>
 * Active when profile {@code k8s} is set, or when {@code SERVICE_BINDING_ROOT} is present.
 */
public class K8sServiceBindingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(K8sServiceBindingEnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "k8sServiceBindings";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean k8sProfile = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("k8s"));
        boolean hasBindingRoot = System.getenv(ServiceBindingReader.SERVICE_BINDING_ROOT_PROPERTY) != null
                || System.getProperty(ServiceBindingReader.SERVICE_BINDING_ROOT_PROPERTY) != null;

        if (!k8sProfile && !hasBindingRoot) {
            return;
        }

        Map<String, Object> properties = new HashMap<>();

        Optional<ServiceBindingReader.Binding> postgres = ServiceBindingReader.findFirstPostgres();
        postgres.ifPresent(binding -> applyPostgres(binding, properties, environment));

        // Prefer explicit GENAI_* env already in the environment; bindings fill gaps for Spring AI props
        applyGenaiBindingAsSpringAiProps(
                ServiceBindingReader.findFirstGenaiChat(),
                "spring.ai.openai.chat",
                properties,
                environment);
        applyGenaiBindingAsSpringAiProps(
                ServiceBindingReader.findFirstGenaiEmbedding(),
                "spring.ai.openai.embedding",
                properties,
                environment);

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            logger.info("Applied {} properties from Kubernetes service bindings", properties.size());
        }
    }

    private void applyPostgres(ServiceBindingReader.Binding binding,
                               Map<String, Object> properties,
                               ConfigurableEnvironment environment) {
        if (environment.containsProperty("spring.datasource.url")
                && environment.getProperty("spring.datasource.url") != null
                && !environment.getProperty("spring.datasource.url", "").contains("localhost")) {
            logger.debug("spring.datasource.url already set; skipping postgres binding override");
            return;
        }

        binding.jdbcUrl().ifPresent(url -> {
            properties.put("spring.datasource.url", url);
            logger.info("Mapped postgres binding '{}' to spring.datasource.url", binding.name());
        });
        binding.username().ifPresent(user -> properties.put("spring.datasource.username", user));
        binding.password().ifPresent(pass -> properties.put("spring.datasource.password", pass));
        properties.putIfAbsent("spring.datasource.driver-class-name", "org.postgresql.Driver");
    }

    private void applyGenaiBindingAsSpringAiProps(Optional<ServiceBindingReader.Binding> bindingOpt,
                                                  String prefix,
                                                  Map<String, Object> properties,
                                                  ConfigurableEnvironment environment) {
        if (bindingOpt.isEmpty()) {
            return;
        }
        ServiceBindingReader.Binding binding = bindingOpt.get();

        String apiKeyProp = prefix + ".api-key";
        String baseUrlProp = prefix + ".base-url";
        String modelProp = prefix + ".options.model";

        if (!environment.containsProperty(apiKeyProp)) {
            binding.genaiApiKey().ifPresent(key -> properties.put(apiKeyProp, key));
        }
        if (!environment.containsProperty(baseUrlProp)) {
            binding.genaiApiBase().ifPresent(base -> properties.put(baseUrlProp, base));
        }
        if (!environment.containsProperty(modelProp)) {
            String model = binding.getFirst("model", "model_name", "model-name", "modelName");
            if (model != null) {
                properties.put(modelProp, model);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
