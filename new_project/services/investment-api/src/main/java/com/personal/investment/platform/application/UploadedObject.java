package com.personal.investment.platform.application;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Bytes and immutable user metadata read back from the private object store after direct client upload. */
public record UploadedObject(byte[] content, String mediaType, Map<String, String> metadata) {
  public UploadedObject {
    if (content == null || mediaType == null || mediaType.isBlank() || metadata == null) {
      throw new IllegalArgumentException("uploaded object is incomplete");
    }
    content = content.clone();
    Map<String, String> normalized = new LinkedHashMap<>();
    metadata.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
    metadata = Map.copyOf(normalized);
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  public String metadataValue(String xAmzName) {
    String value = metadata.get(xAmzName.toLowerCase(Locale.ROOT));
    return value != null ? value : metadata.get(xAmzName.substring("x-amz-meta-".length()).toLowerCase(Locale.ROOT));
  }
}
