package org.tanzu.mcpclient.bindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads Kubernetes Service Binding directories (SERVICE_BINDING_ROOT, default {@code /bindings}).
 * Compatible with the Service Binding Specification for Kubernetes and Tanzu Platform bindings.
 */
public final class ServiceBindingReader {

    private static final Logger logger = LoggerFactory.getLogger(ServiceBindingReader.class);

    public static final String SERVICE_BINDING_ROOT_PROPERTY = "SERVICE_BINDING_ROOT";
    public static final String DEFAULT_BINDING_ROOT = "/bindings";

    private ServiceBindingReader() {
    }

    public static Path bindingRoot() {
        String root = System.getenv(SERVICE_BINDING_ROOT_PROPERTY);
        if (root == null || root.isBlank()) {
            root = System.getProperty(SERVICE_BINDING_ROOT_PROPERTY, DEFAULT_BINDING_ROOT);
        }
        return Path.of(root);
    }

    /**
     * Returns all binding directories under the binding root that contain a {@code type} file.
     */
    public static List<Binding> readAll() {
        Path root = bindingRoot();
        if (!Files.isDirectory(root)) {
            logger.debug("Service binding root {} does not exist or is not a directory", root);
            return List.of();
        }

        List<Binding> bindings = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry)) {
                    continue;
                }
                Map<String, String> entries = readBindingEntries(entry);
                if (entries.isEmpty()) {
                    continue;
                }
                String type = entries.getOrDefault("type", "");
                String name = entry.getFileName().toString();
                bindings.add(new Binding(name, type, entries));
                logger.info("Discovered service binding name='{}' type='{}' keys={}",
                        name, type, entries.keySet());
            }
        } catch (IOException e) {
            logger.warn("Failed to read service bindings from {}: {}", root, e.getMessage());
        }
        return bindings;
    }

    public static List<Binding> findByTypeContains(String typeFragment) {
        String needle = typeFragment.toLowerCase(Locale.ROOT);
        return readAll().stream()
                .filter(binding -> binding.type().toLowerCase(Locale.ROOT).contains(needle)
                        || binding.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    public static Optional<Binding> findFirstPostgres() {
        return readAll().stream()
                .filter(Binding::isPostgres)
                .findFirst();
    }

    public static Optional<Binding> findFirstGenaiChat() {
        return readAll().stream()
                .filter(Binding::isGenai)
                .filter(binding -> !binding.isEmbedding())
                .findFirst()
                .or(() -> readAll().stream().filter(Binding::isGenai).findFirst());
    }

    public static Optional<Binding> findFirstGenaiEmbedding() {
        return readAll().stream()
                .filter(Binding::isGenai)
                .filter(Binding::isEmbedding)
                .findFirst()
                .or(() -> readAll().stream()
                        .filter(binding -> binding.name().toLowerCase(Locale.ROOT).contains("embed"))
                        .filter(Binding::isGenai)
                        .findFirst());
    }

    private static Map<String, String> readBindingEntries(Path bindingDir) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bindingDir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String key = file.getFileName().toString();
                // Skip metadata-only hidden files commonly present in projected volumes
                if (key.startsWith(".")) {
                    continue;
                }
                String value = Files.readString(file, StandardCharsets.UTF_8).trim();
                entries.put(key, value);
            }
        }
        return entries;
    }

    /**
     * Immutable view of a single service binding directory.
     */
    public record Binding(String name, String type, Map<String, String> entries) {

        public String get(String key) {
            return entries.get(key);
        }

        public String getFirst(String... keys) {
            for (String key : keys) {
                String value = entries.get(key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        public boolean isPostgres() {
            String lowerType = type.toLowerCase(Locale.ROOT);
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerType.contains("postgres")
                    || lowerType.contains("postgresql")
                    || lowerName.contains("postgres")
                    || entries.containsKey("jdbc-url")
                    || entries.containsKey("jdbcUrl")
                    || (entries.containsKey("uri") && entries.get("uri").startsWith("postgres"));
        }

        public boolean isGenai() {
            String lowerType = type.toLowerCase(Locale.ROOT);
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerType.contains("genai")
                    || lowerType.contains("openai")
                    || lowerName.contains("genai")
                    || entries.containsKey("config_url")
                    || entries.containsKey("api_base")
                    || entries.containsKey("api-base")
                    || (entries.containsKey("uri") && entries.containsKey("api-key"));
        }

        public boolean isEmbedding() {
            String lowerType = type.toLowerCase(Locale.ROOT);
            String lowerName = name.toLowerCase(Locale.ROOT);
            String modelCap = Optional.ofNullable(getFirst("model_capabilities", "model-capabilities", "capability"))
                    .orElse("")
                    .toLowerCase(Locale.ROOT);
            return lowerName.contains("embed")
                    || lowerType.contains("embed")
                    || modelCap.contains("embed");
        }

        /**
         * Builds a JDBC URL from binding fields.
         */
        public Optional<String> jdbcUrl() {
            String explicit = getFirst("jdbc-url", "jdbcUrl", "jdbc_url");
            if (explicit != null) {
                return Optional.of(explicit);
            }
            String uri = getFirst("uri", "url");
            if (uri != null && uri.startsWith("jdbc:")) {
                return Optional.of(uri);
            }
            if (uri != null && uri.startsWith("postgres")) {
                // Convert postgres:// or postgresql:// to jdbc:postgresql://
                String jdbc = uri.replaceFirst("^postgres(ql)?://", "jdbc:postgresql://");
                return Optional.of(jdbc);
            }
            String host = getFirst("host", "hostname");
            String port = getFirst("port");
            String database = getFirst("database", "db", "name");
            if (host != null && database != null) {
                String effectivePort = (port == null || port.isBlank()) ? "5432" : port;
                return Optional.of("jdbc:postgresql://" + host + ":" + effectivePort + "/" + database);
            }
            return Optional.empty();
        }

        public Optional<String> username() {
            return Optional.ofNullable(getFirst("username", "user", "access-key", "access_key"));
        }

        public Optional<String> password() {
            return Optional.ofNullable(getFirst("password", "passwd", "secret", "access-secret", "access_secret"));
        }

        public Optional<String> genaiConfigUrl() {
            return Optional.ofNullable(getFirst("config_url", "config-url", "configUrl"));
        }

        public Optional<String> genaiApiKey() {
            return Optional.ofNullable(getFirst("api_key", "api-key", "apiKey", "key"));
        }

        public Optional<String> genaiApiBase() {
            String base = getFirst("api_base", "api-base", "apiBase", "base_url", "base-url", "baseUrl", "uri", "url");
            return Optional.ofNullable(base);
        }
    }
}
