package com.personal.investment.ledger.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.market.application.InstrumentPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only write path for a snapshot recovery. It obtains the owner ledger-state lock, rejects every non-empty
 * workspace and then atomically imports a newly-identified copy of the snapshot facts. It never updates or deletes
 * an existing fact.
 */
@Service
public class LedgerSnapshotRestoreService {
  private final LedgerSnapshotService snapshotService;
  private final LedgerAccountPort accountPort;
  private final LedgerTransactionPort transactionPort;
  private final CorporateActionPort corporateActionPort;
  private final IncomeDetailPort incomeDetailPort;
  private final InstrumentPort instrumentPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerSnapshotAuditPort auditPort;
  private final ObjectMapper json;

  public LedgerSnapshotRestoreService(LedgerSnapshotService snapshotService, LedgerAccountPort accountPort,
      LedgerTransactionPort transactionPort, CorporateActionPort corporateActionPort, IncomeDetailPort incomeDetailPort,
      InstrumentPort instrumentPort, LedgerIdGenerator idGenerator, LedgerSnapshotAuditPort auditPort,
      ObjectMapper json) {
    this.snapshotService = snapshotService;
    this.accountPort = accountPort;
    this.transactionPort = transactionPort;
    this.corporateActionPort = corporateActionPort;
    this.incomeDetailPort = incomeDetailPort;
    this.instrumentPort = instrumentPort;
    this.idGenerator = idGenerator;
    this.auditPort = auditPort;
    this.json = json;
  }

