package com.personal.investment.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.investment.ledger.application.DividendCommand;
import com.personal.investment.ledger.application.FuturesCloseCommand;
import com.personal.investment.ledger.application.FuturesCloseService;
import com.personal.investment.ledger.application.FuturesDailySettlementCommand;
import com.personal.investment.ledger.application.FuturesDailySettlementService;
import com.personal.investment.ledger.application.FuturesMarginService;
import com.personal.investment.ledger.application.FuturesOpenCommand;
import com.personal.investment.ledger.application.FuturesOpenService;
import com.personal.investment.ledger.application.FuturesRollCommand;
import com.personal.investment.ledger.application.FuturesRollService;
import com.personal.investment.ledger.application.IncomeCommand;
import com.personal.investment.ledger.application.LedgerAccountService;
import com.personal.investment.ledger.application.LedgerAppendMetadata;
import com.personal.investment.ledger.application.LedgerIdGenerator;
import com.personal.investment.ledger.application.LedgerTransactionPort;
import com.personal.investment.ledger.application.LedgerTransactionService;
import com.personal.investment.ledger.application.OptionExpiryCommand;
import com.personal.investment.ledger.application.OptionExpiryOutcome;
import com.personal.investment.ledger.application.OptionTradeCommand;
import com.personal.investment.ledger.application.OptionTradeService;
import com.personal.investment.ledger.application.SpotTradeCommand;
import com.personal.investment.ledger.application.SpotTradeService;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.market.application.CreateInstrumentCommand;
import com.personal.investment.market.application.InstrumentService;
import com.personal.investment.market.application.MarketSnapshotService;
import com.personal.investment.market.application.MarketSnapshotSubmissionCommand;
import com.personal.investment.market.domain.AssetType;
import com.personal.investment.market.domain.FutureSpecification;
import com.personal.investment.market.domain.OptionRight;
import com.personal.investment.market.domain.OptionSpecification;
import com.personal.investment.market.domain.Instrument;
import com.personal.investment.strategy.domain.StrategyKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only fixture. All 23 ledger facts go through the normal application services and carry immutable strategy
 * attribution; only the seed-run marker bypasses those commands because it is not a financial fact.
 */
@Service
@Profile("local")
public class LocalStrategyTestSeedService {
  public static final String SEED_NAME = "LEGACY_FULL_PATH";
  private final StrategySeedPort seedPort;
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerAccountService accountService;
  private final InstrumentService instrumentService;
  private final LedgerTransactionService ledgerService;
  private final SpotTradeService spotTradeService;
  private final OptionTradeService optionTradeService;
  private final FuturesMarginService futuresMarginService;
  private final FuturesOpenService futuresOpenService;
  private final FuturesDailySettlementService futuresSettlementService;
  private final FuturesRollService futuresRollService;
  private final FuturesCloseService futuresCloseService;
  private final StrategyRuleVersionService ruleVersionService;
  private final StrategyReferenceNavService referenceNavService;
  private final StrategyEvaluationService evaluationService;
  private final MarketSnapshotService marketSnapshotService;
  private final ObjectMapper json;

  public LocalStrategyTestSeedService(StrategySeedPort seedPort, LedgerTransactionPort transactionPort,
      LedgerIdGenerator idGenerator, LedgerAccountService accountService, InstrumentService instrumentService,
      LedgerTransactionService ledgerService, SpotTradeService spotTradeService, OptionTradeService optionTradeService,
      FuturesMarginService futuresMarginService, FuturesOpenService futuresOpenService,
      FuturesDailySettlementService futuresSettlementService, FuturesRollService futuresRollService,
      FuturesCloseService futuresCloseService, StrategyRuleVersionService ruleVersionService,
      StrategyReferenceNavService referenceNavService, StrategyEvaluationService evaluationService,
      MarketSnapshotService marketSnapshotService, ObjectMapper json) {
    this.seedPort = seedPort;
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
    this.accountService = accountService;
    this.instrumentService = instrumentService;
    this.ledgerService = ledgerService;
    this.spotTradeService = spotTradeService;
    this.optionTradeService = optionTradeService;
    this.futuresMarginService = futuresMarginService;
    this.futuresOpenService = futuresOpenService;
    this.futuresSettlementService = futuresSettlementService;
    this.futuresRollService = futuresRollService;
    this.futuresCloseService = futuresCloseService;
    this.ruleVersionService = ruleVersionService;
    this.referenceNavService = referenceNavService;
    this.evaluationService = evaluationService;
    this.marketSnapshotService = marketSnapshotService;
    this.json = json;
  }

