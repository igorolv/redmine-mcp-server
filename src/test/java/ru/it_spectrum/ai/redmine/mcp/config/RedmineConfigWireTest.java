package ru.it_spectrum.ai.redmine.mcp.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPageMutation;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RedmineConfigWireTest {

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void configuredClientSendsWikiJsonBodyOnTheWire() {
        var captured = new AtomicReference<CapturedRequest>();
        server.createContext("/", exchange -> {
            captured.set(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Content-Length"),
                    exchange.getRequestHeaders().getFirst("Transfer-Encoding"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        new ApplicationContextRunner()
                .withUserConfiguration(JsonConfig.class, RedmineConfig.class, MutationClientConfiguration.class)
                .withPropertyValues(
                        "redmine.url=" + baseUrl,
                        "redmine.api-key=test-key",
                        "redmine-mcp.write.enabled=true")
                .run(context -> context.getBean(RedmineMutationClient.class)
                        .putWikiPage("backend", "Тест страница", new RedmineWikiPageMutation.Fields(
                                "h1. Проверка", "AI_EDIT:\n\nПервый текст", null, null)));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().method()).isEqualTo("PUT");
        assertThat(captured.get().rawPath())
                .isEqualTo("/projects/backend/wiki/%D0%A2%D0%B5%D1%81%D1%82%20%D1%81%D1%82%D1%80%D0%B0%D0%BD%D0%B8%D1%86%D0%B0.json");
        assertThat(captured.get().contentType()).isEqualTo("application/json");
        assertThat(captured.get().contentLength())
                .isEqualTo(Integer.toString(captured.get().body().getBytes(StandardCharsets.UTF_8).length));
        assertThat(captured.get().transferEncoding()).isNull();
        assertThat(captured.get().body()).isEqualTo(
                "{\"wiki_page\":{\"text\":\"h1. Проверка\",\"comments\":\"AI_EDIT:\\n\\nПервый текст\"}}");
    }

    @Test
    void configuredClientEncodesCyrillicWikiTitleForGet() {
        var capturedPath = new AtomicReference<String>();
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getRawPath());
            byte[] response = """
                    {"wiki_page":{"title":"Тест страница","text":"Текст","version":1}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var restClient = new RedmineConfig().redmineRestClient(
                new RedmineClientProperties(baseUrl, "test-key"));

        var page = new RedmineClient(restClient).getWikiPage("backend", "Тест страница");

        assertThat(page.text()).isEqualTo("Текст");
        assertThat(capturedPath.get())
                .isEqualTo("/projects/backend/wiki/%D0%A2%D0%B5%D1%81%D1%82%20%D1%81%D1%82%D1%80%D0%B0%D0%BD%D0%B8%D1%86%D0%B0.json");
    }

    private record CapturedRequest(String method, String rawPath, String contentType,
                                   String contentLength, String transferEncoding, String body) {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RedmineMutationClient.class)
    static class MutationClientConfiguration {
    }
}
