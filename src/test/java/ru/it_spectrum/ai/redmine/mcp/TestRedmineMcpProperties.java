package ru.it_spectrum.ai.redmine.mcp;

import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.nio.file.Path;

public final class TestRedmineMcpProperties {

    private TestRedmineMcpProperties() {
    }

    public static RedmineMcpProperties defaults() {
        return withDataDir((String) null);
    }

    public static RedmineMcpProperties withDataDir(Path dataDir) {
        return withDataDir(dataDir != null ? dataDir.toString() : null);
    }

    public static RedmineMcpProperties withDataDir(String dataDir) {
        return new RedmineMcpProperties(dataDir, null, null, null, null, null, null);
    }
}