  @Transactional
  public StrategySeedResult seed(String ownerUserId) {
    requireOwner(ownerUserId);
    transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    if (seedPort.countLedgerTransactions(ownerUserId) != 0 || seedPort.hasSeedRun(ownerUserId, SEED_NAME)) {
      throw new StrategySeedRequiresEmptyLedgerException();
    }
    var highCash = accountService.createCashAccount(ownerUserId, "本地高分红 CNY", CurrencyCode.CNY);
    var usdCash = accountService.createCashAccount(ownerUserId, "本地 USD 策略", CurrencyCode.USD);
    var futuresCash = accountService.createCashAccount(ownerUserId, "本地 IC/IM CNY", CurrencyCode.CNY);
    Instrument highDividend = instrument("CN", "SSE", "LOCALDIV", "本地高分红 ETF", AssetType.ETF, CurrencyCode.CNY,
        null, null, null);
    Instrument qqq = instrument("US", "NASDAQ", "QQQ", "Invesco QQQ", AssetType.ETF, CurrencyCode.USD, null, null,
        null);
    Instrument qld = instrument("US", "NYSEARCA", "QLD", "ProShares Ultra QQQ", AssetType.ETF, CurrencyCode.USD,
        null, null, null);
    Instrument csi500 = instrument("CN", "CSI", "000905", "中证500", AssetType.INDEX, CurrencyCode.CNY,
        null, null, null, "000905.SH", null);
    Instrument csi1000 = instrument("CN", "CSI", "000852", "中证1000", AssetType.INDEX, CurrencyCode.CNY,
        null, null, null, "000852.SH", null);
    Instrument putA = instrument("US", "OPRA", "QQQ260821P400", "QQQ 2026-08 Put", AssetType.OPTION, CurrencyCode.USD,
        LocalDate.of(2026, 8, 21), null, new OptionSpecification(qqq.instrumentId(), OptionRight.PUT, 40_000L, 100L));
    Instrument putB = instrument("US", "OPRA", "QQQ261016P360", "QQQ 2026-10 Put", AssetType.OPTION, CurrencyCode.USD,
        LocalDate.of(2026, 10, 16), null, new OptionSpecification(qqq.instrumentId(), OptionRight.PUT, 36_000L, 100L));
    Instrument icOld = instrument("CFFEX", "CFFEX", "IC2608", "IC 2026-08", AssetType.FUTURE, CurrencyCode.CNY,
        LocalDate.of(2026, 8, 21), new FutureSpecification("IC", 20_000L), null, "IC2608.CFX",
        csi500.instrumentId());
    Instrument im = instrument("CFFEX", "CFFEX", "IM2608", "IM 2026-08", AssetType.FUTURE, CurrencyCode.CNY,
        LocalDate.of(2026, 8, 21), new FutureSpecification("IM", 20_000L), null, "IM2608.CFX",
        csi1000.instrumentId());
    Instrument icNew = instrument("CFFEX", "CFFEX", "IC2609", "IC 2026-09", AssetType.FUTURE, CurrencyCode.CNY,
        LocalDate.of(2026, 9, 18), new FutureSpecification("IC", 20_000L), null, "IC2609.CFX",
        csi500.instrumentId());

    run(StrategyKey.HIGH_DIVIDEND, () -> ledgerService.externalFundingByMinorUnit(ownerUserId, highCash.accountId(),
        LocalDate.of(2026, 1, 2), 15_000_000L, "local high dividend funding"));
    run(StrategyKey.HIGH_DIVIDEND, () -> spotTradeService.buy(ownerUserId, new SpotTradeCommand(highCash.accountId(),
        highDividend.instrumentId(), LocalDate.of(2026, 1, 5), new BigDecimal("100"), 10_000L, 100L, "local buy")));
    run(StrategyKey.HIGH_DIVIDEND, () -> spotTradeService.sell(ownerUserId, new SpotTradeCommand(highCash.accountId(),
        highDividend.instrumentId(), LocalDate.of(2026, 3, 5), new BigDecimal("40"), 11_000L, 80L, "local sell")));
    run(StrategyKey.HIGH_DIVIDEND, () -> ledgerService.dividend(ownerUserId, new DividendCommand(highCash.accountId(),
        highDividend.instrumentId(), LocalDate.of(2026, 5, 20), LocalDate.of(2026, 5, 15), Money.of(48_000L, CurrencyCode.CNY),
        Money.of(0L, CurrencyCode.CNY), 800L, "local dividend")));
    run(StrategyKey.HIGH_DIVIDEND, () -> ledgerService.interest(ownerUserId, new IncomeCommand(highCash.accountId(),
        LocalDate.of(2026, 6, 20), Money.of(20_000L, CurrencyCode.CNY), Money.of(0L, CurrencyCode.CNY), "local interest")));

    run(StrategyKey.QQQ_GROWTH, () -> ledgerService.externalFundingByMinorUnit(ownerUserId, usdCash.accountId(),
        LocalDate.of(2026, 1, 3), 900_000L, "local qqq funding"));
    run(StrategyKey.QQQ_GROWTH, () -> spotTradeService.buy(ownerUserId, new SpotTradeCommand(usdCash.accountId(),
        qqq.instrumentId(), LocalDate.of(2026, 1, 6), new BigDecimal("10"), 50_000L, 100L, "local qqq buy")));
    run(StrategyKey.QQQ_GROWTH, () -> spotTradeService.buy(ownerUserId, new SpotTradeCommand(usdCash.accountId(),
        qld.instrumentId(), LocalDate.of(2026, 2, 6), new BigDecimal("4"), 10_000L, 50L, "local qld buy")));
    run(StrategyKey.QQQ_GROWTH, () -> spotTradeService.sell(ownerUserId, new SpotTradeCommand(usdCash.accountId(),
        qld.instrumentId(), LocalDate.of(2026, 3, 6), new BigDecimal("1"), 11_000L, 20L, "local qld sell")));
    run(StrategyKey.QQQ_GROWTH, () -> ledgerService.dividend(ownerUserId, new DividendCommand(usdCash.accountId(),
        qqq.instrumentId(), LocalDate.of(2026, 6, 25), LocalDate.of(2026, 6, 20), Money.of(500L, CurrencyCode.USD),
        Money.of(0L, CurrencyCode.USD), 50L, "local qqq dividend")));

    run(StrategyKey.DEEP_PUT, () -> ledgerService.externalFundingByMinorUnit(ownerUserId, usdCash.accountId(),
        LocalDate.of(2026, 1, 4), 100_000L, "local put funding"));
    run(StrategyKey.DEEP_PUT, () -> optionTradeService.open(ownerUserId, new OptionTradeCommand(usdCash.accountId(),
        putA.instrumentId(), LocalDate.of(2026, 2, 1), BigDecimal.ONE, 100L, 10L, "local put A open")));
    run(StrategyKey.DEEP_PUT, () -> optionTradeService.open(ownerUserId, new OptionTradeCommand(usdCash.accountId(),
        putB.instrumentId(), LocalDate.of(2026, 3, 1), BigDecimal.ONE, 120L, 10L, "local put B open")));
    run(StrategyKey.DEEP_PUT, () -> optionTradeService.close(ownerUserId, new OptionTradeCommand(usdCash.accountId(),
        putA.instrumentId(), LocalDate.of(2026, 5, 1), BigDecimal.ONE, 90L, 10L, "local put A close")));
    run(StrategyKey.DEEP_PUT, () -> optionTradeService.expire(ownerUserId, new OptionExpiryCommand(usdCash.accountId(),
        putB.instrumentId(), LocalDate.of(2026, 10, 16), BigDecimal.ONE, OptionExpiryOutcome.WORTHLESS,
        "local put B expiry")));

    run(StrategyKey.IC_IM, () -> ledgerService.externalFundingByMinorUnit(ownerUserId, futuresCash.accountId(),
        LocalDate.of(2026, 1, 5), 200_000_000L, "local futures funding"));
    run(StrategyKey.IC_IM, () -> futuresMarginService.moveByMinorUnit(ownerUserId, futuresCash.accountId(),
        LocalDate.of(2026, 1, 6), MarginDirection.IN, 100_000_000L, "local margin funding"));
    run(StrategyKey.IC_IM, () -> futuresOpenService.open(ownerUserId, new FuturesOpenCommand(futuresCash.accountId(),
        icOld.instrumentId(), LocalDate.of(2026, 6, 1), BigDecimal.ONE, new BigDecimal("6000"), 10_000_000L, 100L,
        "local IC open")));
    run(StrategyKey.IC_IM, () -> futuresOpenService.open(ownerUserId, new FuturesOpenCommand(futuresCash.accountId(),
        im.instrumentId(), LocalDate.of(2026, 6, 2), BigDecimal.ONE, new BigDecimal("5500"), 8_000_000L, 100L,
        "local IM open")));
    run(StrategyKey.IC_IM, () -> futuresSettlementService.settle(ownerUserId, new FuturesDailySettlementCommand(
        futuresCash.accountId(), icOld.instrumentId(), LocalDate.of(2026, 6, 3), new BigDecimal("6010"),
        "local IC settlement")));
    run(StrategyKey.IC_IM, () -> futuresRollService.roll(ownerUserId, new FuturesRollCommand(
        new FuturesCloseCommand(futuresCash.accountId(), icOld.instrumentId(), LocalDate.of(2026, 7, 1), BigDecimal.ONE,
            new BigDecimal("6020"), 100L, "local IC roll close"),
        new FuturesOpenCommand(futuresCash.accountId(), icNew.instrumentId(), LocalDate.of(2026, 7, 1), BigDecimal.ONE,
            new BigDecimal("6030"), 10_000_000L, 100L, "local IC roll open"))));
    run(StrategyKey.IC_IM, () -> futuresCloseService.close(ownerUserId, new FuturesCloseCommand(futuresCash.accountId(),
        icNew.instrumentId(), LocalDate.of(2026, 7, 15), BigDecimal.ONE, new BigDecimal("6040"), 100L,
        "local IC final close")));

    createRules(ownerUserId);
    Instant now = Instant.now();
    writeMarketFixture(ownerUserId, now, qqq, qld, csi500, csi1000, icNew, im);
    referenceNavService.record(ownerUserId, StrategyKey.QQQ_GROWTH, CurrencyCode.USD, 3_000_000L, now, now.plusSeconds(604800),
        "LOCAL_SEED");
    referenceNavService.record(ownerUserId, StrategyKey.DEEP_PUT, CurrencyCode.USD, 2_200_000L, now, now.plusSeconds(604800),
        "LOCAL_SEED");
    for (StrategyKey key : StrategyKey.values()) {
      evaluationService.evaluate(ownerUserId, key, now);
    }
    seedPort.appendSeedRun(idGenerator.next(), ownerUserId, SEED_NAME, checksum(SEED_NAME));
    return new StrategySeedResult(SEED_NAME, 3, 10, 23, 4, List.of("CNY", "USD"));
  }

