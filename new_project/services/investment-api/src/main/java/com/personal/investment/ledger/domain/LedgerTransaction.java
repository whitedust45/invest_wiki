package com.personal.investment.ledger.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable append-only ledger fact. Corrections will create additional facts rather than mutation. */
public record LedgerTransaction(
    String transactionId,
    String ownerUserId,
    LedgerTransactionType transactionType,
    LocalDate occurredOn,
    LedgerSourceType sourceType,
    String importExportFileId,
    String correctionRootTransactionId,
    String reversalOfTransactionId,
    int revisionNo,
    long ledgerVersion,
    String note,
    List<LedgerPostingFact> postings,
    List<LedgerTradeDetail> tradeDetails) {
  public LedgerTransaction {
    requireText(transactionId, "transactionId");
    requireText(ownerUserId, "ownerUserId");
    Objects.requireNonNull(transactionType, "transactionType must not be null");
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    Objects.requireNonNull(sourceType, "sourceType must not be null");
    if (sourceType == LedgerSourceType.IMPORT) {
      requireText(importExportFileId, "importExportFileId");
      if (!importExportFileId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
        throw new IllegalArgumentException("importExportFileId must be a ULID");
      }
    } else if (importExportFileId != null) {
      throw new IllegalArgumentException("only imported transactions may reference an importExportFileId");
    }
    requireText(correctionRootTransactionId, "correctionRootTransactionId");
    if (revisionNo < 0) {
      throw new IllegalArgumentException("revisionNo must not be negative");
    }
    if (transactionType == LedgerTransactionType.REVERSAL
        && (reversalOfTransactionId == null || reversalOfTransactionId.isBlank())) {
      throw new IllegalArgumentException("reversal transaction must reference its target transaction");
    }
    if (transactionType != LedgerTransactionType.REVERSAL && reversalOfTransactionId != null) {
      throw new IllegalArgumentException("only reversal transactions may reference a target transaction");
    }
    if (ledgerVersion < 1) {
      throw new IllegalArgumentException("ledgerVersion must be positive");
    }
    if (note != null && note.length() > 1_000) {
      throw new IllegalArgumentException("note exceeds 1000 characters");
    }
    postings = List.copyOf(postings);
    tradeDetails = List.copyOf(tradeDetails);
    if (!(postings.isEmpty() && permitsNonMonetaryFact(transactionType))) {
      BalancedPostings.of(postings.stream()
          .map(posting -> new Posting(posting.accountId(), posting.side(), posting.amount())).toList());
    }
    if (postings.stream().map(LedgerPostingFact::postingNo).distinct().count() != postings.size()) {
      throw new IllegalArgumentException("postingNo must be unique within a transaction");
    }
    if (tradeDetails.stream().map(LedgerTradeDetail::detailNo).distinct().count() != tradeDetails.size()) {
      throw new IllegalArgumentException("detailNo must be unique within a transaction");
    }
    if ((transactionType == LedgerTransactionType.TRADE_BUY || transactionType == LedgerTransactionType.TRADE_SELL)
        && tradeDetails.size() != 1) {
      throw new IllegalArgumentException("spot trade requires exactly one trade detail");
    }
  }

  private static boolean permitsNonMonetaryFact(LedgerTransactionType transactionType) {
    return transactionType == LedgerTransactionType.CORPORATE_ACTION
        || transactionType == LedgerTransactionType.REVERSAL
        || transactionType == LedgerTransactionType.FUTURES_DAILY_SETTLEMENT;
  }

  public static LedgerTransaction original(
      String transactionId,
      String ownerUserId,
      LedgerTransactionType transactionType,
      LocalDate occurredOn,
      long ledgerVersion,
      String note,
      List<LedgerPostingFact> postings) {
    return new LedgerTransaction(transactionId, ownerUserId, transactionType, occurredOn,
        LedgerSourceType.MANUAL, null, transactionId, null, 0, ledgerVersion, note, postings, List.of());
  }

  /** A fact reconstructed from an immutable, user-confirmed import preview. */
  public static LedgerTransaction imported(
      String transactionId,
      String ownerUserId,
      LedgerTransactionType transactionType,
      LocalDate occurredOn,
      String importExportFileId,
      long ledgerVersion,
      String note,
      List<LedgerPostingFact> postings) {
    return new LedgerTransaction(transactionId, ownerUserId, transactionType, occurredOn,
        LedgerSourceType.IMPORT, importExportFileId, transactionId, null, 0, ledgerVersion, note, postings, List.of());
  }

  public static LedgerTransaction original(
      String transactionId,
      String ownerUserId,
      LedgerTransactionType transactionType,
      LocalDate occurredOn,
      long ledgerVersion,
      String note,
      List<LedgerPostingFact> postings,
      List<LedgerTradeDetail> tradeDetails) {
    return new LedgerTransaction(transactionId, ownerUserId, transactionType, occurredOn,
        LedgerSourceType.MANUAL, null, transactionId, null, 0, ledgerVersion, note, postings, tradeDetails);
  }

  /** A fact reconstructed from an immutable, user-confirmed import preview. */
  public static LedgerTransaction imported(
      String transactionId,
      String ownerUserId,
      LedgerTransactionType transactionType,
      LocalDate occurredOn,
      String importExportFileId,
      long ledgerVersion,
      String note,
      List<LedgerPostingFact> postings,
      List<LedgerTradeDetail> tradeDetails) {
    return new LedgerTransaction(transactionId, ownerUserId, transactionType, occurredOn,
        LedgerSourceType.IMPORT, importExportFileId, transactionId, null, 0, ledgerVersion, note, postings, tradeDetails);
  }

  public static LedgerTransaction reversal(String transactionId, String ownerUserId, LocalDate occurredOn,
      String correctionRootTransactionId, String reversalOfTransactionId, int revisionNo, long ledgerVersion,
      String note, List<LedgerPostingFact> postings, List<LedgerTradeDetail> tradeDetails) {
    return new LedgerTransaction(transactionId, ownerUserId, LedgerTransactionType.REVERSAL, occurredOn,
        LedgerSourceType.CORRECTION_REVERSAL, null, correctionRootTransactionId, reversalOfTransactionId, revisionNo,
        ledgerVersion, note, postings, tradeDetails);
  }

  public static LedgerTransaction replacement(String transactionId, String ownerUserId,
      LedgerTransactionType transactionType, LocalDate occurredOn, String correctionRootTransactionId,
      int revisionNo, long ledgerVersion, String note, List<LedgerPostingFact> postings,
      List<LedgerTradeDetail> tradeDetails) {
    if (transactionType == LedgerTransactionType.REVERSAL) {
      throw new IllegalArgumentException("a replacement must use a normal business transaction type");
    }
    return new LedgerTransaction(transactionId, ownerUserId, transactionType, occurredOn,
        LedgerSourceType.CORRECTION_REPLACEMENT, null, correctionRootTransactionId, null, revisionNo, ledgerVersion,
        note, postings, tradeDetails);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
