package com.personal.investment.platform.application;

public record UploadRequestCommand(ImportExportFileDirection direction, String mediaType, long byteSize,
                                   String contentSha256Hex) {
}