  private Instrument instrument(String market, String exchange, String symbol, String displayName, AssetType type,
      CurrencyCode currency, LocalDate maturity, FutureSpecification future, OptionSpecification option) {
    return instrumentService.create(new CreateInstrumentCommand(market, exchange, symbol, displayName, type, currency,
        maturity, future, option));
  }

  private void writeMarketFixture(String ownerUserId, Instant asOfAt, Instrument qqq, Instrument qld,
      Instrument csi500, Instrument csi1000, Instrument ic, Instrument im) {
    LocalDate date = asOfAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    marketSnapshotService.submit(ownerUserId, new MarketSnapshotSubmissionCommand(date, "LOCAL_SEED_ATTESTED",
        "local://strategy-test-seed/market-" + date + ".json", List.of(
            new MarketSnapshotSubmissionCommand.Quote(qqq.instrumentId(), asOfAt, "QQQ-local-" + date,
                20_000L, 19_900L, CurrencyCode.USD),
            new MarketSnapshotSubmissionCommand.Quote(qld.instrumentId(), asOfAt, "QLD-local-" + date,
                10_000L, 9_950L, CurrencyCode.USD),
            new MarketSnapshotSubmissionCommand.Quote(csi500.instrumentId(), asOfAt, "CSI500-local-" + date,
                750_000L, 748_000L, CurrencyCode.CNY),
            new MarketSnapshotSubmissionCommand.Quote(csi1000.instrumentId(), asOfAt, "CSI1000-local-" + date,
                7_200_000L, 7_180_000L, CurrencyCode.CNY),
            new MarketSnapshotSubmissionCommand.Quote(ic.instrumentId(), asOfAt, "IC-local-" + date,
                745_000L, 743_000L, CurrencyCode.CNY),
            new MarketSnapshotSubmissionCommand.Quote(im.instrumentId(), asOfAt, "IM-local-" + date,
                7_150_000L, 7_130_000L, CurrencyCode.CNY)),
        List.of(new MarketSnapshotSubmissionCommand.Metric(csi500.instrumentId(), "PB_PERCENTILE",
                new BigDecimal("25"), "CSI500-pb-" + date),
            new MarketSnapshotSubmissionCommand.Metric(csi1000.instrumentId(), "PB_PERCENTILE",
                new BigDecimal("35"), "CSI1000-pb-" + date)),
        List.of(new MarketSnapshotSubmissionCommand.Basis(csi500.instrumentId(), ic.instrumentId(),
                new BigDecimal("7500"), new BigDecimal("7450"), new BigDecimal("-0.0500"), ic.maturityDate(),
                daysLeft(date, ic.maturityDate()), "IC-basis-" + date),
            new MarketSnapshotSubmissionCommand.Basis(csi1000.instrumentId(), im.instrumentId(),
                new BigDecimal("7200"), new BigDecimal("7150"), new BigDecimal("-0.0500"), im.maturityDate(),
                daysLeft(date, im.maturityDate()), "IM-basis-" + date))));
    if (marketSnapshotService.processQueuedSubmissions() != 1) {
      throw new IllegalStateException("local market fixture was not processed exactly once");
    }
  }

