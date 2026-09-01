package com.personal.investment.ledger.interfaces;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.personal.investment.identity.interfaces.MeController.SessionAuthenticationPrincipal;
import com.personal.investment.ledger.application.CashTransactionPreviewResult;
import com.personal.investment.ledger.application.CashTransactionPreviewService;
import com.personal.investment.ledger.application.CorporateActionCommand;
import com.personal.investment.ledger.application.CorporateActionService;
import com.personal.investment.ledger.application.CorporateActionType;
import com.personal.investment.ledger.application.CorporateActionRatioException;
import com.personal.investment.ledger.application.CorporateActionUnsupportedException;
import com.personal.investment.ledger.application.LedgerTransactionService;
import com.personal.investment.ledger.application.LedgerTransactionQueryService;
import com.personal.investment.ledger.application.LedgerCorrectionService;
import com.personal.investment.ledger.application.LedgerTransactionDetail;
import com.personal.investment.ledger.application.LedgerTransactionDetailService;
import com.personal.investment.ledger.application.LedgerAppendContext;
import com.personal.investment.ledger.application.LedgerAppendMetadata;
import com.personal.investment.ledger.application.FuturesMarginPreviewResult;
import com.personal.investment.ledger.application.FuturesMarginService;
import com.personal.investment.ledger.application.FuturesOpenCommand;
import com.personal.investment.ledger.application.FuturesOpenPreviewResult;
import com.personal.investment.ledger.application.FuturesOpenService;
import com.personal.investment.ledger.application.FuturesCloseCommand;
import com.personal.investment.ledger.application.FuturesClosePreviewResult;
import com.personal.investment.ledger.application.FuturesCloseService;
import com.personal.investment.ledger.application.FuturesDailySettlementCommand;
import com.personal.investment.ledger.application.FuturesDailySettlementPreviewResult;
import com.personal.investment.ledger.application.FuturesDailySettlementService;
import com.personal.investment.ledger.application.FuturesRollCommand;
import com.personal.investment.ledger.application.FuturesRollPreviewResult;
import com.personal.investment.ledger.application.FuturesRollResult;
import com.personal.investment.ledger.application.FuturesRollService;
import com.personal.investment.ledger.application.OptionExpiryCommand;
import com.personal.investment.ledger.application.OptionExpiryOutcome;
import com.personal.investment.ledger.application.OptionTradeCommand;
import com.personal.investment.ledger.application.OptionTradePreviewResult;
import com.personal.investment.ledger.application.OptionTradePreviewService;
import com.personal.investment.ledger.application.OptionTradeService;
import com.personal.investment.ledger.application.SpotTradeCommand;
import com.personal.investment.ledger.application.SpotTradePreviewResult;
import com.personal.investment.ledger.application.SpotTradePreviewService;
import com.personal.investment.ledger.application.SpotTradeResult;
import com.personal.investment.ledger.application.SpotTradeService;
import com.personal.investment.ledger.domain.LedgerTradeDetail;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.PositionEffect;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.platform.application.IdempotencyExecutor;
import com.personal.investment.platform.application.IdempotencyResponse;
import com.personal.investment.strategy.domain.StrategyKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ledger/transactions")
public class LedgerTransactionController {
  private static final String PATH = "/api/v1/ledger/transactions";

  private final LedgerTransactionService ledgerTransactionService;
  private final LedgerTransactionQueryService ledgerTransactionQueryService;
  private final LedgerCorrectionService ledgerCorrectionService;
  private final LedgerTransactionDetailService ledgerTransactionDetailService;
  private final CashTransactionPreviewService cashTransactionPreviewService;
  private final SpotTradeService spotTradeService;
  private final SpotTradePreviewService spotTradePreviewService;
  private final CorporateActionService corporateActionService;
  private final FuturesMarginService futuresMarginService;
  private final FuturesOpenService futuresOpenService;
  private final FuturesCloseService futuresCloseService;
  private final FuturesDailySettlementService futuresDailySettlementService;
  private final FuturesRollService futuresRollService;
  private final OptionTradeService optionTradeService;
  private final OptionTradePreviewService optionTradePreviewService;
  private final IdempotencyExecutor idempotencyExecutor;

  public LedgerTransactionController(LedgerTransactionService ledgerTransactionService,
      LedgerTransactionQueryService ledgerTransactionQueryService,
      LedgerCorrectionService ledgerCorrectionService,
      LedgerTransactionDetailService ledgerTransactionDetailService,
      CashTransactionPreviewService cashTransactionPreviewService,
      SpotTradeService spotTradeService, SpotTradePreviewService spotTradePreviewService,
      CorporateActionService corporateActionService,
      FuturesMarginService futuresMarginService,
      FuturesOpenService futuresOpenService,
      FuturesCloseService futuresCloseService,
      FuturesDailySettlementService futuresDailySettlementService,
      FuturesRollService futuresRollService,
      OptionTradeService optionTradeService,
      OptionTradePreviewService optionTradePreviewService,
      IdempotencyExecutor idempotencyExecutor) {
    this.ledgerTransactionService = ledgerTransactionService;
    this.ledgerTransactionQueryService = ledgerTransactionQueryService;
    this.ledgerCorrectionService = ledgerCorrectionService;
    this.ledgerTransactionDetailService = ledgerTransactionDetailService;
    this.cashTransactionPreviewService = cashTransactionPreviewService;
    this.spotTradeService = spotTradeService;
    this.spotTradePreviewService = spotTradePreviewService;
    this.corporateActionService = corporateActionService;
    this.futuresMarginService = futuresMarginService;
    this.futuresOpenService = futuresOpenService;
    this.futuresCloseService = futuresCloseService;
    this.futuresDailySettlementService = futuresDailySettlementService;
    this.futuresRollService = futuresRollService;
    this.optionTradeService = optionTradeService;
    this.optionTradePreviewService = optionTradePreviewService;
    this.idempotencyExecutor = idempotencyExecutor;
  }

