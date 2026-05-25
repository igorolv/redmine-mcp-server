package ru.it_spectrum.ai.redmine.mcp.compression;

public record CompressionOptions(ResponseProfile profile) {

    public CompressionOptions {
        profile = profile != null ? profile : ResponseProfile.DEFAULT;
    }

    public static CompressionOptions defaults() {
        return new CompressionOptions(ResponseProfile.DEFAULT);
    }

    public static CompressionOptions fromProfile(String responseProfile) {
        return new CompressionOptions(ResponseProfile.from(responseProfile));
    }
}
