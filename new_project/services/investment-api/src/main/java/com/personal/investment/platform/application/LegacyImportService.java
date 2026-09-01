package com.personal.investment.platform.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.application.LedgerCommandAccountPort;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts the old Dashboard entry stream into immutable, checksum-protected command rows. Preview never appends a
 * ledger fact; confirmation replays only this persisted payload and marks every resulting transaction as IMPORT.
 */
@Service
public class LegacyImportService {
  private static final Duration PREVIEW_TTL = Duration.ofHours(24);

  private final ImportExportFilePort filePort;
  private final UploadedObjectStoragePort storagePort;
  private final LegacyImportPreviewPort previewPort;
  private final LedgerIdGenerator idGenerator;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final LedgerCommandAccountPort accountPort;
  private final LegacyImportLedgerAppender ledgerAppender;
  private final LegacyImportDryRunPort dryRunPort;
  private final LegacyImportSnapshotParser snapshotParser;

  public LegacyImportService(ImportExportFilePort filePort, UploadedObjectStoragePort storagePort,
      LegacyImportPreviewPort previewPort, LedgerIdGenerator idGenerator, ObjectMapper objectMapper, Clock clock,
      LedgerCommandAccountPort accountPort, LegacyImportLedgerAppender ledgerAppender, LegacyImportDryRunPort dryRunPort) {
    this.filePort = filePort;
    this.storagePort = storagePort;
    this.previewPort = previewPort;
    this.idGenerator = idGenerator;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.accountPort = accountPort;
    this.ledgerAppender = ledgerAppender;
    this.dryRunPort = dryRunPort;
    this.snapshotParser = new LegacyImportSnapshotParser();
  }

  /** Local deployment executes the bounded preview synchronously but exposes it as a durable job resource. */
  @Transactional
  public LegacyImportPreview createPreview(String ownerUserId, CreateLegacyImportPreviewCommand command) {
    requireOwner(ownerUserId);
    Objects.requireNonNull(command, "command must not be null");
    ImportExportFile file = filePort.findOwned(ownerUserId, command.importExportFileId())
        .orElseThrow(() -> new IllegalArgumentException("import file was not found"));
    if (file.direction() != ImportExportFileDirection.IMPORT || (file.status() != ImportExportFileStatus.SCANNED
        && file.status() != ImportExportFileStatus.PREVIEWED)) {
      throw new IllegalArgumentException("import file must be scanned before creating a preview");
    }
    validateMappings(command);
    String mappingJson = json(command);
    String mappingSha256Hex = sha256Hex(mappingJson.getBytes(StandardCharsets.UTF_8));
    String previewId = idGenerator.next();
    String jobId = idGenerator.next();
    Instant now = Instant.now(clock);
    LegacyImportPreviewPayload payload;
    LegacyImportPreviewStatus status;
    String sourceSnapshotId = command.snapshotId();
    try {
      UploadedObject object = storagePort.read(file.objectKey());
      verifyContentHash(file, object.content());
      LegacyImportSnapshot snapshot = command.format() == LegacyImportFormat.LEGACY_DASHBOARD_JSON
          ? snapshotParser.parseJson(object.content())
          : snapshotParser.parseSqlite(object.content(), command.snapshotId());
      sourceSnapshotId = snapshot.sourceSnapshotId();
      ConversionResult converted = convert(ownerUserId, snapshot, command);
      LegacyImportDryRunResult dryRun = dryRunPort.validate(ownerUserId, file.importExportFileId(), converted.lines());
      payload = bindChecksumContext(dryRun.succeeded() ? converted
          : converted.withRejected(dryRun.rejectedSourceRow(), dryRun.message()), file, snapshot.sourceSnapshotId(),
          mappingSha256Hex);
      status = payload.needsReviewCount() == 0 ? LegacyImportPreviewStatus.SUCCEEDED
          : LegacyImportPreviewStatus.NEEDS_REVIEW;
    } catch (RuntimeException exception) {
      payload = new LegacyImportPreviewPayload(file.importExportFileId(), file.contentSha256Hex(), sourceSnapshotId,
          mappingSha256Hex, List.of(new LegacyImportPreviewLine(0, "FAILED", "PARSE_OR_VERIFY_FAILED", null, null,
              null, null, null, null, null, null, null, null, null, safeMessage(exception))), 0, 1);
      status = LegacyImportPreviewStatus.FAILED;
    }
    String previewJson = json(payload);
    LegacyImportPreview preview = new LegacyImportPreview(previewId, ownerUserId, jobId, file.importExportFileId(),
        command.format(), sourceSnapshotId, mappingJson, previewJson, sha256Hex(previewJson.getBytes(StandardCharsets.UTF_8)),
        status, now.plus(PREVIEW_TTL), now);
    previewPort.expireUncommitted(ownerUserId, file.importExportFileId());
    previewPort.append(preview);
    if (file.status() == ImportExportFileStatus.SCANNED) {
      filePort.transition(ownerUserId, file.importExportFileId(), ImportExportFileStatus.SCANNED,
          ImportExportFileStatus.PREVIEWED);
    }
    return preview;
  }