  @PostMapping
  public ResponseEntity<?> create(
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody TransactionCreateRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    String strategyKey = optionalStrategyKey(request);
    if (isFuturesRoll(request)) {
      if (!"IC_IM".equals(strategyKey)) {
        throw new TransactionFieldsException("futures roll requires strategyKey IC_IM");
      }
      IdempotencyResponse<FuturesRollResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", PATH, key,
          canonical(request), FuturesRollResponse.class, () -> {
            FuturesRollResult result = LedgerAppendMetadata.withStrategyKey(strategyKey,
                () -> futuresRollService.roll(ownerUserId, parseFuturesRoll(request)));
            return new IdempotencyResponse<>(HttpStatus.CREATED.value(), new FuturesRollResponse(
                result.operationGroupKey(), List.of(toResponse(result.closeResult().transaction()),
                    toResponse(result.openResult().transaction()))));
          });
      return ResponseEntity.status(response.status()).body(response.body());
    }
    IdempotencyResponse<TransactionResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", PATH, key,
        canonical(request), TransactionResponse.class, () -> new IdempotencyResponse<>(HttpStatus.CREATED.value(),
            LedgerAppendMetadata.withStrategyKey(strategyKey, () -> toResponse(execute(ownerUserId, request)))));
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @PostMapping("/{transactionId}/corrections")
  public ResponseEntity<CorrectionResponse> correct(@PathVariable String transactionId,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody(required = false) CorrectionRequest request, Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    String path = PATH + "/" + transactionId + "/corrections";
    TransactionCreateRequest replacementRequest = request == null ? null : request.replacement();
    IdempotencyResponse<CorrectionResponse> response = idempotencyExecutor.execute(ownerUserId, "POST", path, key,
        part(transactionId) + (replacementRequest == null ? part(null) : canonical(replacementRequest)),
        CorrectionResponse.class, () -> {
          var result = ledgerCorrectionService.correct(ownerUserId, transactionId, replacementRequest == null ? null
              : context -> {
                String requestedStrategyKey = optionalStrategyKey(replacementRequest);
                if (replacementRequest.strategyKey() != null
                    && !java.util.Objects.equals(requestedStrategyKey, context.strategyKey())) {
                  throw new TransactionFieldsException("replacement strategyKey must match the corrected transaction");
                }
                return executeReplacement(ownerUserId, replacementRequest, context);
              });
          LedgerTransaction reversal = result.reversal();
          return new IdempotencyResponse<>(HttpStatus.CREATED.value(), new CorrectionResponse(
              List.of(reversal.transactionId()), result.replacement() == null ? List.of()
                  : List.of(result.replacement().transactionId()), List.of(reversal.correctionRootTransactionId()),
              Long.toString(result.replacement() == null ? reversal.ledgerVersion() : result.replacement().ledgerVersion())));
        });
    return ResponseEntity.status(response.status()).body(response.body());
  }

  @GetMapping
  public TransactionListResponse list(@RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "30") int limit, @RequestParam(required = false) String accountId,
      @RequestParam(required = false) String instrumentId, @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) LedgerTransactionType transactionType,
      @RequestParam(required = false) String strategyKey,
      @RequestParam(required = false) @Size(max = 128) String search,
      Authentication authentication) {
    var page = ledgerTransactionQueryService.list(ownerUserId(authentication), cursor, limit, accountId, instrumentId,
        transactionType, strategyKey, search, from, to);
    return new TransactionListResponse(page.items().stream().map(item -> new TransactionSummaryResponse(
        item.transactionId(), item.transactionType(), item.occurredOn(),
        item.currency() == null ? null : item.currency().name(),
        Long.toString(item.ledgerVersion()), item.sourceType().name(), item.importExportFileId())).toList(), page.nextCursor());
  }

  /**
   * Read-only audit view. Corrections create an immutable reversal/replacement chain; this endpoint never exposes
   * a physical edit or delete operation.
   */
  @GetMapping("/{transactionId}")
  public ResponseEntity<TransactionDetailResponse> detail(@PathVariable String transactionId,
      Authentication authentication) {
    return ledgerTransactionDetailService.find(ownerUserId(authentication), transactionId)
        .map(LedgerTransactionController::toDetailResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/preview")
  public TransactionPreviewResponse preview(@Valid @RequestBody TransactionCreateRequest request,
      Authentication authentication) {
    String ownerUserId = ownerUserId(authentication);
    String strategyKey = optionalStrategyKey(request);
    if (isFuturesRoll(request)) {
      if (!"IC_IM".equals(strategyKey)) {
        throw new TransactionFieldsException("futures roll requires strategyKey IC_IM");
      }
      FuturesRollPreviewResult result = futuresRollService.preview(ownerUserId, parseFuturesRoll(request));
      return new TransactionPreviewResponse(draftHash(canonical(request)), "CNY",
          concat(previewPostings(result.closePreview().postings()), previewPostings(result.openPreview().postings())),
          List.of(new TradePreviewDetail(request.futuresRoll().closeLeg().instrumentId(), PositionEffect.CLOSE,
                  request.futuresRoll().closeLeg().quantity()),
              new TradePreviewDetail(request.futuresRoll().openLeg().instrumentId(), PositionEffect.OPEN,
                  request.futuresRoll().openLeg().quantity())), result.openPreview().accountProvisioning(),
          List.of("确认后会原子写入平旧仓和开新仓两条期货事实。"), result.proposedOperationGroupKey());
    }
    LedgerTransactionType type = transactionType(request);
    if (type == LedgerTransactionType.CORPORATE_ACTION) {
      corporateActionService.preview(ownerUserId, parseCorporateAction(request));
      return new TransactionPreviewResponse(draftHash(canonical(request)), null, List.of(), List.of(), List.of(),
          List.of());
    }
    if (type == LedgerTransactionType.FUTURES_MARGIN) {
      FuturesMarginPreviewResult result = futuresMarginService.previewByMinorUnit(ownerUserId,
          requiredFuturesMarginCashAccount(request), request.occurredOn(), requiredMarginDirection(request),
          requiredAmountCent(request), request.note());
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(), result.accountProvisioning(), List.of());
    }
    if (type == LedgerTransactionType.FUTURES_OPEN) {
      FuturesOpenCommand command = parseFuturesOpen(request);
      FuturesOpenPreviewResult result = futuresOpenService.preview(ownerUserId, command);
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(new TradePreviewDetail(command.instrumentId(),
              PositionEffect.OPEN, command.quantity().toPlainString())), result.accountProvisioning(), List.of());
    }
    if (type == LedgerTransactionType.FUTURES_CLOSE) {
      FuturesCloseCommand command = parseFuturesClose(request);
      FuturesClosePreviewResult result = futuresCloseService.preview(ownerUserId, command);
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(new TradePreviewDetail(command.instrumentId(),
          PositionEffect.CLOSE, command.quantity().toPlainString())), List.of(), List.of());
    }
    if (type == LedgerTransactionType.FUTURES_DAILY_SETTLEMENT) {
      FuturesDailySettlementCommand command = parseFuturesDailySettlement(request);
      FuturesDailySettlementPreviewResult result = futuresDailySettlementService.preview(ownerUserId, command);
      List<String> warnings = result.realizedPnlCent() == 0
          ? List.of("本次逐日结算损益为零，确认后仍会追加不可变结算事实。") : List.of();
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(), List.of(), warnings);
    }
    if (type == LedgerTransactionType.OPTION_OPEN || type == LedgerTransactionType.OPTION_CLOSE) {
      OptionTradeCommand command = parseOptionTrade(request, type);
      OptionTradePreviewResult result = optionTradePreviewService.preview(ownerUserId, type, command);
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(new TradePreviewDetail(command.instrumentId(),
              type == LedgerTransactionType.OPTION_OPEN ? PositionEffect.OPEN : PositionEffect.CLOSE,
              command.quantity().toPlainString())), result.accountProvisioning(), List.of());
    }
    if (type == LedgerTransactionType.OPTION_EXPIRE) {
      OptionExpiryCommand command = parseOptionExpiry(request);
      OptionTradePreviewResult result = optionTradePreviewService.previewExpiry(ownerUserId, command);
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(new TradePreviewDetail(command.instrumentId(),
              PositionEffect.CLOSE, command.quantity().toPlainString())), List.of(),
          List.of("已确认该期权在到期日无行权价值；提交后会核销全部剩余成本。"));
    }
    if (type == LedgerTransactionType.EXTERNAL_FUNDING || type == LedgerTransactionType.EXTERNAL_WITHDRAWAL
        || type == LedgerTransactionType.INTERNAL_TRANSFER || type == LedgerTransactionType.FEE) {
      boolean transfer = type == LedgerTransactionType.INTERNAL_TRANSFER;
      CashTransactionPreviewResult result = cashTransactionPreviewService.preview(ownerUserId, type,
          requiredCashAccount(request, transfer), transfer ? requiredDestinationAccount(request) : null,
          requiredAmountCent(request));
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(), List.of(), List.of());
    }
    if (type == LedgerTransactionType.DIVIDEND || type == LedgerTransactionType.INTEREST) {
      String cashAccountId = type == LedgerTransactionType.DIVIDEND
          ? requiredDividendCashAccount(request) : requiredInterestCashAccount(request);
      if (type == LedgerTransactionType.DIVIDEND) {
        requiredDividendInstrument(request);
        requiredEntitlementDate(request);
        ledgerTransactionService.previewDividendByMinorUnit(ownerUserId, cashAccountId, request.instrumentId(),
            request.occurredOn(), request.entitlementDate(), requiredAmountCent(request), optionalTaxCent(request),
            optionalPerShareAmountCent(request), request.note());
      }
      CashTransactionPreviewResult result = cashTransactionPreviewService.previewIncome(ownerUserId, type,
          cashAccountId, requiredAmountCent(request), optionalTaxCent(request));
      return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
          previewPostings(result.postings()), List.of(), List.of(), List.of());
    }
    ParsedSpotCommand parsed = parseSpot(request, type);
    SpotTradePreviewResult result = spotTradePreviewService.preview(ownerUserId, parsed.type(), parsed.command());
    return new TransactionPreviewResponse(draftHash(canonical(request)), result.currency().name(),
        previewPostings(result.postings()), List.of(new TradePreviewDetail(request.instrumentId(),
            parsed.type() == LedgerTransactionType.TRADE_BUY ? PositionEffect.OPEN : PositionEffect.CLOSE,
            parsed.command().quantity().toPlainString())), result.accountProvisioning(), List.of());
  }

  private LedgerTransaction execute(String ownerUserId, TransactionCreateRequest request) {
    LedgerTransactionType type = transactionType(request);
    return switch (type) {
      case TRADE_BUY, TRADE_SELL -> {
        ParsedSpotCommand parsed = parseSpot(request, type);
        SpotTradeResult result = type == LedgerTransactionType.TRADE_BUY
            ? spotTradeService.buy(ownerUserId, parsed.command())
            : spotTradeService.sell(ownerUserId, parsed.command());
        yield result.transaction();
      }
      case EXTERNAL_FUNDING -> ledgerTransactionService.externalFundingByMinorUnit(ownerUserId,
          requiredCashAccount(request, false), request.occurredOn(), requiredAmountCent(request), request.note());
      case EXTERNAL_WITHDRAWAL -> ledgerTransactionService.externalWithdrawalByMinorUnit(ownerUserId,
          requiredCashAccount(request, false), request.occurredOn(), requiredAmountCent(request), request.note());
      case INTERNAL_TRANSFER -> ledgerTransactionService.internalTransferByMinorUnit(ownerUserId,
          requiredCashAccount(request, true), requiredDestinationAccount(request), request.occurredOn(),
          requiredAmountCent(request), request.note());
      case FEE -> ledgerTransactionService.feeByMinorUnit(ownerUserId,
          requiredCashAccount(request, false), request.occurredOn(), requiredAmountCent(request), request.note());
      case DIVIDEND -> ledgerTransactionService.dividendByMinorUnit(ownerUserId,
          requiredDividendCashAccount(request), requiredDividendInstrument(request), request.occurredOn(),
          requiredEntitlementDate(request), requiredAmountCent(request), optionalTaxCent(request),
          optionalPerShareAmountCent(request), request.note());
      case INTEREST -> ledgerTransactionService.interestByMinorUnit(ownerUserId,
          requiredInterestCashAccount(request), request.occurredOn(), requiredAmountCent(request),
          optionalTaxCent(request), request.note());
      case CORPORATE_ACTION -> corporateActionService.apply(ownerUserId, parseCorporateAction(request));
      case FUTURES_MARGIN -> futuresMarginService.moveByMinorUnit(ownerUserId, requiredFuturesMarginCashAccount(request),
          request.occurredOn(), requiredMarginDirection(request), requiredAmountCent(request), request.note());
      case FUTURES_OPEN -> futuresOpenService.open(ownerUserId, parseFuturesOpen(request)).transaction();
      case FUTURES_CLOSE -> futuresCloseService.close(ownerUserId, parseFuturesClose(request)).transaction();
      case FUTURES_DAILY_SETTLEMENT -> futuresDailySettlementService.settle(ownerUserId,
          parseFuturesDailySettlement(request)).transaction();
      case OPTION_OPEN -> optionTradeService.open(ownerUserId, parseOptionTrade(request, type)).transaction();
      case OPTION_CLOSE -> optionTradeService.close(ownerUserId, parseOptionTrade(request, type)).transaction();
      case OPTION_EXPIRE -> optionTradeService.expire(ownerUserId, parseOptionExpiry(request)).transaction();
      default -> throw new TransactionFieldsException("transactionType is not implemented in this delivery slice");
    };
  }

  private LedgerTransaction executeReplacement(String ownerUserId, TransactionCreateRequest request,
      LedgerAppendContext context) {
    if (!ownerUserId.equals(context.ownerUserId())) {
      throw new IllegalArgumentException("replacement context owner does not match authenticated owner");
    }
    LedgerTransactionType type = transactionType(request);
    return switch (type) {
      case TRADE_BUY, TRADE_SELL -> {
        ParsedSpotCommand parsed = parseSpot(request, type);
        yield type == LedgerTransactionType.TRADE_BUY
            ? spotTradeService.buyReplacement(context, parsed.command()).transaction()
            : spotTradeService.sellReplacement(context, parsed.command()).transaction();
      }
      case EXTERNAL_FUNDING -> ledgerTransactionService.externalFundingReplacementByMinorUnit(context,
          requiredCashAccount(request, false), request.occurredOn(), requiredAmountCent(request), request.note());
      case EXTERNAL_WITHDRAWAL -> ledgerTransactionService.externalWithdrawalReplacementByMinorUnit(context,
          requiredCashAccount(request, false), request.occurredOn(), requiredAmountCent(request), request.note());
      case INTERNAL_TRANSFER -> ledgerTransactionService.internalTransferReplacementByMinorUnit(context,
          requiredCashAccount(request, true), requiredDestinationAccount(request), request.occurredOn(),
          requiredAmountCent(request), request.note());
      case FEE -> ledgerTransactionService.feeReplacementByMinorUnit(context, requiredCashAccount(request, false),
          request.occurredOn(), requiredAmountCent(request), request.note());
      case DIVIDEND -> ledgerTransactionService.dividendReplacementByMinorUnit(context,
          requiredDividendCashAccount(request), requiredDividendInstrument(request), request.occurredOn(),
          requiredEntitlementDate(request), requiredAmountCent(request), optionalTaxCent(request),
          optionalPerShareAmountCent(request), request.note());
      case INTEREST -> ledgerTransactionService.interestReplacementByMinorUnit(context,
          requiredInterestCashAccount(request), request.occurredOn(), requiredAmountCent(request),
          optionalTaxCent(request), request.note());
      case CORPORATE_ACTION -> corporateActionService.applyReplacement(context, parseCorporateAction(request));
      default -> throw new TransactionFieldsException("replacement transactionType is not implemented in this delivery slice");
    };
  }

  private static String requiredFuturesMarginCashAccount(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank() || request.marginDirection() == null
        || request.destinationAccountId() != null || request.instrumentId() != null || request.quantity() != null
        || request.unitPriceCent() != null || request.pricePoints() != null || request.settlementPricePoints() != null
        || request.initialMarginCent() != null
        || request.feeCent() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.corporateAction() != null) {
      throw new TransactionFieldsException("futures margin requires cashAccountId, amountCent and marginDirection only");
    }
    return request.cashAccountId();
  }

  private static MarginDirection requiredMarginDirection(TransactionCreateRequest request) {
    try {
      return MarginDirection.valueOf(request.marginDirection());
    } catch (IllegalArgumentException exception) {
      throw new TransactionFieldsException("marginDirection must be IN or OUT");
    }
  }

  private static ParsedSpotCommand parseSpot(TransactionCreateRequest request, LedgerTransactionType type) {
    if (type != LedgerTransactionType.TRADE_BUY && type != LedgerTransactionType.TRADE_SELL) {
      throw new TransactionFieldsException("spot preview only accepts TRADE_BUY or TRADE_SELL");
    }
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank()
        || request.quantity() == null || request.unitPriceCent() == null
        || request.amountCent() != null || request.destinationAccountId() != null
        || request.taxWithheldCent() != null || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.pricePoints() != null || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null
        || request.marginDirection() != null) {
      throw new TransactionFieldsException("spot trade requires cashAccountId, instrumentId, quantity and unit_price_cent only");
    }
    BigDecimal quantity = QuantityWireParser.parsePositive(request.quantity(), "quantity");
    long unitPriceCent = PositiveMinorUnitParser.parse(request.unitPriceCent(), "unit_price_cent");
    long feeCent = request.feeCent() == null ? 0 : parseOptionalFee(request.feeCent());
    return new ParsedSpotCommand(type, new SpotTradeCommand(request.cashAccountId(), request.instrumentId(),
        request.occurredOn(), quantity, unitPriceCent, feeCent, request.note()));
  }

  private static FuturesOpenCommand parseFuturesOpen(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank() || request.quantity() == null
        || request.pricePoints() == null || request.initialMarginCent() == null || request.amountCent() != null
        || request.destinationAccountId() != null || request.unitPriceCent() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null || request.corporateAction() != null
        || request.marginDirection() != null || request.settlementPricePoints() != null) {
      throw new TransactionFieldsException("futures open requires cashAccountId, instrumentId, quantity, pricePoints, initialMarginCent and optional feeCent only");
    }
    BigDecimal quantity = QuantityWireParser.parsePositive(request.quantity(), "quantity");
    BigDecimal pricePoints = QuantityWireParser.parsePositive(request.pricePoints(), "price_points");
    long initialMarginCent = PositiveMinorUnitParser.parse(request.initialMarginCent(), "initial_margin_cent");
    long feeCent = request.feeCent() == null ? 0 : parseOptionalFee(request.feeCent());
    return new FuturesOpenCommand(request.cashAccountId(), request.instrumentId(), request.occurredOn(), quantity,
        pricePoints, initialMarginCent, feeCent, request.note());
  }

  private static FuturesRollCommand parseFuturesRoll(TransactionCreateRequest request) {
    if (!isFuturesRoll(request) || request.futuresRoll() == null || request.cashAccountId() != null
        || request.destinationAccountId() != null || request.instrumentId() != null || request.quantity() != null
        || request.unitPriceCent() != null || request.pricePoints() != null || request.settlementPricePoints() != null
        || request.initialMarginCent() != null || request.feeCent() != null || request.amountCent() != null
        || request.taxWithheldCent() != null || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.corporateAction() != null || request.marginDirection() != null || request.expiryOutcome() != null) {
      throw new TransactionFieldsException("futures roll accepts only occurredOn, note, strategyKey and futuresRoll legs");
    }
    FuturesRollLegRequest close = request.futuresRoll().closeLeg();
    FuturesRollLegRequest open = request.futuresRoll().openLeg();
    if (close == null || open == null || blank(close.cashAccountId()) || blank(close.instrumentId())
        || blank(close.quantity()) || blank(close.pricePoints()) || blank(open.cashAccountId()) || blank(open.instrumentId())
        || blank(open.quantity()) || blank(open.pricePoints()) || blank(open.initialMarginCent())
        || close.initialMarginCent() != null || open.unitPriceCent() != null) {
      throw new TransactionFieldsException("futures roll requires complete close and open legs");
    }
    return new FuturesRollCommand(new FuturesCloseCommand(close.cashAccountId(), close.instrumentId(), request.occurredOn(),
        QuantityWireParser.parsePositive(close.quantity(), "futuresRoll.closeLeg.quantity"),
        QuantityWireParser.parsePositive(close.pricePoints(), "futuresRoll.closeLeg.pricePoints"),
        optionalFee(close.feeCent()), request.note()),
        new FuturesOpenCommand(open.cashAccountId(), open.instrumentId(), request.occurredOn(),
            QuantityWireParser.parsePositive(open.quantity(), "futuresRoll.openLeg.quantity"),
            QuantityWireParser.parsePositive(open.pricePoints(), "futuresRoll.openLeg.pricePoints"),
            PositiveMinorUnitParser.parse(open.initialMarginCent(), "futuresRoll.openLeg.initialMarginCent"),
            optionalFee(open.feeCent()), request.note()));
  }

  private static long optionalFee(String feeCent) {
    return feeCent == null || "0".equals(feeCent) ? 0 : parseOptionalFee(feeCent);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isFuturesRoll(TransactionCreateRequest request) {
    return "FUTURES_ROLL".equals(request.transactionType());
  }

  private static FuturesCloseCommand parseFuturesClose(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank() || request.quantity() == null
        || request.pricePoints() == null || request.initialMarginCent() != null || request.amountCent() != null
        || request.destinationAccountId() != null || request.unitPriceCent() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null || request.corporateAction() != null
        || request.marginDirection() != null || request.settlementPricePoints() != null) {
      throw new TransactionFieldsException("futures close requires cashAccountId, instrumentId, quantity, pricePoints and optional feeCent only");
    }
    BigDecimal quantity = QuantityWireParser.parsePositive(request.quantity(), "quantity");
    BigDecimal pricePoints = QuantityWireParser.parsePositive(request.pricePoints(), "price_points");
    long feeCent = request.feeCent() == null ? 0 : parseOptionalFee(request.feeCent());
    return new FuturesCloseCommand(request.cashAccountId(), request.instrumentId(), request.occurredOn(), quantity,
        pricePoints, feeCent, request.note());
  }

  private static FuturesDailySettlementCommand parseFuturesDailySettlement(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank() || request.settlementPricePoints() == null
        || request.quantity() != null || request.pricePoints() != null || request.initialMarginCent() != null
        || request.feeCent() != null || request.amountCent() != null || request.destinationAccountId() != null
        || request.unitPriceCent() != null || request.taxWithheldCent() != null || request.entitlementDate() != null
        || request.perShareAmountCent() != null || request.corporateAction() != null || request.marginDirection() != null) {
      throw new TransactionFieldsException("futures daily settlement requires cashAccountId, instrumentId and settlementPricePoints only");
    }
    BigDecimal settlementPricePoints = QuantityWireParser.parsePositive(request.settlementPricePoints(),
        "settlement_price_points");
    return new FuturesDailySettlementCommand(request.cashAccountId(), request.instrumentId(), request.occurredOn(),
        settlementPricePoints, request.note());
  }

  private static OptionTradeCommand parseOptionTrade(TransactionCreateRequest request, LedgerTransactionType type) {
    if (type != LedgerTransactionType.OPTION_OPEN && type != LedgerTransactionType.OPTION_CLOSE) {
      throw new TransactionFieldsException("option trade requires OPTION_OPEN or OPTION_CLOSE");
    }
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank() || request.quantity() == null
        || request.unitPriceCent() == null || request.amountCent() != null || request.destinationAccountId() != null
        || request.taxWithheldCent() != null || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.pricePoints() != null || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null || request.marginDirection() != null || request.expiryOutcome() != null) {
      throw new TransactionFieldsException("option trade requires cashAccountId, instrumentId, quantity, unitPriceCent and optional feeCent only");
    }
    BigDecimal quantity = QuantityWireParser.parsePositive(request.quantity(), "quantity");
    long unitPriceCent = PositiveMinorUnitParser.parse(request.unitPriceCent(), "unit_price_cent");
    long feeCent = request.feeCent() == null ? 0 : parseOptionalFee(request.feeCent());
    return new OptionTradeCommand(request.cashAccountId(), request.instrumentId(), request.occurredOn(), quantity,
        unitPriceCent, feeCent, request.note());
  }

  private static OptionExpiryCommand parseOptionExpiry(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()
        || request.instrumentId() == null || request.instrumentId().isBlank() || request.quantity() == null
        || request.expiryOutcome() == null || request.unitPriceCent() != null || request.feeCent() != null
        || request.amountCent() != null || request.destinationAccountId() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null || request.pricePoints() != null
        || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null || request.marginDirection() != null) {
      throw new TransactionFieldsException("option expiry requires cashAccountId, instrumentId, quantity and expiryOutcome only");
    }
    OptionExpiryOutcome outcome;
    try {
      outcome = OptionExpiryOutcome.valueOf(request.expiryOutcome());
    } catch (IllegalArgumentException exception) {
      throw new TransactionFieldsException("expiryOutcome must be WORTHLESS");
    }
    return new OptionExpiryCommand(request.cashAccountId(), request.instrumentId(), request.occurredOn(),
        QuantityWireParser.parsePositive(request.quantity(), "quantity"), outcome, request.note());
  }

  private static CorporateActionCommand parseCorporateAction(TransactionCreateRequest request) {
    CorporateActionRequest action = request.corporateAction();
    if (action == null || request.cashAccountId() != null || request.destinationAccountId() != null
        || request.instrumentId() != null || request.quantity() != null || request.unitPriceCent() != null
        || request.feeCent() != null || request.amountCent() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.pricePoints() != null || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.marginDirection() != null
        || action.instrumentId() == null || action.instrumentId().isBlank()
        || action.actionType() == null || action.actionType().isBlank()
        || action.ratioNumerator() == null || action.ratioDenominator() == null) {
      throw new TransactionFieldsException("corporate action accepts only the documented corporateAction object");
    }
    CorporateActionType type;
    try {
      type = CorporateActionType.valueOf(action.actionType());
    } catch (IllegalArgumentException exception) {
      throw new CorporateActionUnsupportedException("corporate action type is unsupported");
    }
    try {
      return new CorporateActionCommand(action.instrumentId(), request.occurredOn(), type,
          PositiveMinorUnitParser.parse(action.ratioNumerator(), "ratio_numerator"),
          PositiveMinorUnitParser.parse(action.ratioDenominator(), "ratio_denominator"), request.note());
    } catch (IllegalArgumentException exception) {
      throw new CorporateActionRatioException("corporate action ratio must be a positive integer within long range",
          exception);
    }
  }

  private static LedgerTransactionType transactionType(TransactionCreateRequest request) {
    try {
      LedgerTransactionType type = LedgerTransactionType.valueOf(request.transactionType());
      if (request.expiryOutcome() != null && type != LedgerTransactionType.OPTION_EXPIRE) {
        throw new TransactionFieldsException("expiryOutcome is only allowed for OPTION_EXPIRE");
      }
      return type;
    } catch (IllegalArgumentException exception) {
      throw new TransactionFieldsException("unsupported transactionType");
    }
  }

  private static String optionalStrategyKey(TransactionCreateRequest request) {
    return request.strategyKey() == null ? null : StrategyKey.from(request.strategyKey()).name();
  }

  private static String requiredCashAccount(TransactionCreateRequest request, boolean destinationAllowed) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank()) {
      throw new TransactionFieldsException("cashAccountId is required");
    }
    if (request.instrumentId() != null || request.quantity() != null || request.unitPriceCent() != null
        || request.feeCent() != null || request.taxWithheldCent() != null
        || request.entitlementDate() != null || request.perShareAmountCent() != null || request.pricePoints() != null
        || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null || request.marginDirection() != null
        || (!destinationAllowed && request.destinationAccountId() != null)) {
      throw new TransactionFieldsException("this transaction type accepts only its documented cash and amount fields");
    }
    return request.cashAccountId();
  }

  private static String requiredDestinationAccount(TransactionCreateRequest request) {
    if (request.destinationAccountId() == null || request.destinationAccountId().isBlank()
        || request.destinationAccountId().equals(request.cashAccountId())) {
      throw new TransactionFieldsException("a distinct destinationAccountId is required");
    }
    return request.destinationAccountId();
  }

  private static long requiredAmountCent(TransactionCreateRequest request) {
    if (request.amountCent() == null) {
      throw new TransactionFieldsException("amountCent is required");
    }
    return PositiveMinorUnitParser.parse(request.amountCent(), "amount_cent");
  }

  private static String requiredDividendCashAccount(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank() || request.destinationAccountId() != null
        || request.quantity() != null || request.unitPriceCent() != null || request.feeCent() != null
        || request.pricePoints() != null || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null
        || request.marginDirection() != null) {
      throw new TransactionFieldsException("dividend requires cashAccountId and income fields only");
    }
    return request.cashAccountId();
  }

  private static String requiredDividendInstrument(TransactionCreateRequest request) {
    if (request.instrumentId() == null || request.instrumentId().isBlank()) {
      throw new TransactionFieldsException("dividend requires instrumentId");
    }
    return request.instrumentId();
  }

  private static String requiredInterestCashAccount(TransactionCreateRequest request) {
    if (request.cashAccountId() == null || request.cashAccountId().isBlank() || request.destinationAccountId() != null
        || request.instrumentId() != null || request.quantity() != null || request.unitPriceCent() != null
        || request.feeCent() != null || request.entitlementDate() != null || request.perShareAmountCent() != null
        || request.pricePoints() != null || request.settlementPricePoints() != null || request.initialMarginCent() != null
        || request.corporateAction() != null
        || request.marginDirection() != null) {
      throw new TransactionFieldsException("interest requires cashAccountId, amountCent and optional taxWithheldCent only");
    }
    return request.cashAccountId();
  }

  private static LocalDate requiredEntitlementDate(TransactionCreateRequest request) {
    if (request.entitlementDate() == null) {
      throw new TransactionFieldsException("dividend requires entitlementDate");
    }
    return request.entitlementDate();
  }

  private static long optionalTaxCent(TransactionCreateRequest request) {
    if (request.taxWithheldCent() == null) {
      return 0;
    }
    return "0".equals(request.taxWithheldCent()) ? 0
        : PositiveMinorUnitParser.parse(request.taxWithheldCent(), "tax_withheld_cent");
  }

  private static Long optionalPerShareAmountCent(TransactionCreateRequest request) {
    if (request.perShareAmountCent() == null) {
      return null;
    }
    return PositiveMinorUnitParser.parse(request.perShareAmountCent(), "per_share_amount_cent");
  }

  private static long parseOptionalFee(String feeCent) {
    return "0".equals(feeCent) ? 0 : PositiveMinorUnitParser.parse(feeCent, "fee_cent");
  }

  private static TransactionResponse toResponse(LedgerTransaction transaction) {
    String currency = transaction.postings().isEmpty()
        ? transaction.transactionType() == LedgerTransactionType.FUTURES_DAILY_SETTLEMENT ? "CNY" : null
        : transaction.postings().getFirst().amount().currency().name();
    return new TransactionResponse(transaction.transactionId(), transaction.transactionType(), transaction.occurredOn(),
        currency, transaction.postings().stream().map(posting -> new PostingResponse(posting.postingId(),
            posting.accountId(), posting.side(), Long.toString(posting.amount().cent()),
            posting.amount().currency().name())).toList(), transaction.tradeDetails().stream()
            .map(LedgerTransactionController::tradeDetailResponse).toList(), Long.toString(transaction.ledgerVersion()));
  }

  private static TransactionDetailResponse toDetailResponse(LedgerTransactionDetail transaction) {
    return new TransactionDetailResponse(transaction.transactionId(), transaction.transactionType(),
        transaction.occurredOn(), transaction.strategyKey(), transaction.operationGroupKey(), transaction.sourceType().name(),
        transaction.importExportFileId(), transaction.correctionRootTransactionId(), transaction.reversalOfTransactionId(),
        transaction.revisionNo(), Long.toString(transaction.ledgerVersion()), transaction.note(), transaction.correctable(),
        transaction.postings().stream().map(posting -> new TransactionDetailPostingResponse(posting.postingId(),
            posting.accountId(), posting.postingNo(), posting.postingSide(), Long.toString(posting.amountCent()),
            posting.currency().name())).toList(), transaction.tradeDetails().stream()
            .map(detail -> new TransactionDetailTradeResponse(detail.tradeDetailId(), detail.detailNo(),
                detail.instrumentId(), detail.positionEffect(), detail.quantity().toPlainString(),
                detail.unitPriceCent() == null ? null : Long.toString(detail.unitPriceCent()),
                detail.pricePoints() == null ? null : detail.pricePoints().toPlainString(),
                detail.contractMultiplierCent() == null ? null : Long.toString(detail.contractMultiplierCent()),
                detail.deliveryDate(), Long.toString(detail.feeCent()), detail.optionContractMultiplier() == null ? null
                    : Long.toString(detail.optionContractMultiplier())))
            .toList(), transaction.corporateAction() == null ? null : new CorporateActionDetailResponse(
                transaction.corporateAction().corporateActionId(), transaction.corporateAction().instrumentId(),
                transaction.corporateAction().actionType(), transaction.corporateAction().effectiveOn(),
                Long.toString(transaction.corporateAction().ratioNumerator()),
                Long.toString(transaction.corporateAction().ratioDenominator())), transaction.income() == null ? null
                    : new IncomeDetailResponse(transaction.income().incomeDetailId(), transaction.income().incomeType(),
                        transaction.income().instrumentId(), transaction.income().entitlementDate(),
                        Long.toString(transaction.income().grossAmountCent()),
                        Long.toString(transaction.income().taxWithheldCent()),
                        transaction.income().perShareAmountCent() == null ? null
                            : Long.toString(transaction.income().perShareAmountCent()),
                        transaction.income().currency().name()));
  }

  private static TradeDetailResponse tradeDetailResponse(LedgerTradeDetail detail) {
    return new TradeDetailResponse(detail.tradeDetailId(), detail.instrumentId(), detail.positionEffect(),
        detail.quantity().toPlainString(), detail.unitPriceCent() == null ? null : Long.toString(detail.unitPriceCent()),
        detail.pricePoints() == null ? null : detail.pricePoints().toPlainString(),
        detail.contractMultiplierCent() == null ? null : Long.toString(detail.contractMultiplierCent()),
        detail.deliveryDate(), Long.toString(detail.feeCent()),
        detail.optionContractMultiplier() == null ? null : Long.toString(detail.optionContractMultiplier()));
  }

  private static List<PreviewPostingResponse> previewPostings(
      List<com.personal.investment.ledger.application.PreviewPosting> postings) {
    return postings.stream().map(posting -> new PreviewPostingResponse(posting.accountCode(), posting.displayName(),
        posting.postingSide(), Long.toString(posting.amountCent()), posting.currency().name())).toList();
  }

  private static String canonical(TransactionCreateRequest request) {
    return part(request.transactionType()) + part(request.occurredOn() == null ? null : request.occurredOn().toString())
        + part(request.note()) + part(request.cashAccountId()) + part(request.destinationAccountId())
        + part(request.instrumentId()) + part(request.quantity()) + part(request.unitPriceCent()) + part(request.feeCent())
        + part(request.pricePoints()) + part(request.settlementPricePoints()) + part(request.initialMarginCent())
        + part(request.amountCent()) + part(request.taxWithheldCent())
        + part(request.entitlementDate() == null ? null : request.entitlementDate().toString())
        + part(request.perShareAmountCent()) + part(request.corporateAction() == null ? null
            : request.corporateAction().actionType()) + part(request.corporateAction() == null ? null
            : request.corporateAction().instrumentId()) + part(request.corporateAction() == null ? null
            : request.corporateAction().ratioNumerator()) + part(request.corporateAction() == null ? null
            : request.corporateAction().ratioDenominator()) + part(request.marginDirection())
        + part(request.expiryOutcome()) + part(request.strategyKey()) + part(rollCanonical(request.futuresRoll()));
  }

  private static String rollCanonical(FuturesRollRequest roll) {
    if (roll == null) {
      return null;
    }
    if (roll.closeLeg() == null || roll.openLeg() == null) {
      return "missing-leg";
    }
    return part(roll.closeLeg().cashAccountId()) + part(roll.closeLeg().instrumentId())
        + part(roll.closeLeg().quantity()) + part(roll.closeLeg().pricePoints()) + part(roll.closeLeg().feeCent())
        + part(roll.openLeg().cashAccountId()) + part(roll.openLeg().instrumentId())
        + part(roll.openLeg().quantity()) + part(roll.openLeg().pricePoints())
        + part(roll.openLeg().initialMarginCent()) + part(roll.openLeg().feeCent());
  }

  private static String part(String value) {
    return value == null ? "-1:" : value.length() + ":" + value;
  }

  private static String draftHash(String canonicalRequest) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String ownerUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (!(principal instanceof SessionAuthenticationPrincipal sessionPrincipal)) {
      throw new IllegalArgumentException("authenticated session principal is invalid");
    }
    return sessionPrincipal.user().userId();
  }

  public record TransactionCreateRequest(
      @NotBlank String transactionType,
      @NotNull LocalDate occurredOn,
      @Size(max = 1000) String note,
      String cashAccountId,
      String destinationAccountId,
      String instrumentId,
      @JsonDeserialize(using = StrictStringDeserializer.class) String quantity,
      @JsonDeserialize(using = StrictStringDeserializer.class) String unitPriceCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String pricePoints,
      @JsonDeserialize(using = StrictStringDeserializer.class) String settlementPricePoints,
      @JsonDeserialize(using = StrictStringDeserializer.class) String initialMarginCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String feeCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String amountCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String taxWithheldCent,
      LocalDate entitlementDate,
      @JsonDeserialize(using = StrictStringDeserializer.class) String perShareAmountCent,
      CorporateActionRequest corporateAction,
      String marginDirection,
      String expiryOutcome,
      String strategyKey,
      FuturesRollRequest futuresRoll) {
  }

  public record CorporateActionRequest(String actionType, String instrumentId,
      @JsonDeserialize(using = StrictStringDeserializer.class) String ratioNumerator,
      @JsonDeserialize(using = StrictStringDeserializer.class) String ratioDenominator) {
  }

  public record TransactionResponse(String transactionId, LedgerTransactionType transactionType, LocalDate occurredOn,
                                    String currency, List<PostingResponse> postings,
                                    List<TradeDetailResponse> tradeDetails, String ledgerVersion) {
  }

  public record FuturesRollResponse(String operationGroupKey, List<TransactionResponse> transactions) {
  }

  public record FuturesRollRequest(@NotNull @Valid FuturesRollLegRequest closeLeg,
                                   @NotNull @Valid FuturesRollLegRequest openLeg) {
  }

  public record FuturesRollLegRequest(String cashAccountId, String instrumentId,
      @JsonDeserialize(using = StrictStringDeserializer.class) String quantity,
      @JsonDeserialize(using = StrictStringDeserializer.class) String pricePoints,
      @JsonDeserialize(using = StrictStringDeserializer.class) String initialMarginCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String unitPriceCent,
      @JsonDeserialize(using = StrictStringDeserializer.class) String feeCent) {
  }

  public record CorrectionRequest(@Valid TransactionCreateRequest replacement) {
  }

  public record CorrectionResponse(List<String> reversalTransactionIds, List<String> replacementTransactionIds,
                                   List<String> correctionRootTransactionIds, String ledgerVersion) {
  }

  public record TransactionListResponse(List<TransactionSummaryResponse> items, String nextCursor) {
  }

  public record TransactionSummaryResponse(String transactionId, LedgerTransactionType transactionType,
                                           LocalDate occurredOn, String currency, String ledgerVersion,
                                           String sourceType, String importExportFileId) {
  }

  public record TransactionDetailResponse(String transactionId, LedgerTransactionType transactionType,
                                          LocalDate occurredOn, String strategyKey, String operationGroupKey, String sourceType,
                                          String importExportFileId, String correctionRootTransactionId,
                                          String reversalOfTransactionId, int revisionNo, String ledgerVersion,
                                          String note, boolean correctable,
                                          List<TransactionDetailPostingResponse> postings,
                                          List<TransactionDetailTradeResponse> tradeDetails,
                                          CorporateActionDetailResponse corporateAction, IncomeDetailResponse income) {
  }

  public record TransactionDetailPostingResponse(String postingId, String accountId, int postingNo,
                                                 PostingSide postingSide, String amountCent, String currency) {
  }

  public record TransactionDetailTradeResponse(String tradeDetailId, int detailNo, String instrumentId,
                                               PositionEffect positionEffect, String quantity, String unitPriceCent,
                                               String pricePoints, String contractMultiplierCent,
                                               LocalDate deliveryDate, String feeCent,
                                               String optionContractMultiplier) {
  }

  public record CorporateActionDetailResponse(String corporateActionId, String instrumentId, String actionType,
                                              LocalDate effectiveOn, String ratioNumerator,
                                              String ratioDenominator) {
  }

  public record IncomeDetailResponse(String incomeDetailId, String incomeType, String instrumentId,
                                     LocalDate entitlementDate, String grossAmountCent, String taxWithheldCent,
                                     String perShareAmountCent, String currency) {
  }

  public record PostingResponse(String postingId, String accountId, PostingSide postingSide, String amountCent,
                                String currency) {
  }

  public record TradeDetailResponse(String tradeDetailId, String instrumentId, PositionEffect positionEffect,
                                    String quantity, String unitPriceCent, String pricePoints,
                                    String contractMultiplierCent, LocalDate deliveryDate, String feeCent,
                                    String optionContractMultiplier) {
  }

  public record TransactionPreviewResponse(String draftHash, String currency, List<PreviewPostingResponse> postings,
                                           List<TradePreviewDetail> tradeDetails, List<String> accountProvisioning,
                                           List<String> validationWarnings, String proposedOperationGroupKey) {
    public TransactionPreviewResponse(String draftHash, String currency, List<PreviewPostingResponse> postings,
                                      List<TradePreviewDetail> tradeDetails, List<String> accountProvisioning,
                                      List<String> validationWarnings) {
      this(draftHash, currency, postings, tradeDetails, accountProvisioning, validationWarnings, null);
    }
  }

  public record PreviewPostingResponse(String accountCode, String displayName, PostingSide postingSide,
                                       String amountCent, String currency) {
  }

  public record TradePreviewDetail(String instrumentId, PositionEffect positionEffect, String quantity) {
  }

  private static <T> List<T> concat(List<T> first, List<T> second) {
    return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
  }

  private record ParsedSpotCommand(LedgerTransactionType type, SpotTradeCommand command) {
  }
}
