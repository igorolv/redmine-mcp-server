package ru.it_spectrum.ai.redmine.mcp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Makes slow and failed Redmine round-trips visible in the server log. Tool calls are processed one
 * at a time, so a stalled request explains why every later call waits; without this line the log
 * only shows a tool call that never completes.
 */
public class RedmineHttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RedmineHttpLoggingInterceptor.class);

    private final long slowRequestWarnMillis;

    public RedmineHttpLoggingInterceptor(long slowRequestWarnMillis) {
        this.slowRequestWarnMillis = slowRequestWarnMillis;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            var response = execution.execute(request, body);
            long elapsedMillis = elapsedMillis(start);
            if (elapsedMillis >= slowRequestWarnMillis) {
                log.warn("Slow Redmine request: {} {} -> {} (elapsed: {}ms)",
                        request.getMethod(), request.getURI().getRawPath(), response.getStatusCode().value(),
                        elapsedMillis);
            }
            return response;
        } catch (IOException e) {
            log.warn("Redmine request failed: {} {} (elapsed: {}ms): {}",
                    request.getMethod(), request.getURI().getRawPath(), elapsedMillis(start), e.toString());
            throw e;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
