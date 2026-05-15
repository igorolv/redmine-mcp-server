package ru.it_spectrum.ai.redmine.mcp.service;

public class ImageProcessingFailedException extends AttachmentServiceException {

    public ImageProcessingFailedException(int attachmentId, Throwable cause) {
        super(attachmentId, "Failed to process image #%d: %s".formatted(attachmentId, cause.getMessage()), cause);
    }
}
