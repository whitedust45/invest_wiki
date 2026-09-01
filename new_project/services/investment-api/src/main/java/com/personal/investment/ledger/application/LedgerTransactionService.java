package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.BalancedPostings;
import com.personal.investment.ledger.domain.LedgerAccount;
import com.personal.investment.ledger.domain.LedgerAccountKind;
import com.personal.investment.ledger.domain.LedgerAccountStatus;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerPostingTemplates;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.LedgerSourceType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.Posting;
import com.personal.investment.ledger.domain.PostingSide;
import com.personal.investment.ledger.domain.SystemLedgerAccount;
import com.personal.investment.ledger.domain.TradableInstrument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTransactionService {
  private final LedgerCommandAccountPort accountPort;
  private final LedgerTransactionPort transactionPort;
  private final LedgerIdGenerator idGenerator;
  private final LedgerTransactionEventPort transactionEventPort;
  private final IncomeDetailPort incomeDetailPort;
  private final SpotInstrumentPort spotInstrumentPort;
  private final SpotHistoryReplayer historyReplayer;

  public LedgerTransactionService(LedgerCommandAccountPort accountPort,
      LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator) {
    this(accountPort, transactionPort, idGenerator, LedgerTransactionEventPort.noop(), IncomeDetailPort.noop());
  }

  public LedgerTransactionService(LedgerCommandAccountPort accountPort,
      LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator,
      LedgerTransactionEventPort transactionEventPort) {
    this(accountPort, transactionPort, idGenerator, transactionEventPort, IncomeDetailPort.noop());
  }

  public LedgerTransactionService(LedgerCommandAccountPort accountPort,
      LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator,
      LedgerTransactionEventPort transactionEventPort, IncomeDetailPort incomeDetailPort) {
    this(accountPort, transactionPort, idGenerator, transactionEventPort, incomeDetailPort, null,
        SpotHistoryReplayer.noop());
  }

  @Autowired
  public LedgerTransactionService(LedgerCommandAccountPort accountPort,
      LedgerTransactionPort transactionPort, LedgerIdGenerator idGenerator,
      LedgerTransactionEventPort transactionEventPort, IncomeDetailPort incomeDetailPort,
      SpotInstrumentPort spotInstrumentPort, SpotHistoryReplayer historyReplayer) {
    this.accountPort = accountPort;
    this.transactionPort = transactionPort;
    this.idGenerator = idGenerator;
    this.transactionEventPort = transactionEventPort;
    this.incomeDetailPort = incomeDetailPort;
    this.spotInstrumentPort = spotInstrumentPort;
    this.historyReplayer = historyReplayer;
  }

  @Transactional
  public LedgerTransaction externalFunding(String ownerUserId, ExternalFundingCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount externalEquity = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.EXTERNAL_EQUITY, cashAccount);
    return appendOriginal(ownerUserId, LedgerTransactionType.EXTERNAL_FUNDING, command.occurredOn(),
        command.note(), LedgerPostingTemplates.externalFunding(cashAccount.accountId(),
            externalEquity.accountId(), command.amount()), cashAccount);
  }

  @Transactional
  public LedgerTransaction externalFundingReplacement(LedgerAppendContext context, ExternalFundingCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(context.ownerUserId(), command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount externalEquity = activeSystemAccount(context.ownerUserId(), SystemLedgerAccount.EXTERNAL_EQUITY,
        cashAccount);
    return appendReplacement(context, LedgerTransactionType.EXTERNAL_FUNDING, command.occurredOn(), command.note(),
        LedgerPostingTemplates.externalFunding(cashAccount.accountId(), externalEquity.accountId(), command.amount()),
        cashAccount);
  }

  /** HTTP requests carry only a minor-unit integer; the selected cash account determines the currency. */
  @Transactional
  public LedgerTransaction externalFundingByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    return externalFunding(ownerUserId, new ExternalFundingCommand(cashAccountId, occurredOn,
        Money.of(amountCent, cashAccount.currency()), note));
  }

  /** Appends an import-origin funding fact after the preview checksum has been confirmed. */
  @Transactional
  public LedgerTransaction externalFundingImportedByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note, String importExportFileId) {
    LedgerAccount cash = activeCashAccount(ownerUserId, cashAccountId);
    LedgerAccount equity = activeSystemAccount(ownerUserId, SystemLedgerAccount.EXTERNAL_EQUITY, cash);
    return appendOriginal(ownerUserId, LedgerSourceType.IMPORT, importExportFileId, LedgerTransactionType.EXTERNAL_FUNDING, occurredOn,
        note, LedgerPostingTemplates.externalFunding(cash.accountId(), equity.accountId(),
            Money.of(amountCent, cash.currency())), cash);
  }

  @Transactional
  public LedgerTransaction externalFundingReplacementByMinorUnit(LedgerAppendContext context, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cash = activeCashAccount(context.ownerUserId(), cashAccountId);
    return externalFundingReplacement(context, new ExternalFundingCommand(cashAccountId, occurredOn,
        Money.of(amountCent, cash.currency()), note));
  }

  @Transactional
  public LedgerTransaction externalWithdrawal(String ownerUserId, ExternalFundingCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount externalEquity = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.EXTERNAL_EQUITY, cashAccount);
    return appendOriginal(ownerUserId, LedgerTransactionType.EXTERNAL_WITHDRAWAL, command.occurredOn(),
        command.note(), LedgerPostingTemplates.externalWithdrawal(cashAccount.accountId(),
            externalEquity.accountId(), command.amount()), cashAccount);
  }

  @Transactional
  public LedgerTransaction externalWithdrawalReplacement(LedgerAppendContext context, ExternalFundingCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(context.ownerUserId(), command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount externalEquity = activeSystemAccount(context.ownerUserId(), SystemLedgerAccount.EXTERNAL_EQUITY,
        cashAccount);
    return appendReplacement(context, LedgerTransactionType.EXTERNAL_WITHDRAWAL, command.occurredOn(), command.note(),
        LedgerPostingTemplates.externalWithdrawal(cashAccount.accountId(), externalEquity.accountId(), command.amount()),
        cashAccount);
  }

  @Transactional
  public LedgerTransaction externalWithdrawalByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    return externalWithdrawal(ownerUserId, new ExternalFundingCommand(cashAccountId, occurredOn,
        Money.of(amountCent, cashAccount.currency()), note));
  }

  /** Appends an import-origin withdrawal fact after the preview checksum has been confirmed. */
  @Transactional
  public LedgerTransaction externalWithdrawalImportedByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note, String importExportFileId) {
    LedgerAccount cash = activeCashAccount(ownerUserId, cashAccountId);
    LedgerAccount equity = activeSystemAccount(ownerUserId, SystemLedgerAccount.EXTERNAL_EQUITY, cash);
    return appendOriginal(ownerUserId, LedgerSourceType.IMPORT, importExportFileId, LedgerTransactionType.EXTERNAL_WITHDRAWAL, occurredOn,
        note, LedgerPostingTemplates.externalWithdrawal(cash.accountId(), equity.accountId(),
            Money.of(amountCent, cash.currency())), cash);
  }

  @Transactional
  public LedgerTransaction externalWithdrawalReplacementByMinorUnit(LedgerAppendContext context,
      String cashAccountId, java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cash = activeCashAccount(context.ownerUserId(), cashAccountId);
    return externalWithdrawalReplacement(context, new ExternalFundingCommand(cashAccountId, occurredOn,
        Money.of(amountCent, cash.currency()), note));
  }

  @Transactional
  public LedgerTransaction internalTransfer(String ownerUserId, InternalTransferCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount sourceCash = activeCashAccount(ownerUserId, command.sourceCashAccountId());
    LedgerAccount destinationCash = activeCashAccount(ownerUserId, command.destinationCashAccountId());
    requireCurrency(sourceCash, command.amount());
    if (sourceCash.currency() != destinationCash.currency()) {
      throw new IllegalArgumentException("internal transfer accounts must use the same currency");
    }
    return appendOriginal(ownerUserId, LedgerTransactionType.INTERNAL_TRANSFER, command.occurredOn(),
        command.note(), LedgerPostingTemplates.internalTransfer(sourceCash.accountId(),
            destinationCash.accountId(), command.amount()), sourceCash, destinationCash);
  }

  @Transactional
  public LedgerTransaction internalTransferReplacement(LedgerAppendContext context, InternalTransferCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount sourceCash = activeCashAccount(context.ownerUserId(), command.sourceCashAccountId());
    LedgerAccount destinationCash = activeCashAccount(context.ownerUserId(), command.destinationCashAccountId());
    requireCurrency(sourceCash, command.amount());
    if (sourceCash.currency() != destinationCash.currency()) {
      throw new IllegalArgumentException("internal transfer accounts must use the same currency");
    }
    return appendReplacement(context, LedgerTransactionType.INTERNAL_TRANSFER, command.occurredOn(), command.note(),
        LedgerPostingTemplates.internalTransfer(sourceCash.accountId(), destinationCash.accountId(), command.amount()),
        sourceCash, destinationCash);
  }

  @Transactional
  public LedgerTransaction internalTransferByMinorUnit(String ownerUserId, String sourceCashAccountId,
      String destinationCashAccountId, java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount sourceCash = activeCashAccount(ownerUserId, sourceCashAccountId);
    return internalTransfer(ownerUserId, new InternalTransferCommand(sourceCashAccountId, destinationCashAccountId,
        occurredOn, Money.of(amountCent, sourceCash.currency()), note));
  }

  @Transactional
  public LedgerTransaction internalTransferReplacementByMinorUnit(LedgerAppendContext context,
      String sourceCashAccountId, String destinationCashAccountId, java.time.LocalDate occurredOn, long amountCent,
      String note) {
    LedgerAccount sourceCash = activeCashAccount(context.ownerUserId(), sourceCashAccountId);
    return internalTransferReplacement(context, new InternalTransferCommand(sourceCashAccountId,
        destinationCashAccountId, occurredOn, Money.of(amountCent, sourceCash.currency()), note));
  }

  @Transactional
  public LedgerTransaction dividend(String ownerUserId, DividendCommand command) {
    return dividend(ownerUserId, command, null);
  }

  @Transactional
  public LedgerTransaction dividendReplacement(LedgerAppendContext context, DividendCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    return dividend(context.ownerUserId(), command, context);
  }

  private LedgerTransaction dividend(String ownerUserId, DividendCommand command, LedgerAppendContext context) {
    Objects.requireNonNull(command, "command must not be null");
    requireText(command.instrumentId(), "instrumentId");
    Objects.requireNonNull(command.entitlementDate(), "entitlementDate must not be null");
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, command.cashAccountId());
    requireCurrency(cashAccount, command.grossAmount());
    requireCurrency(cashAccount, command.taxWithheld());
    validateDividendEntitlement(ownerUserId, command, cashAccount);
    LedgerAccount dividendIncome = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.DIVIDEND_INCOME, cashAccount);
    LedgerAccount taxExpense = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.WITHHOLDING_TAX_EXPENSE, cashAccount);
    LedgerTransaction transaction = context == null
        ? appendOriginal(ownerUserId, LedgerTransactionType.DIVIDEND, command.occurredOn(), command.note(),
        LedgerPostingTemplates.income(cashAccount.accountId(), dividendIncome.accountId(),
            taxExpense.accountId(), command.grossAmount(), command.taxWithheld()), cashAccount)
        : appendReplacement(context, LedgerTransactionType.DIVIDEND, command.occurredOn(), command.note(),
            LedgerPostingTemplates.income(cashAccount.accountId(), dividendIncome.accountId(),
                taxExpense.accountId(), command.grossAmount(), command.taxWithheld()), cashAccount);
    incomeDetailPort.insert(new IncomeDetail(idGenerator.next(), transaction.transactionId(), "DIVIDEND",
        command.instrumentId(), command.entitlementDate(), command.grossAmount().cent(), command.taxWithheld().cent(),
        command.perShareAmountCent(), cashAccount.currency()));
    return transaction;
  }

  @Transactional
  public LedgerTransaction dividendByMinorUnit(String ownerUserId, String cashAccountId, String instrumentId,
      java.time.LocalDate occurredOn, java.time.LocalDate entitlementDate, long grossAmountCent,
      long taxWithheldCent, Long perShareAmountCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    return dividend(ownerUserId, new DividendCommand(cashAccountId, instrumentId, occurredOn, entitlementDate,
        Money.of(grossAmountCent, cashAccount.currency()), Money.of(taxWithheldCent, cashAccount.currency()),
        perShareAmountCent, note));
  }

  /** Appends an import-origin dividend fact. The caller has already required an entitlement-date override. */
  @Transactional
  public LedgerTransaction dividendImportedByMinorUnit(String ownerUserId, String cashAccountId, String instrumentId,
      java.time.LocalDate occurredOn, java.time.LocalDate entitlementDate, long grossAmountCent,
      long taxWithheldCent, Long perShareAmountCent, String note, String importExportFileId) {
    LedgerAccount cash = activeCashAccount(ownerUserId, cashAccountId);
    DividendCommand command = new DividendCommand(cashAccountId, instrumentId, occurredOn, entitlementDate,
        Money.of(grossAmountCent, cash.currency()), Money.of(taxWithheldCent, cash.currency()), perShareAmountCent,
        note);
    validateDividendCommand(ownerUserId, command, cash);
    LedgerAccount income = activeSystemAccount(ownerUserId, SystemLedgerAccount.DIVIDEND_INCOME, cash);
    LedgerAccount taxExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.WITHHOLDING_TAX_EXPENSE, cash);
    LedgerTransaction transaction = appendOriginal(ownerUserId, LedgerSourceType.IMPORT, importExportFileId, LedgerTransactionType.DIVIDEND,
        occurredOn, note, LedgerPostingTemplates.income(cash.accountId(), income.accountId(), taxExpense.accountId(),
            command.grossAmount(), command.taxWithheld()), cash);
    incomeDetailPort.insert(new IncomeDetail(idGenerator.next(), transaction.transactionId(), "DIVIDEND", instrumentId,
        entitlementDate, grossAmountCent, taxWithheldCent, perShareAmountCent, cash.currency()));
    return transaction;
  }

  @Transactional
  public LedgerTransaction dividendReplacementByMinorUnit(LedgerAppendContext context, String cashAccountId,
      String instrumentId, java.time.LocalDate occurredOn, java.time.LocalDate entitlementDate, long grossAmountCent,
      long taxWithheldCent, Long perShareAmountCent, String note) {
    LedgerAccount cash = activeCashAccount(context.ownerUserId(), cashAccountId);
    return dividendReplacement(context, new DividendCommand(cashAccountId, instrumentId, occurredOn, entitlementDate,
        Money.of(grossAmountCent, cash.currency()), Money.of(taxWithheldCent, cash.currency()), perShareAmountCent,
        note));
  }

  /** Performs the dividend-specific read-only checks required by the preview endpoint. */
  @Transactional(readOnly = true)
  public void previewDividendByMinorUnit(String ownerUserId, String cashAccountId, String instrumentId,
      java.time.LocalDate occurredOn, java.time.LocalDate entitlementDate, long grossAmountCent,
      long taxWithheldCent, Long perShareAmountCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    DividendCommand command = new DividendCommand(cashAccountId, instrumentId, occurredOn, entitlementDate,
        Money.of(grossAmountCent, cashAccount.currency()), Money.of(taxWithheldCent, cashAccount.currency()),
        perShareAmountCent, note);
    validateDividendCommand(ownerUserId, command, cashAccount);
  }

  @Transactional
  public LedgerTransaction interest(String ownerUserId, IncomeCommand command) {
    return interest(ownerUserId, command, null);
  }

  @Transactional
  public LedgerTransaction interestReplacement(LedgerAppendContext context, IncomeCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    return interest(context.ownerUserId(), command, context);
  }

  private LedgerTransaction interest(String ownerUserId, IncomeCommand command, LedgerAppendContext context) {
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, command.cashAccountId());
    requireCurrency(cashAccount, command.grossAmount());
    requireCurrency(cashAccount, command.taxWithheld());
    LedgerAccount interestIncome = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.INTEREST_INCOME, cashAccount);
    LedgerAccount taxExpense = activeSystemAccount(ownerUserId,
        SystemLedgerAccount.WITHHOLDING_TAX_EXPENSE, cashAccount);
    LedgerTransaction transaction = context == null
        ? appendOriginal(ownerUserId, LedgerTransactionType.INTEREST, command.occurredOn(), command.note(),
        LedgerPostingTemplates.income(cashAccount.accountId(), interestIncome.accountId(),
            taxExpense.accountId(), command.grossAmount(), command.taxWithheld()), cashAccount)
        : appendReplacement(context, LedgerTransactionType.INTEREST, command.occurredOn(), command.note(),
            LedgerPostingTemplates.income(cashAccount.accountId(), interestIncome.accountId(),
                taxExpense.accountId(), command.grossAmount(), command.taxWithheld()), cashAccount);
    incomeDetailPort.insert(new IncomeDetail(idGenerator.next(), transaction.transactionId(), "INTEREST", null,
        null, command.grossAmount().cent(), command.taxWithheld().cent(), null, cashAccount.currency()));
    return transaction;
  }

  @Transactional
  public LedgerTransaction interestByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long grossAmountCent, long taxWithheldCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    return interest(ownerUserId, new IncomeCommand(cashAccountId, occurredOn,
        Money.of(grossAmountCent, cashAccount.currency()), Money.of(taxWithheldCent, cashAccount.currency()), note));
  }

  /** Appends an import-origin interest fact; absent legacy tax is represented as zero. */
  @Transactional
  public LedgerTransaction interestImportedByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long grossAmountCent, long taxWithheldCent, String note, String importExportFileId) {
    LedgerAccount cash = activeCashAccount(ownerUserId, cashAccountId);
    Money gross = Money.of(grossAmountCent, cash.currency());
    Money tax = Money.of(taxWithheldCent, cash.currency());
    LedgerAccount income = activeSystemAccount(ownerUserId, SystemLedgerAccount.INTEREST_INCOME, cash);
    LedgerAccount taxExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.WITHHOLDING_TAX_EXPENSE, cash);
    LedgerTransaction transaction = appendOriginal(ownerUserId, LedgerSourceType.IMPORT, importExportFileId, LedgerTransactionType.INTEREST,
        occurredOn, note, LedgerPostingTemplates.income(cash.accountId(), income.accountId(), taxExpense.accountId(),
            gross, tax), cash);
    incomeDetailPort.insert(new IncomeDetail(idGenerator.next(), transaction.transactionId(), "INTEREST", null, null,
        grossAmountCent, taxWithheldCent, null, cash.currency()));
    return transaction;
  }

  @Transactional
  public LedgerTransaction interestReplacementByMinorUnit(LedgerAppendContext context, String cashAccountId,
      java.time.LocalDate occurredOn, long grossAmountCent, long taxWithheldCent, String note) {
    LedgerAccount cash = activeCashAccount(context.ownerUserId(), cashAccountId);
    return interestReplacement(context, new IncomeCommand(cashAccountId, occurredOn,
        Money.of(grossAmountCent, cash.currency()), Money.of(taxWithheldCent, cash.currency()), note));
  }

  @Transactional
  public LedgerTransaction fee(String ownerUserId, FeeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount feeExpense = activeSystemAccount(ownerUserId, SystemLedgerAccount.FEE_EXPENSE,
        cashAccount);
    return appendOriginal(ownerUserId, LedgerTransactionType.FEE, command.occurredOn(), command.note(),
        LedgerPostingTemplates.fee(cashAccount.accountId(), feeExpense.accountId(), command.amount()), cashAccount);
  }

  @Transactional
  public LedgerTransaction feeReplacement(LedgerAppendContext context, FeeCommand command) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(command, "command must not be null");
    LedgerAccount cashAccount = activeCashAccount(context.ownerUserId(), command.cashAccountId());
    requireCurrency(cashAccount, command.amount());
    LedgerAccount feeExpense = activeSystemAccount(context.ownerUserId(), SystemLedgerAccount.FEE_EXPENSE,
        cashAccount);
    return appendReplacement(context, LedgerTransactionType.FEE, command.occurredOn(), command.note(),
        LedgerPostingTemplates.fee(cashAccount.accountId(), feeExpense.accountId(), command.amount()), cashAccount);
  }

  @Transactional
  public LedgerTransaction feeByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cashAccount = activeCashAccount(ownerUserId, cashAccountId);
    return fee(ownerUserId, new FeeCommand(cashAccountId, occurredOn, Money.of(amountCent, cashAccount.currency()), note));
  }

  /** Appends an import-origin fee fact, including the confirmed fee-only legacy futures roll. */
  @Transactional
  public LedgerTransaction feeImportedByMinorUnit(String ownerUserId, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note, String importExportFileId) {
    LedgerAccount cash = activeCashAccount(ownerUserId, cashAccountId);
    LedgerAccount expense = activeSystemAccount(ownerUserId, SystemLedgerAccount.FEE_EXPENSE, cash);
    return appendOriginal(ownerUserId, LedgerSourceType.IMPORT, importExportFileId, LedgerTransactionType.FEE, occurredOn, note,
        LedgerPostingTemplates.fee(cash.accountId(), expense.accountId(), Money.of(amountCent, cash.currency())), cash);
  }

  @Transactional
  public LedgerTransaction feeReplacementByMinorUnit(LedgerAppendContext context, String cashAccountId,
      java.time.LocalDate occurredOn, long amountCent, String note) {
    LedgerAccount cash = activeCashAccount(context.ownerUserId(), cashAccountId);
    return feeReplacement(context, new FeeCommand(cashAccountId, occurredOn, Money.of(amountCent, cash.currency()),
        note));
  }

  private LedgerTransaction appendOriginal(String ownerUserId, LedgerTransactionType transactionType,
      java.time.LocalDate occurredOn, String note, BalancedPostings balancedPostings,
      LedgerAccount... cashAccounts) {
    return appendOriginal(ownerUserId, LedgerSourceType.MANUAL, null, transactionType, occurredOn, note,
        balancedPostings, cashAccounts);
  }

  private LedgerTransaction appendOriginal(String ownerUserId, LedgerSourceType sourceType, String importExportFileId,
      LedgerTransactionType transactionType, java.time.LocalDate occurredOn, String note,
      BalancedPostings balancedPostings, LedgerAccount... cashAccounts) {
    requireText(ownerUserId, "ownerUserId");
    long lockedLedgerVersion = transactionPort.lockCurrentLedgerVersion(ownerUserId, idGenerator.next());
    ensureNonNegativeCashBalances(ownerUserId, balancedPostings, cashAccounts);
    long ledgerVersion = transactionPort.reserveNextLedgerVersion(ownerUserId, lockedLedgerVersion);
    return persist(ownerUserId, transactionType, occurredOn, note, balancedPostings, null, sourceType,
        importExportFileId, ledgerVersion);
  }

  private LedgerTransaction appendReplacement(LedgerAppendContext context, LedgerTransactionType transactionType,
      java.time.LocalDate occurredOn, String note, BalancedPostings balancedPostings, LedgerAccount... cashAccounts) {
    ensureNonNegativeCashBalances(context.ownerUserId(), balancedPostings, cashAccounts);
    return persist(context.ownerUserId(), transactionType, occurredOn, note, balancedPostings, context,
        LedgerSourceType.CORRECTION_REPLACEMENT, null, context.ledgerVersion());
  }

  private LedgerTransaction persist(String ownerUserId, LedgerTransactionType transactionType,
      java.time.LocalDate occurredOn, String note, BalancedPostings balancedPostings, LedgerAppendContext context,
      LedgerSourceType sourceType, String importExportFileId, long ledgerVersion) {
    requireText(ownerUserId, "ownerUserId");
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    String transactionId = idGenerator.next();
    List<LedgerPostingFact> postings = toPostingFacts(balancedPostings);
    LedgerTransaction transaction = context == null
        ? sourceType == LedgerSourceType.IMPORT
            ? LedgerTransaction.imported(transactionId, ownerUserId, transactionType, occurredOn, importExportFileId, ledgerVersion, note,
                postings)
            : LedgerTransaction.original(transactionId, ownerUserId, transactionType, occurredOn, ledgerVersion, note,
                postings)
        : LedgerTransaction.replacement(transactionId, ownerUserId, transactionType, occurredOn,
            context.correctionRootTransactionId(), context.revisionNo(), ledgerVersion, note, postings, List.of());
    transactionPort.append(transaction);
    transactionEventPort.recordAppended(transaction);
    return transaction;
  }

  private void validateDividendCommand(String ownerUserId, DividendCommand command, LedgerAccount cashAccount) {
    Objects.requireNonNull(command, "command must not be null");
    requireText(command.instrumentId(), "instrumentId");
    Objects.requireNonNull(command.occurredOn(), "occurredOn must not be null");
    Objects.requireNonNull(command.entitlementDate(), "entitlementDate must not be null");
    requireCurrency(cashAccount, command.grossAmount());
    requireCurrency(cashAccount, command.taxWithheld());
    validateDividendEntitlement(ownerUserId, command, cashAccount);
  }

  private void validateDividendEntitlement(String ownerUserId, DividendCommand command, LedgerAccount cashAccount) {
    if (spotInstrumentPort != null) {
      TradableInstrument instrument = spotInstrumentPort.findById(command.instrumentId())
          .orElseThrow(() -> new IllegalArgumentException("dividend instrument was not found or is not a spot instrument"));
      if (instrument.nativeCurrency() != cashAccount.currency()) {
        throw new IllegalArgumentException("dividend instrument currency must match the selected cash account");
      }
    }
    if (command.perShareAmountCent() == null) {
      return;
    }
    if (command.perShareAmountCent() <= 0) {
      throw new IllegalArgumentException("perShareAmountCent must be positive");
    }
    BigDecimal quantity = historyReplayer.quantityAt(ownerUserId, command.instrumentId(), command.entitlementDate());
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("dividend entitlement date has no open position");
    }
    try {
      long expectedGrossCent = quantity.multiply(BigDecimal.valueOf(command.perShareAmountCent()))
          .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
      if (expectedGrossCent != command.grossAmount().cent()) {
        throw new IllegalArgumentException("dividend gross amount does not equal entitlement quantity times per-share amount");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("dividend per-share amount cannot be represented as an exact cent amount",
          exception);
    }
  }

  private void ensureNonNegativeCashBalances(String ownerUserId, BalancedPostings proposedPostings,
      LedgerAccount... cashAccounts) {
    Map<String, LedgerAccount> accountsById = new HashMap<>();
    Map<String, Long> balances = new HashMap<>();
    for (LedgerAccount account : cashAccounts) {
      if (account == null) {
        throw new IllegalArgumentException("cash account must not be null");
      }
      accountsById.put(account.accountId(), account);
      balances.put(account.accountId(), 0L);
    }
    for (LedgerPostingFact fact : transactionPort.findPostingFactsByOwner(ownerUserId)) {
      applyCashPosting(accountsById, balances, fact.accountId(), fact.side(), fact.amount());
    }
    for (Posting posting : proposedPostings.postings()) {
      applyCashPosting(accountsById, balances, posting.accountId(), posting.side(), posting.amount());
    }
    for (Map.Entry<String, Long> balance : balances.entrySet()) {
      if (balance.getValue() < 0) {
        throw new InsufficientBalanceException(balance.getKey());
      }
    }
  }

  private static void applyCashPosting(Map<String, LedgerAccount> accountsById, Map<String, Long> balances,
      String accountId, PostingSide side, Money amount) {
    LedgerAccount account = accountsById.get(accountId);
    if (account == null) {
      return;
    }
    if (account.currency() != amount.currency()) {
      throw new IllegalStateException("cash posting currency does not match its account");
    }
    long current = balances.get(accountId);
    try {
      balances.put(accountId, side == PostingSide.DEBIT
          ? Math.addExact(current, amount.cent())
          : Math.subtractExact(current, amount.cent()));
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("cash balance overflow", exception);
    }
  }

  private List<LedgerPostingFact> toPostingFacts(BalancedPostings balancedPostings) {
    int[] postingNo = {1};
    return balancedPostings.postings().stream().map(posting -> new LedgerPostingFact(idGenerator.next(),
        posting.accountId(), postingNo[0]++, posting.side(), posting.amount())).toList();
  }

  private LedgerAccount activeCashAccount(String ownerUserId, String accountId) {
    requireText(ownerUserId, "ownerUserId");
    LedgerAccount account = accountPort.findByIdAndOwner(accountId, ownerUserId)
        .orElseThrow(() -> new IllegalArgumentException("cash account was not found for the authenticated owner"));
    if (account.accountKind() != LedgerAccountKind.ASSET_CASH) {
      throw new IllegalArgumentException("account must be a cash account");
    }
    if (account.status() != LedgerAccountStatus.ACTIVE) {
      throw new IllegalArgumentException("cash account is disabled");
    }
    return account;
  }

  private LedgerAccount activeSystemAccount(String ownerUserId, SystemLedgerAccount systemAccount,
      LedgerAccount cashAccount) {
    LedgerAccount account = accountPort.findByOwnerAndCode(ownerUserId,
            systemAccount.accountCode(cashAccount.currency()))
        .orElseThrow(() -> new IllegalArgumentException("required system account was not provisioned"));
    if (account.accountKind() != systemAccount.accountKind()
        || account.status() != LedgerAccountStatus.ACTIVE
        || account.currency() != cashAccount.currency()) {
      throw new IllegalArgumentException("required system account is invalid");
    }
    return account;
  }

  private static void requireCurrency(LedgerAccount account, Money amount) {
    Objects.requireNonNull(amount, "amount must not be null");
    if (account.currency() != amount.currency()) {
      throw new IllegalArgumentException("amount currency must match the selected cash account");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
