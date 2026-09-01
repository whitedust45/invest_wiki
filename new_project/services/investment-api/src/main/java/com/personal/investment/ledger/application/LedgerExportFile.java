package com.personal.investment.ledger.application;

import java.util.Arrays;

/** In-memory response for one owner-scoped audit export. Bytes are streamed to the authenticated caller only. */
public record LedgerExportFile(String exportId, LedgerExportFormat format, byte[] content, String contentSha256Hex,
                               long sourceLedgerVersion) {
  public LedgerExportFile {
    if (exportId == null || !exportId.matches("^[0-9A-HJKMNP-TV-Z]{26}$") || format == null || content == null
        || content.length == 0 || contentSha256Hex == null || !contentSha256Hex.matches("^[a-f0-9]{64}$")
        || sourceLedgerVersion < 0) {
      throw new IllegalArgumentException("ledger export response is invalid");
    }
    content = Arrays.copyOf(content, content.length);
  }

  @Override
  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }
}
