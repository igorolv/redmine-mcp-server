package ru.it_spectrum.ai.redmine.mcp.extraction.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.extraction.DocumentParser;
import ru.it_spectrum.ai.redmine.mcp.extraction.ExtractedPart;
import ru.it_spectrum.ai.redmine.mcp.extraction.FileTypeDetector;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseInput;
import ru.it_spectrum.ai.redmine.mcp.extraction.ParseSink;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@Order(600)
public class ZipParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(ZipParser.class);

    private final FileTypeDetector types;
    private final int maxEntriesPerArchive;

    public ZipParser(FileTypeDetector types, RedmineMcpProperties properties) {
        this.types = types;
        this.maxEntriesPerArchive = properties.extraction().zip().maxEntriesPerArchive();
    }

    @Override
    public boolean applies(ParseInput in) {
        // Mirrors the legacy MAX_ARCHIVE_DEPTH gate: a nested archive (zip-in-zip) is left
        // to BinaryFallbackParser to emit a "skipped, not text-extractable" stub.
        if (in.depth() >= in.ctx().limits().maxDepth()) return false;
        return types.isArchive(in.logicalName(), in.contentType());
    }

    @Override
    public void parse(ParseInput input, ParseSink sink) {
        Path stagingDir;
        try {
            stagingDir = input.workDir().resolve("zip");
            Files.createDirectories(stagingDir);
        } catch (IOException e) {
            log.warn("Failed to prepare staging dir for ZIP {}: {}", input.logicalName(), e.getMessage());
            sink.emit(failurePart(input, "failed to prepare staging dir: " + e.getMessage()));
            return;
        }

        long maxEntryBytes = input.ctx().limits().maxEntryBytes();
        long maxTotalBytes = input.ctx().limits().maxTotalBytes();
        long[] totalBytes = {0};
        int entries = 0;

        try (var raw = Files.newInputStream(input.file());
             var zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                entries++;
                if (entries > maxEntriesPerArchive) {
                    sink.emit(stubPart("...", "zip-entry",
                            "skipped remaining entries (limit: %d)".formatted(maxEntriesPerArchive)));
                    break;
                }

                String entryName = normalizeEntryName(entry.getName());
                if (!isSafeEntryName(entryName)) {
                    sink.emit(stubPart(entry.getName(), "zip-entry", "skipped, unsafe entry name"));
                    continue;
                }

                byte[] entryBytes;
                try {
                    entryBytes = readEntry(zip, entryName, maxEntryBytes, maxTotalBytes, totalBytes);
                } catch (ArchiveReadLimitException e) {
                    sink.emit(stubPart(entryName,
                            types.detectExtractionType(entryName, null),
                            "skipped, " + e.getMessage()));
                    sink.emit(stubPart("...", "zip-entry",
                            "stopped archive extraction after reaching safety limits"));
                    break;
                }

                Path entryFile = stagingDir.resolve(entryName);
                Files.createDirectories(entryFile.getParent());
                Files.write(entryFile, entryBytes);
                sink.processNow(entryFile, entryName, null);
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from ZIP archive {}: {}", input.logicalName(), e.getMessage());
            sink.emit(failurePart(input, "failed to extract ZIP archive: " + e.getMessage()));
        }
    }

    private byte[] readEntry(InputStream zip, String entryName,
                             long maxEntryBytes, long maxTotalBytes, long[] totalBytes) throws IOException {
        var out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            if (out.size() + read > maxEntryBytes) {
                throw new ArchiveReadLimitException("%s exceeds per-file limit %s"
                        .formatted(entryName, formatBytes(maxEntryBytes)));
            }
            if (totalBytes[0] + read > maxTotalBytes) {
                throw new ArchiveReadLimitException("archive exceeds total limit %s"
                        .formatted(formatBytes(maxTotalBytes)));
            }
            out.write(buffer, 0, read);
            totalBytes[0] += read;
        }
        return out.toByteArray();
    }

    private ExtractedPart stubPart(String name, String extractionType, String note) {
        return new ExtractedPart(name, null, extractionType, null, null, null, null, null, note);
    }

    private ExtractedPart failurePart(ParseInput input, String note) {
        long size;
        try {
            size = Files.size(input.file());
        } catch (IOException ignored) {
            size = 0;
        }
        return new ExtractedPart(input.logicalName(), null, "zip", null, size, null,
                input.file().toString(), input.file().toUri().toString(), note);
    }

    private static String normalizeEntryName(String entryName) {
        return entryName == null ? "" : entryName.replace('\\', '/');
    }

    private static boolean isSafeEntryName(String entryName) {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
            return false;
        }
        for (String part : entryName.split("/")) {
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        return "%.1f MB".formatted(bytes / (1024.0 * 1024));
    }

    private static class ArchiveReadLimitException extends IOException {
        ArchiveReadLimitException(String message) {
            super(message);
        }
    }
}
