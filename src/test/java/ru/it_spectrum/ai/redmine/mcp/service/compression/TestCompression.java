package ru.it_spectrum.ai.redmine.mcp.service.compression;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

public final class TestCompression {

    private TestCompression() {
    }

    public static ResponseCompressor compressor() {
        return new ResponseCompressor(new ObjectMapper());
    }

    public static IssueCompression issueCompression(RedmineMcpProperties properties) {
        return new IssueCompression(compressor(), properties);
    }

    public static IssueFullContextCompression contextCompression(RedmineMcpProperties properties) {
        return new IssueFullContextCompression(compressor(), properties);
    }
}
