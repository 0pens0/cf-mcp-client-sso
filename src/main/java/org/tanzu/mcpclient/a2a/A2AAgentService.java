package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing communication with a single A2A agent.
 * Handles agent card fetching, message sending via JSON-RPC, and health status tracking.
 */
public class A2AAgentService {
    private static final Logger logger = LoggerFactory.getLogger(A2AAgentService.class);

    private final String serviceName;
    private final String agentCardUri;
    private final RestClient restClient;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private AgentCard agentCard;
    private boolean healthy;
    private String errorMessage;

    public A2AAgentService(String serviceName, String agentCardUri,
                          RestClient.Builder restClientBuilder,
                          WebClient.Builder webClientBuilder,
                          ObjectMapper objectMapper) {
        this.serviceName = serviceName;
        this.agentCardUri = agentCardUri;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
        this.webClient = webClientBuilder.build();
        this.healthy = false;
        this.errorMessage = null;

        initializeAgentCard();
    }

    private void initializeAgentCard() {
        logger.debug("[A2A] [{}] [INIT] Fetching agent card from: {}", serviceName, agentCardUri);

        try {
            String agentCardJson = restClient.get()
                    .uri(agentCardUri)
                    .retrieve()
                    .body(String.class);

            if (agentCardJson == null || agentCardJson.trim().isEmpty()) {
                this.errorMessage = "Agent card response is empty";
                this.healthy = false;
                logger.warn("[A2A] [{}] [INIT] {}", serviceName, errorMessage);
                return;
            }

            this.agentCard = objectMapper.readValue(agentCardJson, AgentCard.class);
            this.healthy = true;
            this.errorMessage = null;

            logger.info("[A2A] [{}] [INIT] Successfully loaded agent card for '{}' (version: {}, protocol: {})",
                    serviceName, agentCard.name(), agentCard.version(), agentCard.protocolVersion());

        } catch (Exception e) {
            this.errorMessage = "Failed to fetch agent card: " + e.getMessage();
            this.healthy = false;
            logger.error("[A2A] [{}] [INIT] {}", serviceName, errorMessage, e);
        }
    }

    public A2AModels.JsonRpcResponse sendMessage(String messageText) {
        if (!healthy) {
            throw new IllegalStateException("Agent is unhealthy: " + errorMessage);
        }

        logger.debug("[A2A] [{}] [SEND] Sending message to agent", agentCard.name());

        try {
            A2AModels.TextPart textPart = new A2AModels.TextPart(messageText);
            List<A2AModels.Part> parts = List.of(textPart);
            String messageId = UUID.randomUUID().toString();
            A2AModels.Message message = new A2AModels.Message("user", parts, messageId);

            A2AModels.MessageSendConfiguration configuration = new A2AModels.MessageSendConfiguration(
                    List.of("text"), true);

            Map<String, Object> params = Map.of("message", message, "configuration", configuration);

            A2AModels.JsonRpcRequest request = new A2AModels.JsonRpcRequest(1, "message/send", params);
            String requestJson = objectMapper.writeValueAsString(request);
            logger.debug("[A2A] [{}] [SEND] Request payload: {}", agentCard.name(), requestJson);

            long startTime = System.currentTimeMillis();
            String responseJson = restClient.post()
                    .uri(agentCard.url())
                    .header("Content-Type", "application/json")
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            long duration = System.currentTimeMillis() - startTime;

            A2AModels.JsonRpcResponse response = objectMapper.readValue(responseJson, A2AModels.JsonRpcResponse.class);

            if (response.error() != null) {
                logger.error("[A2A] [{}] [SEND] Agent returned error: {} - {}",
                        agentCard.name(), response.error().code(), response.error().message());
                throw new RuntimeException("Agent error: " + response.error().message());
            }

            logger.info("[A2A] [{}] [RESPONSE] Received response in {}ms", agentCard.name(), duration);
            return response;

        } catch (Exception e) {
            logger.error("[A2A] [{}] [SEND] Failed to send message: {}", agentCard.name(), e.getMessage(), e);
            throw new RuntimeException("Failed to send message to agent: " + e.getMessage(), e);
        }
    }

