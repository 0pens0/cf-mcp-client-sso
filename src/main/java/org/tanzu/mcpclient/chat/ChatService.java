package org.tanzu.mcpclient.chat;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.tanzu.mcpclient.mcp.McpClientFactory;
import org.tanzu.mcpclient.memory.MemoryConfiguration;
import org.tanzu.mcpclient.memory.MemoryPreferenceService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final List<String> mcpServiceURLs;
    private final McpClientFactory mcpClientFactory;
    private final ModelDiscoveryService modelDiscoveryService;
    private final MessageChatMemoryAdvisor transientMemoryAdvisor;
    private final VectorStoreChatMemoryAdvisor persistentMemoryAdvisor;
    private final MemoryPreferenceService memoryPreferenceService;
    private final MemoryConfiguration memoryConfiguration;

    @Value("classpath:/prompts/system-prompt.st")
    private Resource systemChatPrompt;

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    /**
     * Updated constructor to support dynamic memory advisor selection.
     * No longer sets a default memory advisor - instead selects per request based on user preference.
     */
    public ChatService(ChatClient.Builder chatClientBuilder,
                       MessageChatMemoryAdvisor transientMemoryAdvisor,
                       VectorStoreChatMemoryAdvisor persistentMemoryAdvisor,
                       MemoryPreferenceService memoryPreferenceService,
                       MemoryConfiguration memoryConfiguration,
                       List<String> mcpServiceURLs,
                       VectorStore vectorStore,
                       McpClientFactory mcpClientFactory,
                       ModelDiscoveryService modelDiscoveryService) {
        // Only add SimpleLoggerAdvisor as default - memory advisor will be added per request
        chatClientBuilder = chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor());
        this.chatClient = chatClientBuilder.build();

        this.transientMemoryAdvisor = transientMemoryAdvisor;
        this.persistentMemoryAdvisor = persistentMemoryAdvisor;
        this.memoryPreferenceService = memoryPreferenceService;
        this.memoryConfiguration = memoryConfiguration;
        this.mcpServiceURLs = mcpServiceURLs;
        this.vectorStore = vectorStore;
        this.mcpClientFactory = mcpClientFactory;
        this.modelDiscoveryService = modelDiscoveryService;
    }

    /**
     * Updated method to handle multiple document IDs with graceful degradation.
     */
    public Flux<String> chatStream(String chat, String conversationId, List<String> documentIds) {
        // Validate chat model availability - this is where graceful degradation happens
        String chatModel = modelDiscoveryService.getChatModelName();
        if (chatModel == null || chatModel.isEmpty()) {
            logger.warn("Chat request attempted but no chat model configured");
            return Flux.error(new IllegalStateException("No chat model configured"));
        }

        try (Stream<McpSyncClient> mcpSyncClients = createAndInitializeMcpClients()) {
            ToolCallbackProvider[] toolCallbackProviders = mcpSyncClients
                    .map(SyncMcpToolCallbackProvider::new)
                    .toArray(ToolCallbackProvider[]::new);

            logger.info("CHAT STREAM REQUEST: conversationID = {}, documentIds = {}", conversationId, documentIds);
            return buildAndExecuteStreamChatRequest(chat, conversationId, documentIds, toolCallbackProviders);
        }
    }

    /**
     * Selects the appropriate memory advisor based on user preference and availability.
     */
    private BaseChatMemoryAdvisor selectMemoryAdvisor(String conversationId) {
        MemoryPreferenceService.MemoryType preference = memoryPreferenceService.getPreference(conversationId);
        
        // Check if persistent memory is available
        boolean persistentAvailable = memoryConfiguration.isPersistentMemoryAvailable(vectorStore);
        
        // If user prefers persistent and it's available, use it
        if (preference == MemoryPreferenceService.MemoryType.PERSISTENT && persistentAvailable) {
            logger.debug("Using PERSISTENT memory advisor for conversation: {}", conversationId);
            return persistentMemoryAdvisor;
        }
        
        // Otherwise, use transient (default or fallback)
        if (preference == MemoryPreferenceService.MemoryType.PERSISTENT && !persistentAvailable) {
            logger.warn("PERSISTENT memory requested but not available, falling back to TRANSIENT for conversation: {}", 
                    conversationId);
        } else {
            logger.debug("Using TRANSIENT memory advisor for conversation: {}", conversationId);
        }
        
        return transientMemoryAdvisor;
    }

    private Stream<McpSyncClient> createAndInitializeMcpClients() {
        return mcpServiceURLs.stream()
                .map(mcpClientFactory::createMcpSyncClient)
                .peek(McpSyncClient::initialize);
    }

    private Flux<String> buildAndExecuteStreamChatRequest(String chat, String conversationId, List<String> documentIds,
                                                          ToolCallbackProvider[] toolCallbackProviders) {

        ChatClient.ChatClientRequestSpec spec = chatClient
                .prompt()
                .user(chat)
                .system(systemChatPrompt);

        // Select and add the appropriate memory advisor based on user preference
        BaseChatMemoryAdvisor selectedMemoryAdvisor = selectMemoryAdvisor(conversationId);
        spec = spec.advisors(selectedMemoryAdvisor);

        // Add conversation context
        spec = spec.advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, conversationId));

        // Add document context if documents are provided
        if (documentIds != null && !documentIds.isEmpty()) {
            logger.debug("Adding document context for documents: {}", documentIds);

            // Use QuestionAnswerAdvisor builder (Spring AI 1.1.0-RC1 API)
            spec = spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).build());
        }

        // Add MCP tools if available
        if (toolCallbackProviders.length > 0) {
            logger.debug("Adding {} MCP tool callback providers", toolCallbackProviders.length);
            spec = spec.toolCallbacks(toolCallbackProviders);
        }

        return spec.stream().content()
                .filter(Objects::nonNull);
    }

    /**
     * Adds document search capabilities using QuestionAnswerAdvisor.
     * The advisor will automatically search the vector store for relevant document chunks.
     */
    private ChatClient.ChatClientRequestSpec addDocumentSearchCapabilities(
            ChatClient.ChatClientRequestSpec spec,
            List<String> documentIds) {

        logger.debug("Adding document context for documents: {}", documentIds);

        // Use QuestionAnswerAdvisor builder (Spring AI 1.1.0-RC1 API)
        return spec.advisors(QuestionAnswerAdvisor.builder(vectorStore).build());
    }
}