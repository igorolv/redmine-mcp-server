package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xml.sax.helpers.DefaultHandler;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Emits a small "tika-metadata" Part with human-readable Key: value lines describing the
 * top-level attachment (author, page count, application name, image dimensions, etc.).
 *
 * <p>Applies only at depth 0 — metadata of every nested zip entry would just be noise. Skips
 * archives and plain-text files where Tika has nothing interesting beyond Content-Type. The
 * output is capped to keep the Part small and predictable.</p>
 */
@Component
@Order(900)
public class TikaMetadataParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaMetadataParser.class);
    private static final int MAX_FIELDS = 40;

    private final FileTypeDetector types;

    public TikaMetadataParser(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        if (in.depth() != 0) return false;
        if (types.isArchive(in.logicalName(), in.contentType())) return false;
        if (types.isPlainText(in.logicalName(), in.contentType())) return false;
        return true;
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        Metadata metadata = new Metadata();
        try (InputStream is = Files.newInputStream(in.file())) {
            new AutoDetectParser().parse(is, new DefaultHandler(), metadata, new org.apache.tika.parser.ParseContext());
        } catch (Exception e) {
            log.debug("Tika metadata extraction failed for {}: {}", in.logicalName(), e.getMessage());
            return;
        }

        String content = formatMetadata(metadata);
        if (content.isEmpty()) return;

        long size;
        try {
            size = Files.size(in.file());
        } catch (IOException e) {
            size = 0;
        }
        sink.emit(new ExtractedPart(
                in.emitName(),
                null,
                "tika-metadata",
                null,
                size,
                content,
                in.file().toString(),
                in.file().toUri().toString(),
                null
        ));
    }

    private String formatMetadata(Metadata metadata) {
        var sb = new StringBuilder();
        int count = 0;
        int total = 0;
        String[] names = metadata.names();
        Arrays.sort(names);
        for (String name : names) {
            if (name.startsWith("X-TIKA:")) continue;
            String value = metadata.get(name);
            if (value == null || value.isBlank()) continue;
            total++;
            if (count >= MAX_FIELDS) continue;
            sb.append(name).append(": ").append(value).append('\n');
            count++;
        }
        if (total > MAX_FIELDS) {
            sb.append("... (").append(total - MAX_FIELDS).append(" more fields omitted)\n");
        }
        return sb.toString().strip();
    }
}
