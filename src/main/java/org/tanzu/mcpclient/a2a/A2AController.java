package org.tanzu.mcpclient.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST controller for A2A (Agent to Agent) operations.
 * Provides endpoints for sending messages to A2A agents and receiving responses.
 */
@RestController
@RequestMapping("/a2a")
public class A2AController {

    private static final Logger logger = LoggerFactory.getLogger(A2AController.class);

    private final A2AConfiguration a2aConfiguration;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public A2AController(A2AConfiguration a2aConfiguration, ObjectMapper objectMapper) {
        this.a2aConfiguration = a2aConfiguration;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/send-message")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        logger.debug("Received send-message request for service: {}", request.serviceName());

        try {
            A2AAgentService agentService = a2aConfiguration.getAgentServices().stream()
                    .filter(service -> service.getServiceName().equals(request.serviceName()))
                    .findFirst()
                    .orElse(null);

            if (agentService == null) {
                logger.warn("Agent service not found: {}", request.serviceName());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new SendMessageResponse(false, null, null,
                                "Agent service not found: " + request.serviceName()));
            }

            if (!agentService.isHealthy()) {
                logger.warn("Agent service is unhealthy: {}", request.serviceName());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new SendMessageResponse(false,
                                agentService.getAgentCard() != null ? agentService.getAgentCard().name() : "Unknown",
                                null, "Agent is unhealthy: " + agentService.getErrorMessage()));
            }

            A2AModels.JsonRpcResponse response = agentService.sendMessage(request.messageText());
            String responseText = extractResponseText(response.result());

            logger.info("Successfully sent message to agent {} and received response",
                    agentService.getAgentCard().name());

