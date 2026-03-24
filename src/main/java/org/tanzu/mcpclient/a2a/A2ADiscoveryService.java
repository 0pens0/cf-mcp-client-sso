package org.tanzu.mcpclient.a2a;

import io.pivotal.cfenv.core.CfCredentials;
import io.pivotal.cfenv.core.CfEnv;
import io.pivotal.cfenv.core.CfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for discovering A2A (Agent2Agent) agents from Cloud Foundry service bindings.
 *
 * Service Binding Pattern:
 * cf cups a2a-server -p '{"uri":"https://example.com/.well-known/agent.json"}' -t "a2a"
 */
@Service
public class A2ADiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(A2ADiscoveryService.class);

    public static final String A2A_TAG = "a2a";
    public static final String AGENT_CARD_URI_KEY = "uri";

    private final CfEnv cfEnv;

    public A2ADiscoveryService() {
        this.cfEnv = new CfEnv();
        logger.debug("A2ADiscoveryService initialized");
    }

    public List<String> getAgentCardUris() {
        try {
            List<String> uris = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(this::extractAgentCardUri)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A agent card URIs: {}", uris.size(), uris);
            return uris;
        } catch (Exception e) {
            logger.warn("Error getting A2A agent card URIs: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> getA2AServiceNames() {
        try {
            List<String> names = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(CfService::getName)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A services: {}", names.size(), names);
            return names;
        } catch (Exception e) {
            logger.warn("Error getting A2A service names: {}", e.getMessage());
            return List.of();
        }
    }

    public List<A2AServiceInfo> getA2AServices() {
        try {
            List<A2AServiceInfo> services = cfEnv.findAllServices().stream()
                    .filter(this::isA2AService)
                    .map(this::extractServiceInfo)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            logger.debug("Found {} A2A services", services.size());
            return services;
        } catch (Exception e) {
            logger.warn("Error getting A2A services: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isA2AService(CfService service) {
        return service.existsByTagIgnoreCase(A2A_TAG);
    }

    private String extractAgentCardUri(CfService service) {
        CfCredentials credentials = service.getCredentials();
        if (credentials == null) {
            logger.warn("Service '{}' has no credentials", service.getName());
            return null;
        }

        String uri = credentials.getString(AGENT_CARD_URI_KEY);
        if (!isValidUri(uri)) {
            logger.warn("Service '{}' has invalid or missing '{}' credential",
                service.getName(), AGENT_CARD_URI_KEY);
            return null;
        }

        logger.debug("Found A2A service '{}' with URI: {}", service.getName(), uri);
        return uri;
    }

    private A2AServiceInfo extractServiceInfo(CfService service) {
        String uri = extractAgentCardUri(service);
        if (uri == null) {
            return null;
        }
        return new A2AServiceInfo(service.getName(), uri);
    }

    private boolean isValidUri(String uri) {
        return uri != null && !uri.trim().isEmpty();
    }

    public record A2AServiceInfo(
        String serviceName,
        String agentCardUri
    ) {}
}
