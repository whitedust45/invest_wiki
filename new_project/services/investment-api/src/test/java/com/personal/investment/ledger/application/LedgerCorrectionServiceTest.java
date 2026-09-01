package com.personal.investment.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.personal.investment.ledger.domain.CurrencyCode;
import com.personal.investment.ledger.domain.LedgerPostingFact;
import com.personal.investment.ledger.domain.LedgerTransaction;
import com.personal.investment.ledger.domain.LedgerTransactionType;
import com.personal.investment.ledger.domain.Money;
import com.personal.investment.ledger.domain.PostingSide;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LedgerCorrectionServiceTest {
  private static final String OWNER = "01K8D43J4YFN7X9R2B6C8M0V3P";
  private static final String TARGET = "01K8D43J4YFN7X9R2B6C8M0V31";

  @Test
  void appendsMirrorReversalAtTheOriginalBusinessDateAndNextRevision() {
    LedgerTransaction original = LedgerTransaction.original(TARGET, OWNER, LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.of(2026, 7, 25), 1, "原始入金", List.of(
            posting("01K8D43J4YFN7X9R2B6C8M0V41", "cash", 1, PostingSide.DEBIT, 1_000),
            posting("01K8D43J4YFN7X9R2B6C8M0V42", "equity", 2, PostingSide.CREDIT, 1_000)));
    CapturingLedgerPort ledger = new CapturingLedgerPort();
    LedgerCorrectionService service = new LedgerCorrectionService(new SingleTargetPort(original), ledger,
        new SequenceIdGenerator(), LedgerTransactionEventPort.noop(), detail -> { }, SpotHistoryReplayer.noop());

    CorrectionResult result = service.reverse(OWNER, TARGET);

    LedgerTransaction reversal = result.reversal();
    assertThat(reversal.transactionType()).isEqualTo(LedgerTransactionType.REVERSAL);
    assertThat(reversal.occurredOn()).isEqualTo(LocalDate.of(2026, 7, 25));
    assertThat(reversal.correctionRootTransactionId()).isEqualTo(TARGET);
    assertThat(reversal.reversalOfTransactionId()).isEqualTo(TARGET);
    assertThat(reversal.revisionNo()).isEqualTo(1);
    assertThat(reversal.postings()).extracting(LedgerPostingFact::side)
        .containsExactly(PostingSide.CREDIT, PostingSide.DEBIT);
    assertThat(ledger.appended).containsExactly(reversal);
  }

  @Test
  void rejectsASecondDirectReversalWithoutAppendingAnything() {
    LedgerTransaction original = LedgerTransaction.original(TARGET, OWNER, LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.of(2026, 7, 25), 1, null, List.of(
            posting("01K8D43J4YFN7X9R2B6C8M0V41", "cash", 1, PostingSide.DEBIT, 1_000),
            posting("01K8D43J4YFN7X9R2B6C8M0V42", "equity", 2, PostingSide.CREDIT, 1_000)));
    CapturingLedgerPort ledger = new CapturingLedgerPort();
    LedgerCorrectionService service = new LedgerCorrectionService(new SingleTargetPort(original, true), ledger,
        new SequenceIdGenerator(), LedgerTransactionEventPort.noop(), detail -> { }, SpotHistoryReplayer.noop());

    assertThatIllegalStateException().isThrownBy(() -> service.reverse(OWNER, TARGET))
        .withMessageContaining("already has a direct reversal");
    assertThat(ledger.appended).isEmpty();
  }

  @Test
  void appendsReplacementAtomicallyWithTheNextRevisionAndLedgerVersion() {
    LedgerTransaction original = LedgerTransaction.original(TARGET, OWNER, LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.of(2026, 7, 25), 1, null, List.of(
            posting("01K8D43J4YFN7X9R2B6C8M0V41", "cash", 1, PostingSide.DEBIT, 1_000),
            posting("01K8D43J4YFN7X9R2B6C8M0V42", "equity", 2, PostingSide.CREDIT, 1_000)));
    CapturingLedgerPort ledger = new CapturingLedgerPort();
    LedgerCorrectionService service = new LedgerCorrectionService(new SingleTargetPort(original), ledger,
        new SequenceIdGenerator(), LedgerTransactionEventPort.noop(), detail -> { }, SpotHistoryReplayer.noop());

    CorrectionResult result = service.correct(OWNER, TARGET, context -> {
      LedgerTransaction replacement = LedgerTransaction.replacement("01K8D43J4YFN7X9R2B6C8M0V51", OWNER,
          LedgerTransactionType.EXTERNAL_FUNDING, LocalDate.of(2026, 7, 26), context.correctionRootTransactionId(),
          context.revisionNo(), context.ledgerVersion(), "替代入金", List.of(
              posting("01K8D43J4YFN7X9R2B6C8M0V52", "cash", 1, PostingSide.DEBIT, 2_000),
              posting("01K8D43J4YFN7X9R2B6C8M0V53", "equity", 2, PostingSide.CREDIT, 2_000)), List.of());
      ledger.append(replacement);
      return replacement;
    });

    assertThat(result.reversal().ledgerVersion()).isEqualTo(2);
    assertThat(result.replacement()).isNotNull();
    assertThat(result.replacement().ledgerVersion()).isEqualTo(3);
    assertThat(result.replacement().revisionNo()).isEqualTo(2);
    assertThat(result.replacement().correctionRootTransactionId()).isEqualTo(TARGET);
    assertThat(ledger.appended).containsExactly(result.reversal(), result.replacement());
  }

  @Test
  void rebuildsFuturesProjectionWheneverAReversalChangesHistoricalFacts() {
    LedgerTransaction original = LedgerTransaction.original(TARGET, OWNER, LedgerTransactionType.FUTURES_OPEN,
        LocalDate.of(2026, 7, 25), 1, null, List.of(
            posting("01K8D43J4YFN7X9R2B6C8M0V41", "available", 1, PostingSide.CREDIT, 1_000),
            posting("01K8D43J4YFN7X9R2B6C8M0V42", "locked", 2, PostingSide.DEBIT, 1_000)));
    CapturingLedgerPort ledger = new CapturingLedgerPort();
    List<Long> futureVersions = new ArrayList<>();
    LedgerCorrectionService service = new LedgerCorrectionService(new SingleTargetPort(original), ledger,
        new SequenceIdGenerator(), LedgerTransactionEventPort.noop(), detail -> { }, SpotHistoryReplayer.noop(),
        (ownerUserId, version) -> futureVersions.add(version));

    service.reverse(OWNER, TARGET);

    assertThat(futureVersions).containsExactly(2L);
  }

  @Test
  void inheritsTheOriginalStrategyAttributionForBothReversalAndReplacement() {
    LedgerTransaction original = LedgerTransaction.original(TARGET, OWNER, LedgerTransactionType.EXTERNAL_FUNDING,
        LocalDate.of(2026, 7, 25), 1, null, List.of(
            posting("01K8D43J4YFN7X9R2B6C8M0V41", "cash", 1, PostingSide.DEBIT, 1_000),
            posting("01K8D43J4YFN7X9R2B6C8M0V42", "equity", 2, PostingSide.CREDIT, 1_000)));
    CapturingLedgerPort ledger = new CapturingLedgerPort();
    LedgerCorrectionService service = new LedgerCorrectionService(new SingleTargetPort(original, false, "IC_IM"), ledger,
        new SequenceIdGenerator(), LedgerTransactionEventPort.noop(), detail -> { }, SpotHistoryReplayer.noop());
    List<String> replacementContextStrategies = new ArrayList<>();

    service.correct(OWNER, TARGET, context -> {
      replacementContextStrategies.add(context.strategyKey());
      LedgerTransaction replacement = LedgerTransaction.replacement("01K8D43J4YFN7X9R2B6C8M0V51", OWNER,
          LedgerTransactionType.EXTERNAL_FUNDING, LocalDate.of(2026, 7, 26), context.correctionRootTransactionId(),
          context.revisionNo(), context.ledgerVersion(), "替代入金", List.of(
              posting("01K8D43J4YFN7X9R2B6C8M0V52", "cash", 1, PostingSide.DEBIT, 2_000),
              posting("01K8D43J4YFN7X9R2B6C8M0V53", "equity", 2, PostingSide.CREDIT, 2_000)), List.of());
      ledger.append(replacement);
      return replacement;
    });

    assertThat(replacementContextStrategies).containsExactly("IC_IM");
    assertThat(ledger.strategyKeysAtAppend).containsExactly("IC_IM", "IC_IM");
  }

  private static LedgerPostingFact posting(String id, String account, int no, PostingSide side, long amountCent) {
    return new LedgerPostingFact(id, account, no, side, Money.of(amountCent, CurrencyCode.USD));
  }

  private static final class SingleTargetPort implements LedgerCorrectionPort {
    private final LedgerTransaction transaction;
    private final boolean hasReversal;

    private SingleTargetPort(LedgerTransaction transaction) {
      this(transaction, false);
    }

    private SingleTargetPort(LedgerTransaction transaction, boolean hasReversal) {
      this(transaction, hasReversal, null);
    }

    private SingleTargetPort(LedgerTransaction transaction, boolean hasReversal, String strategyKey) {
      this.transaction = transaction;
      this.hasReversal = hasReversal;
      this.strategyKey = strategyKey;
    }

    private final String strategyKey;

    @Override
    public Optional<CorrectionTarget> findTarget(String ownerUserId, String transactionId) {
      return OWNER.equals(ownerUserId) && TARGET.equals(transactionId)
          ? Optional.of(new CorrectionTarget(transaction, null, strategyKey)) : Optional.empty();
    }

    @Override
    public boolean hasDirectReversal(String ownerUserId, String transactionId) {
      return hasReversal;
    }

    @Override
    public int nextRevisionNo(String ownerUserId, String correctionRootTransactionId) {
      return 1;
    }
  }

  private static final class CapturingLedgerPort implements LedgerTransactionPort {
    private final List<LedgerTransaction> appended = new ArrayList<>();
    private final List<String> strategyKeysAtAppend = new ArrayList<>();

    @Override
    public long lockCurrentLedgerVersion(String ownerUserId, String newLedgerStateId) {
      return 1;
    }

    @Override
    public long reserveNextLedgerVersion(String ownerUserId, long lockedLedgerVersion) {
      return lockedLedgerVersion + 1;
    }

    @Override
    public List<LedgerPostingFact> findPostingFactsByOwner(String ownerUserId) {
      return List.of();
    }

    @Override
    public void append(LedgerTransaction transaction) {
      appended.add(transaction);
      strategyKeysAtAppend.add(LedgerAppendMetadata.strategyKey());
    }
  }

  private static final class SequenceIdGenerator implements LedgerIdGenerator {
    private int sequence;

    @Override
    public String next() {
      return "01K8D43J4YFN7X9R2B6C8M0V" + String.format("%02d", sequence++);
    }
  }
}
