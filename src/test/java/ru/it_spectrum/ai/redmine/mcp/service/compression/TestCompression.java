package ru.it_spectrum.ai.redmine.mcp.service.compression;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.it_spectrum.ai.redmine.mcp.compression.AttachmentContentCompression;
import ru.it_spectrum.ai.redmine.mcp.compression.IssueCompression;
import ru.it_spectrum.ai.redmine.mcp.compression.IssueFullContextCompression;
import ru.it_spectrum.ai.redmine.mcp.compression.ResponseCompressor;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

public final class TestCompression {

    private TestCompression() {
    }

    public static ResponseCompressor compressor() {
        return new ResponseCompressor(mapper());
    }

    public static ObjectMapper mapper() {
        return new JsonConfig().redmineMcpObjectMapper();
    }

    public static IssueCompression issueCompression(RedmineMcpProperties properties) {
        return new IssueCompression(compressor(), properties);
    }

    public static IssueFullContextCompression contextCompression(RedmineMcpProperties properties) {
        return new IssueFullContextCompression(compressor(), properties);
    }

    public static AttachmentContentCompression attachmentContentCompression(RedmineMcpProperties properties) {
        return new AttachmentContentCompression(compressor(), properties);
    }
}