    public Flux<A2AModels.SendStreamingMessageResponse> sendMessageStreaming(String messageText) {
        if (!healthy) {
            return Flux.error(new IllegalStateException("Agent is unhealthy: " + errorMessage));
        }

        logger.debug("[A2A] [{}] [STREAM] Starting streaming message to agent", agentCard.name());

        try {
            A2AModels.TextPart textPart = new A2AModels.TextPart(messageText);
            List<A2AModels.Part> parts = List.of(textPart);
            String messageId = UUID.randomUUID().toString();
            A2AModels.Message message = new A2AModels.Message("user", parts, messageId);

            A2AModels.MessageSendConfiguration configuration = new A2AModels.MessageSendConfiguration(
                    List.of("text"), false);

            Map<String, Object> params = Map.of("message", message, "configuration", configuration);
            A2AModels.JsonRpcRequest request = new A2AModels.JsonRpcRequest(1, "message/stream", params);
            String requestJson = objectMapper.writeValueAsString(request);
            logger.debug("[A2A] [{}] [STREAM] Request payload: {}", agentCard.name(), requestJson);

            return webClient.post()
                    .uri(agentCard.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .mapNotNull(event -> {
                        try {
                            String data = event.data();
                            if (data == null || data.isEmpty()) {
                                return null;
                            }

                            logger.debug("[A2A] [{}] [STREAM] Received SSE event: {}", agentCard.name(), data);

                            A2AModels.JsonRpcResponse jsonRpcResponse =
                                    objectMapper.readValue(data, A2AModels.JsonRpcResponse.class);

                            if (jsonRpcResponse.error() != null) {
                                logger.error("[A2A] [{}] [STREAM] Agent returned error: {} - {}",
                                        agentCard.name(), jsonRpcResponse.error().code(),
                                        jsonRpcResponse.error().message());
                                throw new RuntimeException("Agent error: " + jsonRpcResponse.error().message());
                            }

                            Object result = jsonRpcResponse.result();
                            if (result == null) {
                                logger.warn("[A2A] [{}] [STREAM] Received null result in JSON-RPC response",
                                        agentCard.name());
                                return null;
                            }

                            if (result instanceof Map<?, ?> resultMap) {
                                logger.debug("[A2A] [{}] [STREAM] Result keys: {}", agentCard.name(),
                                        resultMap.keySet());

                                String resultJson = objectMapper.writeValueAsString(resultMap);
                                A2AModels.SendStreamingMessageResponse response =
                                        objectMapper.readValue(resultJson,
                                                A2AModels.SendStreamingMessageResponse.class);

                                if (response.status() == null) {
                                    logger.warn("[A2A] [{}] [STREAM] Parsed response has null status. Raw: {}",
                                            agentCard.name(), resultJson);
                                } else {
                                    logger.debug("[A2A] [{}] [STREAM] Parsed status: taskId={}, state={}, final={}",
                                            agentCard.name(), response.taskId(),
                                            response.status().state(), response.finalStatus());
                                }

                                return response;
                            }

                            logger.warn("[A2A] [{}] [STREAM] Result is not a Map, type: {}",
                                    agentCard.name(), result.getClass().getName());
                            return null;

                        } catch (Exception e) {
                            logger.error("[A2A] [{}] [STREAM] Error parsing SSE event: {}",
                                    agentCard.name(), e.getMessage(), e);
                            throw new RuntimeException("Error parsing streaming response: " + e.getMessage(), e);
                        }
                    })
                    .doOnComplete(() -> logger.info("[A2A] [{}] [STREAM] Streaming completed", agentCard.name()))
                    .doOnError(error -> logger.error("[A2A] [{}] [STREAM] Streaming error: {}",
                            agentCard.name(), error.getMessage(), error));

        } catch (Exception e) {
            logger.error("[A2A] [{}] [STREAM] Failed to initiate streaming: {}", agentCard.name(),
                    e.getMessage(), e);
            return Flux.error(new RuntimeException(
                    "Failed to send streaming message to agent: " + e.getMessage(), e));
        }
    }

    public String getServiceName() { return serviceName; }
    public String getAgentCardUri() { return agentCardUri; }
    public AgentCard getAgentCard() { return agentCard; }
    public boolean isHealthy() { return healthy; }
    public String getErrorMessage() { return errorMessage; }
}
