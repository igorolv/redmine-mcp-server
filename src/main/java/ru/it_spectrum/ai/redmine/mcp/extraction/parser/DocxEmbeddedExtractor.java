package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pulls embedded OLE objects and attached files out of a DOCX
 * ({@code word/embeddings/oleObject*.bin}, embedded {@code .xlsx}/{@code .pptx}, etc.) and
 * submits each one back through the pipeline so it gets processed by whatever parser knows
 * its format ({@link XlsxTextParser}, {@link BinaryFallbackParser}, Tika fallback, …).
 */
@Component
@Order(330)
public class DocxEmbeddedExtractor implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocxEmbeddedExtractor.class);

    private final FileTypeDetector types;

    public DocxEmbeddedExtractor(FileTypeDetector types) {
        this.types = types;
    }

    @Override
    public boolean applies(ParseInput in) {
        String ext = types.getFileExtension(in.logicalName());
        String ct = in.contentType() != null ? in.contentType() : "";
        return "docx".equals(ext) || ct.contains("wordprocessingml");
    }

    @Override
    public void parse(ParseInput in, ParseSink sink) {
        Path embeddedDir = in.workDir().resolve("docx").resolve(hashSegment(in.logicalName())).resolve("embedded");
        try {
            Files.createDirectories(embeddedDir);
            try (InputStream is = Files.newInputStream(in.file());
                 XWPFDocument doc = new XWPFDocument(is)) {
                List<PackagePart> embedded = doc.getAllEmbeddedParts();
                for (var part : embedded) {
                    String name = basename(part.getPartName().getName());
                    if (name.isEmpty()) continue;
                    byte[] data;
                    try (InputStream partIn = part.getInputStream()) {
                        data = partIn.readAllBytes();
                    }
                    Path target = embeddedDir.resolve(name);
                    if (!isMaterialized(target, data.length)) {
                        Files.write(target, data);
                    }
                    sink.processNow(target, name, part.getContentType());
                }
            }
        } catch (IOException
                 | org.apache.poi.openxml4j.exceptions.OpenXML4JException
                 | RuntimeException e) {
            log.warn("Failed to extract embedded parts from DOCX {}: {}", in.logicalName(), e.getMessage());
        }
    }

    private static boolean isMaterialized(Path file, long expectedSize) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == expectedSize;
    }

    private static String basename(String partName) {
        if (partName == null) return "";
        int slash = partName.lastIndexOf('/');
        String base = slash >= 0 ? partName.substring(slash + 1) : partName;
        return base.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
    }

    private static String hashSegment(String logicalName) {
        if (logicalName == null) return "root";
        return Integer.toHexString(logicalName.hashCode());
    }
}
