package org.tanzu.mcpclient.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolsChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Utility factory for creating MCP clients with consistent configuration.
 * Supports SSE, Streamable HTTP, and event-driven tool callback caching
 * via McpToolsChangedEvent publishing.
 */
@Component
public class McpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(10);

    private final SSLContext sslContext;
    private final ApplicationEventPublisher eventPublisher;

    public McpClientFactory(SSLContext sslContext, ApplicationEventPublisher eventPublisher) {
        this.sslContext = sslContext;
        this.eventPublisher = eventPublisher;
    }

    public McpSyncClient createMcpSyncClient(String serverUrl) {
        return createMcpSyncClient(serverUrl, DEFAULT_CONNECT_TIMEOUT, DEFAULT_REQUEST_TIMEOUT);
    }

    public McpSyncClient createHealthCheckClient(String serverUrl) {
        return createMcpSyncClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT);
    }

    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol) {
        return createHealthCheckClient(serverUrl, protocol, Map.of());
    }

    public McpSyncClient createHealthCheckClient(String serverUrl, ProtocolType protocol, Map<String, String> headers) {
        return switch (protocol) {
            case ProtocolType.StreamableHttp streamableHttp ->
                    createStreamableClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.SSE sse ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
            case ProtocolType.Legacy legacy ->
                    createSseClient(serverUrl, HEALTH_CHECK_TIMEOUT, HEALTH_CHECK_TIMEOUT, headers);
        };
    }

    /** @deprecated Use createSseClient for clarity */
    @Deprecated
    public McpSyncClient createMcpSyncClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createSseClient(serverUrl, connectTimeout, requestTimeout);
    }

    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createSseClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    public McpSyncClient createSseClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout, headers);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout) {
        return createStreamableClient(serverUrl, connectTimeout, requestTimeout, Map.of());
    }

    public McpSyncClient createStreamableClient(String serverUrl, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
        HttpClient.Builder clientBuilder = createHttpClientBuilder(connectTimeout, headers);

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(serverUrl)
                .clientBuilder(clientBuilder)
                .jsonMapper(new JacksonMcpJsonMapper(new ObjectMapper()))
                .resumableStreams(true)
                .build();

        return McpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .toolsChangeConsumer(tools -> {
                    logger.info("MCP server {} tools changed, publishing event (new tool count: {})",
                            serverUrl, tools.size());
                    eventPublisher.publishEvent(new McpToolsChangedEvent(serverUrl, tools));
                })
                .build();
    }

    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout) {
        return createHttpClientBuilder(connectTimeout, Map.of());
    }

    private HttpClient.Builder createHttpClientBuilder(Duration connectTimeout, Map<String, String> headers) {
        HttpClient.Builder baseBuilder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(connectTimeout);

        if (headers.isEmpty()) {
            return baseBuilder;
        }

        return new HttpClient.Builder() {
            @Override
            public HttpClient build() {
                return createHttpClientWithHeaders(baseBuilder.build(), headers);
            }

            @Override
            public HttpClient.Builder sslContext(SSLContext sslContext) {
                baseBuilder.sslContext(sslContext);
                return this;
            }

            @Override
            public HttpClient.Builder sslParameters(javax.net.ssl.SSLParameters sslParameters) {
                baseBuilder.sslParameters(sslParameters);
                return this;
            }

            @Override
            public HttpClient.Builder executor(java.util.concurrent.Executor executor) {
                baseBuilder.executor(executor);
                return this;
            }

            @Override
            public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
                baseBuilder.followRedirects(policy);
                return this;
            }

            @Override
            public HttpClient.Builder version(HttpClient.Version version) {
                baseBuilder.version(version);
                return this;
            }

            @Override
            public HttpClient.Builder priority(int priority) {
                baseBuilder.priority(priority);
                return this;
            }

            @Override
            public HttpClient.Builder proxy(java.net.ProxySelector proxySelector) {
                baseBuilder.proxy(proxySelector);
                return this;
            }

            @Override
            public HttpClient.Builder authenticator(java.net.Authenticator authenticator) {
                baseBuilder.authenticator(authenticator);
                return this;
            }

            @Override
            public HttpClient.Builder connectTimeout(Duration duration) {
                baseBuilder.connectTimeout(duration);
                return this;
            }

            @Override
            public HttpClient.Builder cookieHandler(java.net.CookieHandler cookieHandler) {
                baseBuilder.cookieHandler(cookieHandler);
                return this;
            }
        };
    }

    private HttpClient createHttpClientWithHeaders(HttpClient baseClient, Map<String, String> headers) {
        return new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                    throws java.io.IOException, InterruptedException {
                return baseClient.send(addHeadersToRequest(request, headers), responseBodyHandler);
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler) {
                return baseClient.sendAsync(addHeadersToRequest(request, headers), responseBodyHandler);
            }

            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler,
                    HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return baseClient.sendAsync(addHeadersToRequest(request, headers), responseBodyHandler, pushPromiseHandler);
            }

            @Override public HttpClient.Version version() { return baseClient.version(); }
            @Override public java.util.Optional<java.net.ProxySelector> proxy() { return baseClient.proxy(); }
            @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return baseClient.cookieHandler(); }
            @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return baseClient.executor(); }
            @Override public java.util.Optional<java.net.Authenticator> authenticator() { return baseClient.authenticator(); }
            @Override public HttpClient.Redirect followRedirects() { return baseClient.followRedirects(); }
            @Override public javax.net.ssl.SSLContext sslContext() { return baseClient.sslContext(); }
            @Override public javax.net.ssl.SSLParameters sslParameters() { return baseClient.sslParameters(); }
            @Override public java.util.Optional<java.time.Duration> connectTimeout() { return baseClient.connectTimeout(); }
        };
    }

    private HttpRequest addHeadersToRequest(HttpRequest originalRequest, Map<String, String> headers) {
        if (headers.isEmpty()) {
            return originalRequest;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(originalRequest.uri())
                .method(originalRequest.method(), originalRequest.bodyPublisher()
                        .orElse(HttpRequest.BodyPublishers.noBody()));

        originalRequest.version().ifPresent(requestBuilder::version);
        originalRequest.timeout().ifPresent(requestBuilder::timeout);
        if (originalRequest.expectContinue()) {
            requestBuilder.expectContinue(true);
        }

        originalRequest.headers().map().forEach((name, values) -> {
            for (String value : values) {
                requestBuilder.header(name, value);
            }
        });

        headers.forEach((name, value) -> {
            if (!originalRequest.headers().firstValue(name).isPresent()) {
                requestBuilder.header(name, value);
            }
        });

        return requestBuilder.build();
    }
}