  public LegacyImportPreview findJob(String ownerUserId, String jobId) {
    requireOwner(ownerUserId);
    return previewPort.findOwned(ownerUserId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("import job was not found"));
  }

  @Transactional
  public LegacyImportPreview confirm(String ownerUserId, String jobId, String expectedChecksumHex) {
    requireOwner(ownerUserId);
    if (expectedChecksumHex == null || !expectedChecksumHex.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("expectedChecksum must be a lowercase SHA-256 hex value");
    }
    LegacyImportPreview preview = previewPort.lockOwned(ownerUserId, jobId)
        .orElseThrow(() -> new IllegalArgumentException("import job was not found"));
    if (preview.status() != LegacyImportPreviewStatus.SUCCEEDED) {
      throw new IllegalArgumentException("only a successful import preview may be confirmed");
    }
    if (!Instant.now(clock).isBefore(preview.expiresAt())) {
      throw new IllegalArgumentException("import preview has expired; create a new preview");
    }
    String actualChecksum = sha256Hex(preview.previewJson().getBytes(StandardCharsets.UTF_8));
    if (!actualChecksum.equals(preview.previewChecksumHex()) || !actualChecksum.equals(expectedChecksumHex)) {
      throw new IllegalArgumentException("import preview checksum does not match");
    }
    ImportExportFile file = filePort.findOwned(ownerUserId, preview.importExportFileId())
        .orElseThrow(() -> new IllegalArgumentException("import evidence file was not found"));
    if (file.status() != ImportExportFileStatus.PREVIEWED) {
      throw new IllegalArgumentException("import evidence file is no longer confirmable");
    }
    LegacyImportPreviewPayload payload = read(preview.previewJson(), LegacyImportPreviewPayload.class);
    if (!payload.importExportFileId().equals(preview.importExportFileId())
        || !payload.evidenceSha256Hex().equals(file.contentSha256Hex())
        || !payload.mappingSha256Hex().equals(sha256Hex(preview.mappingJson().getBytes(StandardCharsets.UTF_8)))
        || !Objects.equals(payload.sourceSnapshotId(), preview.sourceSnapshotId())) {
      throw new IllegalArgumentException("import preview context does not match its immutable evidence");
    }
    if (payload.needsReviewCount() != 0) {
      throw new IllegalArgumentException("import preview contains unresolved rows");
    }
    payload.lines().stream().filter(line -> "APPLICABLE".equals(line.status()))
        .sorted(Comparator.comparing(LegacyImportPreviewLine::occurredOn).thenComparingInt(LegacyImportPreviewLine::sourceRow))
        .forEach(line -> ledgerAppender.append(ownerUserId, preview.importExportFileId(), line));
    previewPort.markCommitted(ownerUserId, jobId);
    filePort.transition(ownerUserId, preview.importExportFileId(), ImportExportFileStatus.PREVIEWED,
        ImportExportFileStatus.COMMITTED);
    return findJob(ownerUserId, jobId);
  }

  private ConversionResult convert(String ownerUserId, LegacyImportSnapshot snapshot,
      CreateLegacyImportPreviewCommand command) {
    MappingIndex mappings = MappingIndex.of(command);
    List<LegacyImportPreviewLine> lines = new ArrayList<>();
    int applicable = 0;
    int review = 0;
    for (LegacyImportEntry entry : snapshot.entries()) {
      LegacyImportPreviewLine line = convertEntry(ownerUserId, entry, mappings);
      lines.add(line);
      if ("APPLICABLE".equals(line.status())) applicable++;
      if ("NEEDS_REVIEW".equals(line.status())) review++;
    }
    return new ConversionResult(lines, applicable, review);
  }

