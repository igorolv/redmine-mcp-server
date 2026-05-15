package ru.it_spectrum.ai.redmine.mcp.service;

public abstract class AttachmentServiceException extends RuntimeException {

    private final int attachmentId;

    protected AttachmentServiceException(int attachmentId, String message) {
        super(message);
        this.attachmentId = attachmentId;
    }

    protected AttachmentServiceException(int attachmentId, String message, Throwable cause) {
        super(message, cause);
        this.attachmentId = attachmentId;
    }

    public int attachmentId() {
        return attachmentId;
    }
}
