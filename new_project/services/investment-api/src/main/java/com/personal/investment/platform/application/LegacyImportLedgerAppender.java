package com.personal.investment.platform.application;

import com.personal.investment.ledger.application.FuturesCloseCommand;
import com.personal.investment.ledger.application.FuturesCloseService;
import com.personal.investment.ledger.application.FuturesMarginCommand;
import com.personal.investment.ledger.application.FuturesMarginService;
import com.personal.investment.ledger.application.FuturesOpenCommand;
import com.personal.investment.ledger.application.FuturesOpenService;
import com.personal.investment.ledger.application.LedgerTransactionService;
import com.personal.investment.ledger.application.OptionTradeCommand;
import com.personal.investment.ledger.application.OptionTradeService;
import com.personal.investment.ledger.application.SpotTradeCommand;
import com.personal.investment.ledger.application.SpotTradeService;
import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.MarginDirection;
import com.personal.investment.ledger.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Single command translation used by both rollback-only dry-run and final transactional confirmation. */
@Component
public class LegacyImportLedgerAppender {
  private final LedgerTransactionService ledgerService;
  private final SpotTradeService spotTradeService;
  private final OptionTradeService optionTradeService;
  private final FuturesMarginService futuresMarginService;
  private final FuturesOpenService futuresOpenService;
  private final FuturesCloseService futuresCloseService;

  public LegacyImportLedgerAppender(LedgerTransactionService ledgerService, SpotTradeService spotTradeService,
      OptionTradeService optionTradeService, FuturesMarginService futuresMarginService,
      FuturesOpenService futuresOpenService, FuturesCloseService futuresCloseService) {
    this.ledgerService = ledgerService;
    this.spotTradeService = spotTradeService;
    this.optionTradeService = optionTradeService;
    this.futuresMarginService = futuresMarginService;
    this.futuresOpenService = futuresOpenService;
    this.futuresCloseService = futuresCloseService;
  }

  public void append(String ownerUserId, String importExportFileId, LegacyImportPreviewLine line) {
    LocalDate occurredOn = LocalDate.parse(line.occurredOn());
    String note = line.note();
    switch (line.operation()) {
      case EXTERNAL_FUNDING -> ledgerService.externalFundingImportedByMinorUnit(ownerUserId, line.cashAccountId(), occurredOn,
          minor(line.amountCent()), note, importExportFileId);
      case EXTERNAL_WITHDRAWAL -> ledgerService.externalWithdrawalImportedByMinorUnit(ownerUserId, line.cashAccountId(), occurredOn,
          minor(line.amountCent()), note, importExportFileId);
      case SPOT_BUY -> spotTradeService.buyImported(ownerUserId, new SpotTradeCommand(line.cashAccountId(), line.instrumentId(),
          occurredOn, quantity(line.quantity()), minor(line.unitPriceCent()), minor(line.feeCent()), note), importExportFileId);
      case SPOT_SELL -> spotTradeService.sellImported(ownerUserId, new SpotTradeCommand(line.cashAccountId(), line.instrumentId(),
          occurredOn, quantity(line.quantity()), minor(line.unitPriceCent()), minor(line.feeCent()), note), importExportFileId);
      case DIVIDEND -> ledgerService.dividendImportedByMinorUnit(ownerUserId, line.cashAccountId(), line.instrumentId(),
          occurredOn, LocalDate.parse(line.entitlementDate()), minor(line.amountCent()), 0, null, note, importExportFileId);
      case INTEREST -> ledgerService.interestImportedByMinorUnit(ownerUserId, line.cashAccountId(), occurredOn,
          minor(line.amountCent()), 0, note, importExportFileId);
      case OPTION_OPEN -> optionTradeService.openImported(ownerUserId, new OptionTradeCommand(line.cashAccountId(),
          line.instrumentId(), occurredOn, quantity(line.quantity()), minor(line.unitPriceCent()), minor(line.feeCent()), note),
          importExportFileId);
      case OPTION_CLOSE -> optionTradeService.closeImported(ownerUserId, new OptionTradeCommand(line.cashAccountId(),
          line.instrumentId(), occurredOn, quantity(line.quantity()), minor(line.unitPriceCent()), minor(line.feeCent()), note),
          importExportFileId);
      case OPTION_EXPIRE_ALL -> optionTradeService.expireAllImported(ownerUserId, line.cashAccountId(), line.instrumentId(),
          occurredOn, note, importExportFileId);
      case FUTURES_MARGIN_IN -> futuresMarginService.moveImported(ownerUserId, new FuturesMarginCommand(line.cashAccountId(),
          occurredOn, MarginDirection.IN, Money.of(minor(line.amountCent()), CurrencyCode.CNY), note), importExportFileId);
      case FUTURES_OPEN -> futuresOpenService.openImported(ownerUserId, new FuturesOpenCommand(line.cashAccountId(),
          line.instrumentId(), occurredOn, quantity(line.quantity()), new BigDecimal(line.pricePoints()),
          minor(line.initialMarginCent()), minor(line.feeCent()), note), importExportFileId);
      case FUTURES_CLOSE -> futuresCloseService.closeImported(ownerUserId, new FuturesCloseCommand(line.cashAccountId(),
          line.instrumentId(), occurredOn, quantity(line.quantity()), new BigDecimal(line.pricePoints()),
          minor(line.feeCent()), note), importExportFileId);
      case FEE -> ledgerService.feeImportedByMinorUnit(ownerUserId, line.cashAccountId(), occurredOn,
          minor(line.amountCent()), note, importExportFileId);
    }
  }

  private static BigDecimal quantity(String value) {
    return new BigDecimal(value).setScale(8, RoundingMode.UNNECESSARY);
  }

  private static long minor(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("persisted preview minor unit is invalid", exception);
    }
  }
}
