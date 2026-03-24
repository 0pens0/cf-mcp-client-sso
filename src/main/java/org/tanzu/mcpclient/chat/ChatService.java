package org.tanzu.mcpclient.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.document.DocumentService;
import org.tanzu.mcpclient.mcp.McpServerService;
import org.tanzu.mcpclient.mcp.McpToolCallbackCacheService;
import org.tanzu.mcpclient.memory.MemoryConfiguration;
import org.tanzu.mcpclient.memory.MemoryPreferenceService;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final List<McpServerService> mcpServerServices;
    private final McpToolCallbackCacheService toolCallbackCacheService;
    private final ModelDiscoveryService modelDiscoveryService;
    private final MessageChatMemoryAdvisor transientMemoryAdvisor;
    private final VectorStoreChatMemoryAdvisor persistentMemoryAdvisor;
    private final MemoryPreferenceService memoryPreferenceService;
    private final MemoryConfiguration memoryConfiguration;

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemChatPrompt;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public ChatService(ChatClient.Builder chatClientBuilder,
                       MessageChatMemoryAdvisor transientMemoryAdvisor,
                       VectorStoreChatMemoryAdvisor persistentMemoryAdvisor,
                       MemoryPreferenceService memoryPreferenceService,
                       MemoryConfiguration memoryConfiguration,
                       List<McpServerService> mcpServerServices,
                       VectorStore vectorStore,
                       McpToolCallbackCacheService toolCallbackCacheService,
                       ModelDiscoveryService modelDiscoveryService) {
        chatClientBuilder = chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor());
        this.chatClient = chatClientBuilder.build();

        this.transientMemoryAdvisor = transientMemoryAdvisor;
        this.persistentMemoryAdvisor = persistentMemoryAdvisor;
        this.memoryPreferenceService = memoryPreferenceService;
        this.memoryConfiguration = memoryConfiguration;
        this.mcpServerServices = mcpServerServices;
        this.vectorStore = vectorStore;
        this.toolCallbackCacheService = toolCallbackCacheService;
        this.modelDiscoveryService = modelDiscoveryService;
    }

    public Flux<String> chatStream(String chat, String conversationId, List<String> documentIds) {
        String chatModel = modelDiscoveryService.getChatModelName();
        if (chatModel == null || chatModel.isEmpty()) {
            logger.warn("Chat request attempted but no chat model configured");
            return Flux.error(new IllegalStateException("No chat model configured"));
        }

        ToolCallbackProvider[] toolCallbackProviders = toolCallbackCacheService.getToolCallbackProviders();

        logger.info("CHAT STREAM REQUEST: conversationID = {}, documentIds = {}, cached tools = {}",
                conversationId, documentIds, toolCallbackProviders.length);

        return buildAndExecuteStreamChatRequest(chat, conversationId, documentIds, toolCallbackProviders);
    }

    private BaseChatMemoryAdvisor selectMemoryAdvisor(String conversationId) {
        MemoryPreferenceService.MemoryType preference = memoryPreferenceService.getPreference(conversationId);

        boolean persistentAvailable = memoryConfiguration.isPersistentMemoryAvailable(vectorStore);

        if (preference == MemoryPreferenceService.MemoryType.PERSISTENT && persistentAvailable) {
            logger.debug("Using PERSISTENT memory advisor for conversation: {}", conversationId);
            return persistentMemoryAdvisor;
        }

        if (preference == MemoryPreferenceService.MemoryType.PERSISTENT && !persistentAvailable) {
            logger.warn("PERSISTENT memory requested but not available, falling back to TRANSIENT for conversation: {}",
                    conversationId);
        } else {
            logger.debug("Using TRANSIENT memory advisor for conversation: {}", conversationId);
        }

        return transientMemoryAdvisor;
    }

    private Flux<String> buildAndExecuteStreamChatRequest(String chat, String conversationId,
                                                          List<String> documentIds,
                                                          ToolCallbackProvider[] toolCallbackProviders) {

        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .user(chat)
                .system(systemChatPrompt);

        BaseChatMemoryAdvisor selectedMemoryAdvisor = selectMemoryAdvisor(conversationId);
        spec = spec.advisors(selectedMemoryAdvisor);

        spec = spec.advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, conversationId));

        if (documentIds != null && !documentIds.isEmpty()) {
            logger.debug("Adding document context for documents: {}", documentIds);

            String filterExpression = buildDocumentFilterExpression(documentIds);
            logger.debug("Using document filter expression: {}", filterExpression);

            SearchRequest searchRequest = SearchRequest.builder()
                    .filterExpression(filterExpression)
                    .build();

            spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(searchRequest)
                    .build());
        }

        if (toolCallbackProviders.length > 0) {
            logger.debug("Adding {} MCP tool callback providers", toolCallbackProviders.length);
            spec = spec.toolCallbacks(toolCallbackProviders);
        }

        return spec.stream().content();
    }

    public List<McpServerService> getMcpServerServices() {
        return List.copyOf(mcpServerServices);
    }

    private String buildDocumentFilterExpression(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "";
        }

        List<String> validDocumentIds = documentIds.stream()
                .filter(id -> id != null && !id.trim().isEmpty())
                .toList();

        if (validDocumentIds.isEmpty()) {
            return "";
        }

        if (validDocumentIds.size() == 1) {
            return DocumentService.DOCUMENT_ID + " == '" + validDocumentIds.get(0) + "'";
        }

        return validDocumentIds.stream()
                .map(docId -> DocumentService.DOCUMENT_ID + " == '" + docId + "'")
                .collect(Collectors.joining(" OR "));
    }
}
