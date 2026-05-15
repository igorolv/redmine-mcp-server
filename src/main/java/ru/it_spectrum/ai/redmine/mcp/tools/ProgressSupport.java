package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import ru.it_spectrum.ai.redmine.mcp.service.ProgressReporter;

final class ProgressSupport {

    private ProgressSupport() {
    }

    static void stage(McpSyncRequestContext context, String message) {
        if (!supportsProgress(context)) {
            return;
        }
        context.progress(spec -> spec.message(message));
    }

    static void report(McpSyncRequestContext context, int current, int total, String message) {
        if (!supportsProgress(context)) {
            return;
        }
        context.progress(spec -> spec.progress(current).total(total).message(message));
    }

    static void done(McpSyncRequestContext context, String message) {
        report(context, 1, 1, message);
    }

    static ProgressReporter reporterFor(McpSyncRequestContext context) {
        if (context == null) {
            return ProgressReporter.NOOP;
        }
        return new ProgressReporter() {
            @Override public void stage(String message) { ProgressSupport.stage(context, message); }
            @Override public void report(int current, int total, String message) {
                ProgressSupport.report(context, current, total, message);
            }
            @Override public void done(String message) { ProgressSupport.done(context, message); }
        };
    }

    private static boolean supportsProgress(McpSyncRequestContext context) {
        if (context == null || context.request() == null) {
            return false;
        }
        Object token = context.request().progressToken();
        if (token == null) {
            return false;
        }
        return !(token instanceof String text) || !text.isBlank();
    }
}
