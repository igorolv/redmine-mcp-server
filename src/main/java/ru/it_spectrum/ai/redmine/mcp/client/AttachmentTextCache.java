package ru.it_spectrum.ai.redmine.mcp.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AttachmentTextCache {
    private static final Logger log = LoggerFactory.getLogger(AttachmentTextCache.class);

    private static final long TTL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final int FILE_THRESHOLD = 1_000_000; // 1 MB of chars → use temp file

    private final ConcurrentHashMap<Integer, CacheEntry> cache = new ConcurrentHashMap<>();

    public String get(int attachmentId) {
        var entry = cache.get(attachmentId);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt > TTL_MS) {
            evict(attachmentId);
            return null;
        }
        return entry.read();
    }

    public void put(int attachmentId, String text) {
        evictExpired();
        if (text.length() > FILE_THRESHOLD) {
            try {
                Path tempFile = Files.createTempFile("redmine-att-" + attachmentId + "-", ".txt");
                Files.writeString(tempFile, text);
                cache.put(attachmentId, new CacheEntry(null, tempFile, System.currentTimeMillis()));
                return;
            } catch (IOException e) {
                log.warn("Failed to write cache temp file for attachment #{}, falling back to memory", attachmentId, e);
            }
        }
        cache.put(attachmentId, new CacheEntry(text, null, System.currentTimeMillis()));
    }

    private void evict(int attachmentId) {
        var entry = cache.remove(attachmentId);
        if (entry != null) {
            entry.cleanup();
        }
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> {
            if (now - e.getValue().createdAt > TTL_MS) {
                e.getValue().cleanup();
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void destroy() {
        for (var entry : cache.values()) {
            entry.cleanup();
        }
        cache.clear();
    }

    private static class CacheEntry {
        final String inMemory;
        final Path filePath;
        final long createdAt;

        CacheEntry(String inMemory, Path filePath, long createdAt) {
            this.inMemory = inMemory;
            this.filePath = filePath;
            this.createdAt = createdAt;
        }

        String read() {
            if (inMemory != null) {
                return inMemory;
            }
            if (filePath != null) {
                try {
                    return Files.readString(filePath);
                } catch (IOException e) {
                    log.warn("Failed to read cache file {}", filePath, e);
                    return null;
                }
            }
            return null;
        }

        void cleanup() {
            if (filePath != null) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    log.warn("Failed to delete cache file {}", filePath, e);
                }
            }
        }

        private static final Logger log = LoggerFactory.getLogger(CacheEntry.class);
    }
}
