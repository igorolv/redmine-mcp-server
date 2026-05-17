package ru.it_spectrum.ai.redmine.mcp.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the path to a usable {@code pandoc} executable at startup, caching the result.
 *
 * <p>Lookup order:
 * <ol>
 *   <li>{@code pandoc} on the system PATH;</li>
 *   <li>{@code <data-dir>/utils/pandoc/pandoc[.exe]};</li>
 *   <li>{@code <data-dir>/utils/pandoc/bin/pandoc[.exe]}.</li>
 * </ol>
 * The data-dir fallback lets a portable pandoc binary travel with the
 * {@code redmine-mcp.data-dir} between machines.</p>
 *
 * <p>Disabled when {@code redmine-mcp.extraction.pandoc.enabled=false} or when no candidate
 * responds to {@code --version} within a short timeout.</p>
 */
@Component
public class PandocAvailability {

    private static final Logger log = LoggerFactory.getLogger(PandocAvailability.class);

    private final Path executable;
    private final String version;
    private final int probeTimeoutSeconds;

    @Autowired
    public PandocAvailability(RedmineMcpProperties properties) {
        this.probeTimeoutSeconds = properties.extraction().pandoc().probeTimeoutSeconds();
        if (!properties.extraction().pandoc().enabled()) {
            this.executable = null;
            this.version = null;
            log.info("Pandoc extraction disabled via redmine-mcp.extraction.pandoc.enabled=false");
            return;
        }

        Path dataDir = properties.resolvedDataDir();
        var detected = detect(dataDir);
        if (detected.isPresent()) {
            this.executable = detected.get().path();
            this.version = detected.get().version();
            log.info("Pandoc available: {} ({})", executable, version);
        } else {
            this.executable = null;
            this.version = null;
            log.info("Pandoc not found on PATH or in {}/utils/pandoc — DOCX markdown extraction disabled",
                    dataDir);
        }
    }

    private PandocAvailability(Path executable, String version) {
        this.executable = executable;
        this.version = version;
        this.probeTimeoutSeconds = RedmineMcpProperties.DEFAULT_PANDOC_PROBE_TIMEOUT_SECONDS;
    }

    /** Returns an instance that always reports pandoc as unavailable. Intended for tests. */
    public static PandocAvailability disabled() {
        return new PandocAvailability((Path) null, null);
    }

    public boolean isAvailable() {
        return executable != null;
    }

    public Optional<Path> executable() {
        return Optional.ofNullable(executable);
    }

    public Optional<String> version() {
        return Optional.ofNullable(version);
    }

    private Optional<Detected> detect(Path dataDir) {
        var fromPath = probe(Path.of("pandoc"));
        if (fromPath.isPresent()) return fromPath;

        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = win ? "pandoc.exe" : "pandoc";
        List<Path> candidates = List.of(
                dataDir.resolve("utils").resolve("pandoc").resolve(exe),
                dataDir.resolve("utils").resolve("pandoc").resolve("bin").resolve(exe)
        );
        for (var candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                var probed = probe(candidate);
                if (probed.isPresent()) return probed;
            }
        }
        return Optional.empty();
    }

    private Optional<Detected> probe(Path candidate) {
        try {
            var pb = new ProcessBuilder(candidate.toString(), "--version").redirectErrorStream(true);
            var process = pb.start();
            if (!process.waitFor(probeTimeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String version = output.lines().findFirst().orElse("unknown");
            return Optional.of(new Detected(candidate, version));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private record Detected(Path path, String version) {
    }
}