  @Transactional
  public LedgerSnapshotRestoreResult restoreIntoEmptyWorkspace(String ownerUserId, String ledgerSnapshotId) {
    requireUlid(ownerUserId, "ownerUserId");
    requireUlid(ledgerSnapshotId, "ledgerSnapshotId");
    LedgerSnapshotFile file = snapshotService.download(ownerUserId, ledgerSnapshotId);
    RestorePlan plan = parse(file.content(), file.snapshot().importExportFileId());
    validateInstruments(plan);

    long currentVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    if (currentVersion != 0 || transactionPort.hasAnyTransactionByOwner(ownerUserId)
        || accountPort.hasAnyAccountByOwner(ownerUserId)) {
      throw new LedgerSnapshotRestoreRejectedException(
          "snapshot restore is allowed only in an empty ledger workspace");
    }

    for (RestoredAccount account : plan.accounts()) {
      accountPort.insert(account.account(ownerUserId));
    }
    long targetLedgerVersion = currentVersion;
    for (RestoredTransaction restored : plan.transactions()) {
      targetLedgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, targetLedgerVersion);
      LedgerTransaction transaction = restored.transaction(ownerUserId, targetLedgerVersion);
      String operationGroupKey = restored.operationGroupKey();
      LedgerAppendMetadata.withStrategyKey(restored.strategyKey(), () -> LedgerAppendMetadata.withOperationGroupKey(
          operationGroupKey, () -> {
            transactionPort.append(transaction);
            return null;
          }));
      if (restored.corporateAction() != null) {
        corporateActionPort.insert(restored.corporateAction());
      }
      if (restored.income() != null) {
        incomeDetailPort.insert(restored.income());
      }
    }
    LedgerSnapshotRestoreResult result = new LedgerSnapshotRestoreResult(ledgerSnapshotId, plan.accounts().size(),
        plan.transactions().size(), targetLedgerVersion);
    auditPort.recordRestored(ownerUserId, ledgerSnapshotId, result.restoredAccountCount(),
        result.restoredTransactionCount(), result.targetLedgerVersion());
    return result;
  }

  private RestorePlan parse(byte[] content, String snapshotFileId) {
    try {
      LedgerExportDocument document = json.readValue(content, LedgerExportDocument.class);
      if (document == null || !"2".equals(document.schemaVersion()) || document.accounts() == null
          || document.accounts().isEmpty() || document.transactions() == null || document.transactions().isEmpty()) {
        throw new IllegalArgumentException("snapshot must be a non-empty ledger JSON export with schemaVersion 2");
      }
      long sourceLedgerVersion = positiveLong(document.sourceLedgerVersion(), "sourceLedgerVersion");
      Map<String, String> accountIds = new HashMap<>();
      List<RestoredAccount> accounts = new ArrayList<>();
      Set<String> accountCodes = new HashSet<>();
      for (LedgerExportDocument.Account source : document.accounts()) {
        requireUlid(source.accountId(), "snapshot accountId");
        if (accountIds.putIfAbsent(source.accountId(), idGenerator.next()) != null) {
          throw new IllegalArgumentException("snapshot contains duplicate account IDs");
        }
      }
      for (LedgerExportDocument.Account source : document.accounts()) {
        String code = rewriteAccountReferences(requireText(source.accountCode(), "accountCode"), accountIds);
        if (!accountCodes.add(code)) {
          throw new IllegalArgumentException("snapshot contains duplicate account codes");
        }
        LedgerAccount account = new LedgerAccount(accountIds.get(source.accountId()), "RESTORE_OWNER_PLACEHOLDER", code,
            LedgerAccountKind.valueOf(requireText(source.accountKind(), "accountKind")),
            CurrencyCode.of(requireText(source.currency(), "account currency")),
            requireText(source.displayName(), "account displayName"),
            LedgerAccountStatus.valueOf(requireText(source.status(), "account status")),
            nonNegativeLong(source.version(), "account version"));
        accounts.add(new RestoredAccount(source.accountId(), account));
      }

      Map<String, String> transactionIds = new HashMap<>();
      for (LedgerExportDocument.Transaction source : document.transactions()) {
        requireUlid(source.transactionId(), "snapshot transactionId");
        if (transactionIds.putIfAbsent(source.transactionId(), idGenerator.next()) != null) {
          throw new IllegalArgumentException("snapshot contains duplicate transaction IDs");
        }
      }
      Map<String, String> operationGroups = new HashMap<>();
      List<RestoredTransaction> transactions = new ArrayList<>();
      Set<Long> sourceVersions = new HashSet<>();
      for (LedgerExportDocument.Transaction source : document.transactions()) {
        long originalVersion = positiveLong(source.ledgerVersion(), "transaction ledgerVersion");
        if (!sourceVersions.add(originalVersion)) {
          throw new IllegalArgumentException("snapshot contains duplicate ledger versions");
        }
        String originalRoot = requireText(source.correctionRootTransactionId(), "correctionRootTransactionId");
        String newRoot = transactionIds.get(originalRoot);
        if (newRoot == null) {
          throw new IllegalArgumentException("snapshot correction root is missing from the export");
        }
        String reversal = source.reversalOfTransactionId() == null ? null : transactionIds.get(source.reversalOfTransactionId());
        if (source.reversalOfTransactionId() != null && reversal == null) {
          throw new IllegalArgumentException("snapshot reversal target is missing from the export");
        }
        String operationGroup = mapOptionalUlid(source.operationGroupKey(), operationGroups);
        List<LedgerPostingFact> postings = postings(source.postings(), accountIds);
        List<LedgerTradeDetail> tradeDetails = trades(source.tradeDetails());
        LedgerTransactionType transactionType = LedgerTransactionType.valueOf(requireText(source.transactionType(), "transactionType"));
        String newTransactionId = transactionIds.get(source.transactionId());
        LedgerTransaction transaction = new LedgerTransaction(newTransactionId, "RESTORE_OWNER_PLACEHOLDER", transactionType,
            LocalDate.parse(requireText(source.occurredOn(), "occurredOn")), LedgerSourceType.IMPORT, snapshotFileId,
            newRoot, reversal, nonNegativeInt(source.revisionNo(), "revisionNo"), 1L, source.note(), postings, tradeDetails);
        CorporateActionDetail corporateAction = corporateAction(source.corporateAction(), newTransactionId);
        IncomeDetail income = income(source.income(), newTransactionId);
        transactions.add(new RestoredTransaction(originalVersion, source.strategyKey(), operationGroup, transaction,
            corporateAction, income, tradeInstrumentIds(tradeDetails), corporateAction == null ? null
                : corporateAction.instrumentId(), income == null ? null : income.instrumentId()));
      }
      transactions.sort(Comparator.comparingLong(RestoredTransaction::sourceLedgerVersion));
      for (int index = 0; index < transactions.size(); index++) {
        if (transactions.get(index).sourceLedgerVersion() != index + 1L) {
          throw new IllegalArgumentException("snapshot ledger versions must start at one and be contiguous");
        }
      }
      if (sourceLedgerVersion != transactions.size()) {
        throw new IllegalArgumentException("snapshot sourceLedgerVersion does not match its transaction stream");
      }
      return new RestorePlan(accounts, transactions);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("ledger snapshot JSON cannot be parsed safely", exception);
    }
  }

  private void validateInstruments(RestorePlan plan) {
    Set<String> references = new HashSet<>();
    for (RestoredTransaction transaction : plan.transactions()) {
      references.addAll(transaction.tradeInstrumentIds());
      if (transaction.corporateActionInstrumentId() != null) references.add(transaction.corporateActionInstrumentId());
      if (transaction.incomeInstrumentId() != null) references.add(transaction.incomeInstrumentId());
    }
    for (String instrumentId : references) {
      if (instrumentPort.findById(instrumentId).isEmpty()) {
        throw new IllegalArgumentException("snapshot references a market instrument that is not available locally: " + instrumentId);
      }
    }
  }

  private List<LedgerPostingFact> postings(List<LedgerExportDocument.Posting> source,
      Map<String, String> accountIds) {
    if (source == null) {
      throw new IllegalArgumentException("snapshot postings must not be null");
    }
    List<LedgerPostingFact> result = new ArrayList<>();
    for (LedgerExportDocument.Posting posting : source) {
      String accountId = accountIds.get(posting.accountId());
      if (accountId == null) {
        throw new IllegalArgumentException("snapshot posting references an unknown account");
      }
      result.add(new LedgerPostingFact(idGenerator.next(), accountId, positiveInt(posting.postingNo(), "postingNo"),
          PostingSide.valueOf(requireText(posting.postingSide(), "postingSide")),
          Money.of(positiveLong(posting.amountCent(), "amountCent"), CurrencyCode.of(requireText(posting.currency(), "posting currency")))));
    }
    return List.copyOf(result);
  }

  private List<LedgerTradeDetail> trades(List<LedgerExportDocument.TradeDetail> source) {
    if (source == null) {
      throw new IllegalArgumentException("snapshot tradeDetails must not be null");
    }
    List<LedgerTradeDetail> result = new ArrayList<>();
    for (LedgerExportDocument.TradeDetail detail : source) {
      result.add(new LedgerTradeDetail(idGenerator.next(), positiveInt(detail.detailNo(), "detailNo"),
          requireUlidText(detail.instrumentId(), "instrumentId"), PositionEffect.valueOf(requireText(detail.positionEffect(), "positionEffect")),
          new BigDecimal(requireText(detail.quantity(), "quantity")), optionalPositiveLong(detail.unitPriceCent(), "unitPriceCent"),
          optionalDecimal(detail.pricePoints(), "pricePoints"), optionalPositiveLong(detail.contractMultiplierCent(), "contractMultiplierCent"),
          optionalDate(detail.deliveryDate(), "deliveryDate"), nonNegativeLong(detail.feeCent(), "feeCent"),
          optionalPositiveLong(detail.optionContractMultiplier(), "optionContractMultiplier")));
    }
    return List.copyOf(result);
  }

  private CorporateActionDetail corporateAction(LedgerExportDocument.CorporateAction source, String transactionId) {
    if (source == null) return null;
    return new CorporateActionDetail(idGenerator.next(), transactionId, requireUlidText(source.instrumentId(), "corporate action instrument"),
        CorporateActionType.valueOf(requireText(source.actionType(), "corporate action type")),
        LocalDate.parse(requireText(source.effectiveOn(), "corporate action date")),
        positiveLong(source.ratioNumerator(), "ratioNumerator"), positiveLong(source.ratioDenominator(), "ratioDenominator"));
  }

  private IncomeDetail income(LedgerExportDocument.Income source, String transactionId) {
    if (source == null) return null;
    String incomeType = requireText(source.incomeType(), "incomeType");
    return new IncomeDetail(idGenerator.next(), transactionId, incomeType,
        source.instrumentId() == null ? null : requireUlidText(source.instrumentId(), "income instrument"),
        optionalDate(source.entitlementDate(), "income entitlementDate"), positiveLong(source.grossAmountCent(), "grossAmountCent"),
        nonNegativeLong(source.taxWithheldCent(), "taxWithheldCent"),
        optionalPositiveLong(source.perShareAmountCent(), "perShareAmountCent"),
        CurrencyCode.of(requireText(source.currency(), "income currency")));
  }

  private static List<String> tradeInstrumentIds(List<LedgerTradeDetail> trades) {
    return trades.stream().map(LedgerTradeDetail::instrumentId).toList();
  }

  private String mapOptionalUlid(String value, Map<String, String> mapped) {
    if (value == null) return null;
    requireUlid(value, "operationGroupKey");
    return mapped.computeIfAbsent(value, ignored -> idGenerator.next());
  }

  private static String rewriteAccountReferences(String accountCode, Map<String, String> accountIds) {
    String rewritten = accountCode;
    for (Map.Entry<String, String> entry : accountIds.entrySet()) {
      rewritten = rewritten.replace(entry.getKey(), entry.getValue());
    }
    return rewritten;
  }

  private static void requireUlid(String value, String field) {
    if (value == null || !value.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException(field + " must be a ULID");
    }
  }

  private static String requireUlidText(String value, String field) {
    requireUlid(value, field);
    return value;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static long positiveLong(String value, String field) {
    long parsed = parseLong(value, field);
    if (parsed < 1) throw new IllegalArgumentException(field + " must be positive");
    return parsed;
  }

  private static long nonNegativeLong(String value, String field) {
    long parsed = parseLong(value, field);
    if (parsed < 0) throw new IllegalArgumentException(field + " must not be negative");
    return parsed;
  }

  private static int positiveInt(String value, String field) {
    long parsed = positiveLong(value, field);
    if (parsed > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " exceeds integer range");
    return (int) parsed;
  }

  private static int nonNegativeInt(String value, String field) {
    long parsed = nonNegativeLong(value, field);
    if (parsed > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " exceeds integer range");
    return (int) parsed;
  }

  private static long parseLong(String value, String field) {
    if (value == null || !value.matches("(?:0|[1-9][0-9]*)")) {
      throw new IllegalArgumentException(field + " must be a decimal integer string");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " exceeds long range", exception);
    }
  }

  private static Long optionalPositiveLong(String value, String field) {
    return value == null ? null : positiveLong(value, field);
  }

  private static BigDecimal optionalDecimal(String value, String field) {
    if (value == null) return null;
    try {
      BigDecimal parsed = new BigDecimal(value);
      if (parsed.signum() <= 0 || parsed.scale() > 8) throw new IllegalArgumentException(field + " is invalid");
      return parsed;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " is invalid", exception);
    }
  }

  private static LocalDate optionalDate(String value, String field) {
    if (value == null) return null;
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(field + " is invalid", exception);
    }
  }

  private record RestorePlan(List<RestoredAccount> accounts, List<RestoredTransaction> transactions) { }

  private record RestoredAccount(String sourceAccountId, LedgerAccount prototype) {
    LedgerAccount account(String ownerUserId) {
      return new LedgerAccount(prototype.accountId(), ownerUserId, prototype.accountCode(), prototype.accountKind(),
          prototype.currency(), prototype.displayName(), prototype.status(), prototype.version());
    }
  }

  private record RestoredTransaction(long sourceLedgerVersion, String strategyKey, String operationGroupKey,
                                     LedgerTransaction prototype, CorporateActionDetail corporateAction,
                                     IncomeDetail income, List<String> tradeInstrumentIds,
                                     String corporateActionInstrumentId, String incomeInstrumentId) {
    LedgerTransaction transaction(String ownerUserId, long ledgerVersion) {
      return new LedgerTransaction(prototype.transactionId(), ownerUserId, prototype.transactionType(),
          prototype.occurredOn(), prototype.sourceType(), prototype.importExportFileId(),
          prototype.correctionRootTransactionId(), prototype.reversalOfTransactionId(), prototype.revisionNo(),
          ledgerVersion, prototype.note(), prototype.postings(), prototype.tradeDetails());
    }
  }
}
