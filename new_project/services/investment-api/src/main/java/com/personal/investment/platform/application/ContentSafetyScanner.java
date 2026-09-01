package com.personal.investment.platform.application;

@FunctionalInterface
public interface ContentSafetyScanner {
  void scan(UploadedObject object);

  /** Local development has no malware engine; structural parsing and SHA-256 verification remain mandatory. */
  static ContentSafetyScanner localStructural() {
    return object -> {
      if (object.content().length == 0) {
        throw new IllegalArgumentException("uploaded object must not be empty");
      }
    };
  }
}
