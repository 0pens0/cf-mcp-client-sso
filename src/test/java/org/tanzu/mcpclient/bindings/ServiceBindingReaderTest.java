package org.tanzu.mcpclient.bindings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceBindingReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPostgresAndGenaiBindings() throws Exception {
        Path postgres = tempDir.resolve("postgres-vector");
        Files.createDirectories(postgres);
        Files.writeString(postgres.resolve("type"), "postgresql");
        Files.writeString(postgres.resolve("host"), "pg.example");
        Files.writeString(postgres.resolve("port"), "5432");
        Files.writeString(postgres.resolve("database"), "vectordb");
        Files.writeString(postgres.resolve("username"), "pguser");
        Files.writeString(postgres.resolve("password"), "secret");

        Path chat = tempDir.resolve("genai-chat");
        Files.createDirectories(chat);
        Files.writeString(chat.resolve("type"), "genai");
        Files.writeString(chat.resolve("config_url"), "https://genai.example/chat/config/v1/endpoint");
        Files.writeString(chat.resolve("api_key"), "chat-key");
        Files.writeString(chat.resolve("api_base"), "https://genai.example/chat");

        Path embed = tempDir.resolve("genai-embed");
        Files.createDirectories(embed);
        Files.writeString(embed.resolve("type"), "genai");
        Files.writeString(embed.resolve("model_capabilities"), "EMBEDDING");
        Files.writeString(embed.resolve("config_url"), "https://genai.example/embed/config/v1/endpoint");
        Files.writeString(embed.resolve("api_key"), "embed-key");
        Files.writeString(embed.resolve("api_base"), "https://genai.example/embed");

        System.setProperty(ServiceBindingReader.SERVICE_BINDING_ROOT_PROPERTY, tempDir.toString());
        try {
            List<ServiceBindingReader.Binding> all = ServiceBindingReader.readAll();
            assertEquals(3, all.size());

            Optional<ServiceBindingReader.Binding> pg = ServiceBindingReader.findFirstPostgres();
            assertTrue(pg.isPresent());
            assertEquals("jdbc:postgresql://pg.example:5432/vectordb", pg.get().jdbcUrl().orElseThrow());
            assertEquals("pguser", pg.get().username().orElseThrow());

            Optional<ServiceBindingReader.Binding> chatBinding = ServiceBindingReader.findFirstGenaiChat();
            assertTrue(chatBinding.isPresent());
            assertEquals("chat-key", chatBinding.get().genaiApiKey().orElseThrow());

            Optional<ServiceBindingReader.Binding> embedBinding = ServiceBindingReader.findFirstGenaiEmbedding();
            assertTrue(embedBinding.isPresent());
            assertEquals("embed-key", embedBinding.get().genaiApiKey().orElseThrow());
        } finally {
            System.clearProperty(ServiceBindingReader.SERVICE_BINDING_ROOT_PROPERTY);
        }
    }
}
