package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

/** Read-only result for an explicit manual futures daily-settlement draft. */
public record FuturesDailySettlementPreviewResult(CurrencyCode currency, List<PreviewPosting> postings,
                                                  long realizedPnlCent) {
}
