package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;
import ru.it_spectrum.ai.redmine.mcp.extraction.TextNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Component
public class PlainTextParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PlainTextParser.class);

    private final FileTypeDetector types;

    public PlainTextParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        if (types.isDocument(in.logicalName(), in.contentType())) return false;
        if (types.isArchive(in.logicalName(), in.contentType())) return false;
        return types.isPlainText(in.logicalName(), in.contentType());
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        try {
            long size = Files.size(in.file());
            String text = TextNormalizer.normalize(Files.readString(in.file(), StandardCharsets.UTF_8));
            sink.emit(new ExtractedPart(
                    in.emitName(),
                    null,
                    "text",
                    null,
                    size,
                    text,
                    in.file().toString(),
                    in.file().toUri().toString(),
                    null
            ));
        } catch (IOException e) {
            log.warn("Failed to read plain-text file {}: {}", in.file(), e.getMessage());
        }
    }
}
