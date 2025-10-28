package org.tanzu.mcpclient.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tanzu.mcpclient.model.ModelDiscoveryService;
import org.tanzu.mcpclient.vectorstore.VectorStoreConfiguration;

@Configuration
public class MemoryConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfiguration.class);

    private final ModelDiscoveryService modelDiscoveryService;

    public MemoryConfiguration(ModelDiscoveryService modelDiscoveryService) {
        this.modelDiscoveryService = modelDiscoveryService;
    }

    @Bean
    public BaseChatMemoryAdvisor chatMemoryAdvisor(ChatMemoryRepository chatMemoryRepository, VectorStore vectorStore) {
        // Always use MessageChatMemoryAdvisor to avoid embedding model issues
        // The vector store is used for document search, not for chat memory
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}