package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Extracts embedded media (images, etc.) from a DOCX via POI's {@code getAllPictures()} and
 * submits each file back to the pipeline so {@link ImagePassthroughParser} (or
 * {@link BinaryFallbackParser}) can expose it as its own Part with a {@code localPath}.
 *
 * <p>Runs unconditionally — independent of pandoc — so media is always extracted whenever
 * a DOCX is parsed. Media is written to {@code <workDir>/docx-media/<hash>/}; existing files
 * with matching size are not overwritten.</p>
 */
@Component
public class DocxMediaExtractor implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocxMediaExtractor.class);

    private final FileTypeDetector types;

    public DocxMediaExtractor(FileTypeDetector types) {
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
        Path mediaDir = in.workDir().resolve("docx-media").resolve(hashSegment(in.logicalName()));
        try {
            Files.createDirectories(mediaDir);
            try (InputStream is = Files.newInputStream(in.file());
                 XWPFDocument doc = new XWPFDocument(is)) {
                List<XWPFPictureData> pictures = doc.getAllPictures();
                for (var pic : pictures) {
                    String name = sanitize(pic.getFileName());
                    if (name.isEmpty()) continue;
                    Path target = mediaDir.resolve(name);
                    byte[] data = pic.getData();
                    if (!isMaterialized(target, data.length)) {
                        Files.write(target, data);
                    }
                    sink.processNow(target, name, null);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to extract media from DOCX {}: {}", in.logicalName(), e.getMessage());
        }
    }

    private static boolean isMaterialized(Path file, long expectedSize) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == expectedSize;
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String basename = slash >= 0 ? name.substring(slash + 1) : name;
        return basename.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
    }

    private static String hashSegment(String logicalName) {
        if (logicalName == null) return "root";
        return Integer.toHexString(logicalName.hashCode());
    }
}