  private static LegacyImportPreviewPayload bindChecksumContext(ConversionResult converted,
      ImportExportFile file, String sourceSnapshotId, String mappingSha256Hex) {
    return new LegacyImportPreviewPayload(file.importExportFileId(), file.contentSha256Hex(), sourceSnapshotId,
        mappingSha256Hex, converted.lines(), converted.applicableCount(), converted.needsReviewCount());
  }

  private LegacyImportPreviewLine convertEntry(String ownerUserId, LegacyImportEntry entry, MappingIndex mappings) {
    try {
      LocalDate occurredOn = date(entry.field("date"), entry.sourceRow(), "date");
      String note = optionalText(entry.field("note"));
      if (note != null && note.length() > 1_000) {
        return review(entry, "NOTE_TOO_LONG", "legacy note exceeds 1000 characters");
      }
      LegacyCurrencyMapping currency = mappings.currency(entry.module(), entry.action());
      if (currency == null) return review(entry, "MISSING_CURRENCY_MAPPING", "cash account and amount unit are required");
      validateCashAccount(ownerUserId, currency);
      String cashAccountId = currency.cashAccountId();
      String module = entry.module();
      String action = entry.action();
      String feeCent = moneyOrZero(entry, "fee", currency);
      if ("cash".equals(module) && "deposit".equals(action)) {
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "funding fee requires a separate source row");
        return applicable(entry, LegacyImportOperation.EXTERNAL_FUNDING, occurredOn, cashAccountId, null, null, null,
            null, amount(entry, "amount", currency), null, feeCent, null, note);
      }
      if ("cash".equals(module) && "withdraw".equals(action)) {
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "withdrawal fee requires a separate source row");
        return applicable(entry, LegacyImportOperation.EXTERNAL_WITHDRAWAL, occurredOn, cashAccountId, null, null, null,
            null, amount(entry, "amount", currency), null, feeCent, null, note);
      }
      if (("dividend".equals(module) || "qqq".equals(module)) && ("buy".equals(action) || "sell".equals(action))) {
        String instrumentId = mappings.instrument(module, symbol(entry));
        if (instrumentId == null) return review(entry, "MISSING_INSTRUMENT_MAPPING", "spot trade requires instrument mapping");
        return applicable(entry, "buy".equals(action) ? LegacyImportOperation.SPOT_BUY : LegacyImportOperation.SPOT_SELL,
            occurredOn, cashAccountId, instrumentId, quantity(entry), unitPriceCent(entry), null, null, null,
            feeCent, null, note);
      }
      if (("dividend".equals(module) || "qqq".equals(module)) && "dividend".equals(action)) {
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "dividend fee requires a separate source row");
        String instrumentId = mappings.instrument(module, symbol(entry));
        if (instrumentId == null) return review(entry, "MISSING_INSTRUMENT_MAPPING", "dividend requires instrument mapping");
        LocalDate entitlement = mappings.entitlement(entry.sourceRow());
        if (entitlement == null) return review(entry, "MISSING_ENTITLEMENT_OVERRIDE", "dividend requires entitlement date");
        return applicable(entry, LegacyImportOperation.DIVIDEND, occurredOn, cashAccountId, instrumentId, null, null,
            null, amount(entry, "amount", currency), null, feeCent, entitlement.toString(), note);
      }
      if (("dividend".equals(module) || "qqq".equals(module)) && "interest".equals(action)) {
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "interest fee requires a separate source row");
        return applicable(entry, LegacyImportOperation.INTEREST, occurredOn, cashAccountId, null, null, null, null,
            amount(entry, "amount", currency), null, feeCent, null, note);
      }
      if ("put".equals(module) && ("buy".equals(action) || "sell".equals(action))) {
        String instrumentId = mappings.instrument(module, symbol(entry));
        if (instrumentId == null) return review(entry, "MISSING_INSTRUMENT_MAPPING", "option trade requires instrument mapping");
        return applicable(entry, "buy".equals(action) ? LegacyImportOperation.OPTION_OPEN : LegacyImportOperation.OPTION_CLOSE,
            occurredOn, cashAccountId, instrumentId, quantity(entry), unitPriceCent(entry), null, null, null,
            feeCent, null, note);
      }
      if ("put".equals(module) && "expire".equals(action)) {
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "option expiry fee requires a separate source row");
        String instrumentId = mappings.instrument(module, symbol(entry));
        if (instrumentId == null) return review(entry, "MISSING_INSTRUMENT_MAPPING", "option expiry requires instrument mapping");
        if (!mappings.attestedWorthless(entry.sourceRow())) {
          return review(entry, "MISSING_EXPIRY_ATTESTATION", "option expiry must be attested WORTHLESS");
        }
        return applicable(entry, LegacyImportOperation.OPTION_EXPIRE_ALL, occurredOn, cashAccountId, instrumentId, null,
            null, null, null, null, feeCent, null, note);
      }
      if ("ic".equals(module) && "futures_deposit".equals(action)) {
        if (currency.currency() != CurrencyCode.CNY) return review(entry, "FUTURES_REQUIRES_CNY", "CFFEX futures require CNY cash");
        if (!"0".equals(feeCent)) return review(entry, "UNSUPPORTED_FEE_FIELD", "futures deposit fee requires a separate source row");
        return applicable(entry, LegacyImportOperation.FUTURES_MARGIN_IN, occurredOn, cashAccountId, null, null, null,
            null, amount(entry, "amount", currency), null, feeCent, null, note);
      }
      if ("ic".equals(module) && ("buy".equals(action) || "sell".equals(action))) {
        if (currency.currency() != CurrencyCode.CNY) return review(entry, "FUTURES_REQUIRES_CNY", "CFFEX futures require CNY cash");
        String instrumentId = mappings.instrument(module, symbol(entry));
        if (instrumentId == null) return review(entry, "MISSING_INSTRUMENT_MAPPING", "futures trade requires instrument mapping");
        LegacyImportOperation operation = "buy".equals(action) ? LegacyImportOperation.FUTURES_OPEN
            : LegacyImportOperation.FUTURES_CLOSE;
        return applicable(entry, operation, occurredOn, cashAccountId, instrumentId, quantity(entry), null,
            positiveDecimal(entry.field("price"), entry.sourceRow(), "price").toPlainString(), null,
            operation == LegacyImportOperation.FUTURES_OPEN ? amount(entry, "margin", currency) : null,
            feeCent, null, note);
      }
      if ("ic".equals(module) && "roll".equals(action)) {
        if (currency.currency() != CurrencyCode.CNY) return review(entry, "FUTURES_REQUIRES_CNY", "CFFEX futures require CNY cash");
        if ("0".equals(feeCent)) return skipped(entry, "ROLL_WITHOUT_FEE", "confirmed roll is fee-only; row has no fee");
        return new LegacyImportPreviewLine(entry.sourceRow(), "APPLICABLE", "ROLL_FEE_ONLY", LegacyImportOperation.FEE,
            occurredOn.toString(), cashAccountId, null, null, null, null, feeCent, null, feeCent, null,
            appendNote(note, "legacy roll: fee-only"));
      }
      return review(entry, "UNSUPPORTED_ACTION", "no lossless accounting mapping exists for this module/action");
    } catch (IllegalArgumentException exception) {
      return review(entry, "INVALID_FIELD", safeMessage(exception));
    }
  }

  private void append(String ownerUserId, String importExportFileId, LegacyImportPreviewLine line) {
    ledgerAppender.append(ownerUserId, importExportFileId, line);
  }

  private static LegacyImportPreviewLine applicable(LegacyImportEntry entry, LegacyImportOperation operation,
      LocalDate occurredOn, String cashAccountId, String instrumentId, String quantity, String unitPriceCent,
      String pricePoints, String amountCent, String initialMarginCent, String feeCent, String entitlementDate,
      String note) {
    return new LegacyImportPreviewLine(entry.sourceRow(), "APPLICABLE", null, operation, occurredOn.toString(),
        cashAccountId, instrumentId, quantity, unitPriceCent, pricePoints, amountCent, initialMarginCent, feeCent,
        entitlementDate, note);
  }

  private static LegacyImportPreviewLine review(LegacyImportEntry entry, String code, String note) {
    return new LegacyImportPreviewLine(entry.sourceRow(), "NEEDS_REVIEW", code, null, null, null, null, null,
        null, null, null, null, null, null, note);
  }

  private static LegacyImportPreviewLine skipped(LegacyImportEntry entry, String code, String note) {
    return new LegacyImportPreviewLine(entry.sourceRow(), "SKIPPED", code, null, null, null, null, null,
        null, null, null, null, null, null, note);
  }

  private static String amount(LegacyImportEntry entry, String field, LegacyCurrencyMapping mapping) {
    BigDecimal value = convertMoney(decimal(entry.field(field), entry.sourceRow(), field), mapping.amountUnit());
    if (value.signum() <= 0) throw new IllegalArgumentException("legacy row " + entry.sourceRow() + " " + field + " must be positive");
    return value.toString();
  }

  private static String moneyOrZero(LegacyImportEntry entry, String field, LegacyCurrencyMapping mapping) {
    JsonNode node = entry.field(field);
    if (node.isMissingNode() || node.isNull() || (node.isTextual() && node.asText().isBlank())) return "0";
    return convertMoney(decimal(node, entry.sourceRow(), field), mapping.amountUnit()).toString();
  }

  private static String unitPriceCent(LegacyImportEntry entry) {
    BigDecimal value = convertMoney(decimal(entry.field("price"), entry.sourceRow(), "price"),
        LegacyAmountUnit.ORIGINAL_CURRENCY_DECIMAL);
    if (value.signum() <= 0) throw new IllegalArgumentException("legacy row " + entry.sourceRow() + " price must be positive");
    return value.toString();
  }

  private static BigDecimal convertMoney(BigDecimal decimal, LegacyAmountUnit unit) {
    try {
      BigDecimal factor = unit == LegacyAmountUnit.LEGACY_CNY_WAN ? BigDecimal.valueOf(1_000_000L)
          : BigDecimal.valueOf(100L);
      BigDecimal result = decimal.multiply(factor).setScale(0, RoundingMode.UNNECESSARY);
      if (result.signum() < 0) throw new IllegalArgumentException("legacy money must not be negative");
      return result;
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("legacy money cannot be represented as an exact minor-unit integer", exception);
    }
  }

  private static String quantity(LegacyImportEntry entry) {
    BigDecimal quantity = decimal(entry.field("quantity"), entry.sourceRow(), "quantity");
    if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 8) {
      throw new IllegalArgumentException("legacy quantity must be positive with at most 8 decimal places");
    }
    return quantity.stripTrailingZeros().toPlainString();
  }

  private static BigDecimal decimal(JsonNode node, int row, String field) {
    if (node == null || node.isMissingNode() || node.isNull() || !(node.isNumber() || node.isTextual())
        || node.asText().isBlank()) {
      throw new IllegalArgumentException("legacy row " + row + " " + field + " must be present");
    }
    try {
      return new BigDecimal(node.asText());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("legacy row " + row + " " + field + " must be a decimal string", exception);
    }
  }

  private static BigDecimal positiveDecimal(JsonNode node, int row, String field) {
    BigDecimal value = decimal(node, row, field);
    if (value.signum() <= 0) throw new IllegalArgumentException("legacy row " + row + " " + field + " must be positive");
    return value;
  }

  private static LocalDate date(JsonNode node, int row, String field) {
    if (node == null || !node.isTextual()) throw new IllegalArgumentException("legacy row " + row + " date must be ISO-8601");
    try {
      return LocalDate.parse(node.asText());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("legacy row " + row + " date must be ISO-8601", exception);
    }
  }

  private static String symbol(LegacyImportEntry entry) {
    JsonNode node = entry.field("symbol");
    if (node == null || !node.isTextual() || node.asText().isBlank()) {
      throw new IllegalArgumentException("legacy row " + entry.sourceRow() + " symbol must be present");
    }
    return node.asText().trim().toUpperCase(Locale.ROOT);
  }

  private void validateCashAccount(String ownerUserId, LegacyCurrencyMapping mapping) {
    LedgerAccount account = accountPort.findByIdAndOwner(mapping.cashAccountId(), ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("mapped cash account was not found"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != mapping.currency()) {
      throw new IllegalArgumentException("mapped cash account must be active and match its declared currency");
    }
  }

  private static String optionalText(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
  }

  private static String appendNote(String note, String suffix) {
    return note == null || note.isBlank() ? suffix : note + " | " + suffix;
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || ownerUserId.isBlank()) throw new IllegalArgumentException("ownerUserId must not be blank");
  }

  private static String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }

  private void verifyContentHash(ImportExportFile file, byte[] content) {
    if (!sha256Hex(content).equals(file.contentSha256Hex())) {
      throw new IllegalArgumentException("scanned import evidence hash no longer matches its metadata");
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("import preview JSON serialization failed", exception);
    }
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("persisted import preview JSON is invalid", exception);
    }
  }

  private static String sha256Hex(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void validateMappings(CreateLegacyImportPreviewCommand command) {
    Map<String, LegacyCurrencyMapping> currencies = new HashMap<>();
    for (LegacyCurrencyMapping mapping : command.currencyMappings()) {
      String key = mapping.module() + "|" + (mapping.action() == null ? "*" : mapping.action());
      if (currencies.putIfAbsent(key, mapping) != null) throw new IllegalArgumentException("duplicate legacy currency mapping: " + key);
    }
    Map<String, LegacyInstrumentMapping> instruments = new HashMap<>();
    for (LegacyInstrumentMapping mapping : command.instrumentMappings()) {
      String key = mapping.module() + "|" + mapping.symbol();
      if (instruments.putIfAbsent(key, mapping) != null) throw new IllegalArgumentException("duplicate legacy instrument mapping: " + key);
    }
  }

  private record MappingIndex(Map<String, LegacyCurrencyMapping> currencies, Map<String, String> instruments,
                              Map<Integer, LocalDate> entitlements, Map<Integer, Boolean> expiryAttestations) {
    static MappingIndex of(CreateLegacyImportPreviewCommand command) {
      Map<String, LegacyCurrencyMapping> currencies = new HashMap<>();
      command.currencyMappings().forEach(mapping -> currencies.put(mapping.module() + "|"
          + (mapping.action() == null ? "*" : mapping.action()), mapping));
      Map<String, String> instruments = new HashMap<>();
      command.instrumentMappings().forEach(mapping -> instruments.put(mapping.module() + "|" + mapping.symbol(),
          mapping.instrumentId()));
      Map<Integer, LocalDate> entitlements = new HashMap<>();
      command.dividendEntitlementOverrides().forEach(override -> entitlements.put(override.sourceRow(),
          override.entitlementDate()));
      Map<Integer, Boolean> attestations = new HashMap<>();
      command.optionExpiryAttestations().forEach(attestation -> attestations.put(attestation.sourceRow(), Boolean.TRUE));
      return new MappingIndex(currencies, instruments, entitlements, attestations);
    }

    LegacyCurrencyMapping currency(String module, String action) {
      LegacyCurrencyMapping exact = currencies.get(module + "|" + action);
      return exact != null ? exact : currencies.get(module + "|*");
    }

    String instrument(String module, String symbol) {
      return instruments.get(module + "|" + symbol);
    }

    LocalDate entitlement(int sourceRow) {
      return entitlements.get(sourceRow);
    }

    boolean attestedWorthless(int sourceRow) {
      return expiryAttestations.containsKey(sourceRow);
    }
  }

  private record ConversionResult(List<LegacyImportPreviewLine> lines, int applicableCount, int needsReviewCount) {
    ConversionResult withRejected(Integer sourceRow, String message) {
      if (sourceRow == null) return this;
      List<LegacyImportPreviewLine> updated = lines.stream().map(line -> line.sourceRow() == sourceRow
          && "APPLICABLE".equals(line.status()) ? new LegacyImportPreviewLine(line.sourceRow(), "NEEDS_REVIEW",
              "LEDGER_DRY_RUN_REJECTED", null, line.occurredOn(), line.cashAccountId(), line.instrumentId(),
              line.quantity(), line.unitPriceCent(), line.pricePoints(), line.amountCent(), line.initialMarginCent(),
              line.feeCent(), line.entitlementDate(), message) : line).toList();
      return new ConversionResult(updated, applicableCount - 1, needsReviewCount + 1);
    }
  }
}