            return ResponseEntity.ok(new SendMessageResponse(true,
                    agentService.getAgentCard().name(), responseText, null));

        } catch (IllegalStateException e) {
            logger.error("Agent is unhealthy: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SendMessageResponse(false, null, null,
                            "Agent is unhealthy: " + e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to communicate with agent: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SendMessageResponse(false, null, null,
                            "Failed to communicate with agent: " + e.getMessage()));
        }
    }

    private String extractResponseText(Object result) {
        if (result == null) {
            return "";
        }

        try {
            A2AModels.Task task = objectMapper.convertValue(result, A2AModels.Task.class);
            if (task != null && task.status() != null && task.status().message() != null) {
                return extractTextFromParts(task.status().message().parts());
            }
        } catch (Exception e) {
            logger.debug("Result is not a Task, trying Message");
        }

        try {
            A2AModels.Message message = objectMapper.convertValue(result, A2AModels.Message.class);
            if (message != null && message.parts() != null) {
                return extractTextFromParts(message.parts());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse result as Task or Message: {}", e.getMessage());
        }

        return result.toString();
    }

    private String extractTextFromParts(List<A2AModels.Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        StringBuilder textBuilder = new StringBuilder();
        for (A2AModels.Part part : parts) {
            if (part instanceof A2AModels.TextPart textPart) {
                if (!textBuilder.isEmpty()) {
                    textBuilder.append("\n");
                }
                textBuilder.append(textPart.text());
            }
        }

        return textBuilder.toString();
    }

    @GetMapping(value = "/stream-message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam("serviceName") String serviceName,
                                    @RequestParam("messageText") String messageText) {
        logger.debug("Received stream-message request for service: {}", serviceName);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executor.execute(() -> {
            try {
                A2AAgentService agentService = a2aConfiguration.getAgentServices().stream()
                        .filter(service -> service.getServiceName().equals(serviceName))
                        .findFirst()
                        .orElse(null);

                if (agentService == null) {
                    logger.warn("Agent service not found: {}", serviceName);
                    sendError(emitter, "Agent service not found: " + serviceName);
                    return;
                }

                if (!agentService.isHealthy()) {
                    logger.warn("Agent service is unhealthy: {}", serviceName);
                    sendError(emitter, "Agent is unhealthy: " + agentService.getErrorMessage());
                    return;
                }

                String agentName = agentService.getAgentCard().name();

                agentService.sendMessageStreaming(messageText)
                        .subscribe(
                                response -> {
                                    try {
                                        A2AModels.StatusUpdate statusUpdate =
                                                buildStatusUpdate(response, agentName);

                                        String jsonData = objectMapper.writeValueAsString(statusUpdate);
                                        String eventType = statusUpdate.type();

                                        emitter.send(SseEmitter.event()
                                                .data(jsonData)
                                                .name(eventType));

                                        logger.debug("[A2A] [{}] Sent SSE event: type={}, state={}",
                                                agentName, eventType, statusUpdate.state());

                                    } catch (IOException e) {
                                        logger.error("[A2A] [{}] Error sending SSE event", agentName, e);
                                        emitter.completeWithError(e);
                                    }
                                },
                                error -> {
                                    logger.error("[A2A] [{}] Streaming error: {}",
                                            agentName, error.getMessage(), error);
                                    sendError(emitter, "Streaming error: " + error.getMessage());
                                },
                                () -> {
                                    try {
                                        logger.debug("[A2A] [{}] Streaming completed", agentName);
                                        emitter.send(SseEmitter.event().name("close").data(""));
                                        emitter.complete();
                                    } catch (IOException e) {
                                        emitter.completeWithError(e);
                                    }
                                }
                        );

            } catch (Exception e) {
                logger.error("Failed to initiate streaming: {}", e.getMessage(), e);
                sendError(emitter, "Failed to initiate streaming: " + e.getMessage());
            }
        });

        return emitter;
    }

    private A2AModels.StatusUpdate buildStatusUpdate(A2AModels.SendStreamingMessageResponse response,
                                                      String agentName) {
        if (response.status() == null) {
            logger.warn("[A2A] [{}] Received streaming response with null status: taskId={}, kind={}",
                    agentName, response.taskId(), response.kind());
            return new A2AModels.StatusUpdate("status", "unknown", "Status is null", null, agentName);
        }

        A2AModels.TaskStatus status = response.status();
        String state = status.state();
        String statusMessage = null;
        String responseText = null;

        logger.debug("[A2A] [{}] Building status update: taskId={}, state={}, final={}, hasMessage={}",
                agentName, response.taskId(), state, response.finalStatus(), status.message() != null);

        boolean isFinalState = Boolean.TRUE.equals(response.finalStatus())
                || "completed".equals(state)
                || "failed".equals(state)
                || "rejected".equals(state)
                || "canceled".equals(state);

        String eventType = isFinalState ? "result" : "status";

        if (status.message() != null && status.message().parts() != null) {
            List<A2AModels.Part> parts = status.message().parts();
            String text = extractTextFromParts(parts);

            if (isFinalState) {
                responseText = text;
                logger.info("[A2A] [{}] Final result received: state={}, textLength={}",
                        agentName, state, text != null ? text.length() : 0);
            } else {
                statusMessage = text;
                logger.debug("[A2A] [{}] Status message: {}", agentName, statusMessage);
            }
        } else {
            logger.debug("[A2A] [{}] No message in status for state={}", agentName, state);
        }

        return new A2AModels.StatusUpdate(eventType, state, statusMessage, responseText, agentName);
    }

    private void sendError(SseEmitter emitter, String errorMessage) {
        try {
            A2AModels.StatusUpdate errorUpdate = new A2AModels.StatusUpdate(
                    "error", "failed", null, errorMessage, null);
            String errorJson = objectMapper.writeValueAsString(errorUpdate);
            emitter.send(SseEmitter.event().data(errorJson).name("error"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public record SendMessageRequest(String serviceName, String messageText) {}

    public record SendMessageResponse(
            boolean success,
            String agentName,
            String responseText,
            String error
    ) {}
}
