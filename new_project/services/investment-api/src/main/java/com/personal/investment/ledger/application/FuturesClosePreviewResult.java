package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

public record FuturesClosePreviewResult(CurrencyCode currency, List<PreviewPosting> postings,
                                        long releasedMarginCent, long realizedPnlCent) {
}
