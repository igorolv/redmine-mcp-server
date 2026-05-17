package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;
import ru.it_spectrum.ai.redmine.mcp.extraction.TextNormalizer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Fallback text extractor backed by Apache Tika. Runs only when no PRIMARY parser produced
 * text, so it kicks in for formats we don't natively support (e.g. legacy {@code .doc},
 * {@code .rtf}, {@code .odt}, {@code .msg}, {@code .eml}, {@code .epub}).
 */
@Component
@Order(1000)
public class TikaTextFallbackParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaTextFallbackParser.class);
    private static final int BODY_LIMIT_BYTES = 5 * 1024 * 1024;

    private final FileTypeDetector types;

    public TikaTextFallbackParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public Phase phase() {
        return Phase.FALLBACK;
    }

    @Override
    public boolean applies(ParseInput in) {
        if (types.isImage(in.logicalName(), in.contentType())) return false;
        if (types.isArchive(in.logicalName(), in.contentType())) return false;
        return true;
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        if (sink.hasTextPart()) return;

        String text;
        try (InputStream is = Files.newInputStream(in.file())) {
            var handler = new BodyContentHandler(BODY_LIMIT_BYTES);
            new AutoDetectParser().parse(is, handler, new Metadata(),
                    new org.apache.tika.parser.ParseContext());
            text = handler.toString().strip();
        } catch (Exception e) {
            log.debug("Tika text fallback failed for {}: {}", in.logicalName(), e.getMessage());
            return;
        }

        if (text.isEmpty()) return;

        long size;
        try {
            size = Files.size(in.file());
        } catch (IOException e) {
            size = 0;
        }
        sink.emit(new ExtractedPart(
                in.emitName(),
                null,
                "tika-text",
                null,
                size,
                TextNormalizer.normalize(text),
                in.file().toString(),
                in.file().toUri().toString(),
                null
        ));
    }
}