  private static int daysLeft(LocalDate date, LocalDate maturityDate) {
    return Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(date, maturityDate));
  }

  private Instrument instrument(String market, String exchange, String symbol, String displayName, AssetType type,
      CurrencyCode currency, LocalDate maturity, FutureSpecification future, OptionSpecification option,
      String tushareCode, String underlyingInstrumentId) {
    return instrumentService.create(new CreateInstrumentCommand(market, exchange, symbol, displayName, type, currency,
        maturity, future, option, tushareCode, underlyingInstrumentId));
  }

  private void createRules(String ownerUserId) {
    createRule(ownerUserId, StrategyKey.HIGH_DIVIDEND, "local-high-dividend-v1", """
        {"annual_expense_cent":"12000000","annual_expense_currency":"CNY",
         "minimum_dividend_coverage_percent":"100","cash_buffer_months":"6"}
        """);
    createRule(ownerUserId, StrategyKey.QQQ_GROWTH, "local-qqq-v1", """
        {"starter_percent":"5","target_percent":"10","upper_percent":"12",
         "qld_max_share_percent":"35","moving_average_days":"120"}
        """);
    createRule(ownerUserId, StrategyKey.IC_IM, "local-ic-im-v1", """
        {"minimum_pool_cent":"100000000","minimum_pool_currency":"CNY","pb_entry_percentile":"30",
         "stress_drop_percent":"20","margin_warning_percent":"60","roll_window_days":"10"}
        """);
    createRule(ownerUserId, StrategyKey.DEEP_PUT, "local-deep-put-v1", """
        {"budget_min_percent":"0.5","budget_max_percent":"2","expiry_warning_days":"30"}
        """);
  }

  private void createRule(String ownerUserId, StrategyKey key, String version, String body) {
    try {
      JsonNode rule = json.readTree(body);
      ruleVersionService.create(ownerUserId, key, version, rule, null);
    } catch (Exception exception) {
      throw new IllegalStateException("local strategy seed rule is invalid", exception);
    }
  }

  private static void run(StrategyKey key, Runnable work) {
    LedgerAppendMetadata.withStrategyKey(key.name(), () -> {
      work.run();
      return null;
    });
  }

  private static byte[] checksum(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void requireOwner(String ownerUserId) {
    if (ownerUserId == null || !ownerUserId.matches("[0-9A-HJKMNP-TV-Z]{26}")) {
      throw new IllegalArgumentException("ownerUserId must be a ULID");
    }
  }
}
