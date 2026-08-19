package ru.it_spectrum.ai.redmine.mcp.client;

public class RedmineMutationException extends RuntimeException {
    private final int statusCode;

    public RedmineMutationException(int statusCode, String responseBody, Throwable cause) {
        super(message(statusCode, responseBody), cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    private static String message(int statusCode, String responseBody) {
        String detail = responseBody == null ? "" : responseBody.replaceAll("\\s+", " ").trim();
        if (detail.length() > 500) {
            detail = detail.substring(0, 500) + "...";
        }
        return detail.isEmpty()
                ? "Redmine write request failed with HTTP %d".formatted(statusCode)
                : "Redmine write request failed with HTTP %d: %s".formatted(statusCode, detail);
    }
}
