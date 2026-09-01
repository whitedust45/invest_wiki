package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

public record FuturesMarginPreviewResult(CurrencyCode currency, List<PreviewPosting> postings,
                                         List<String> accountProvisioning) {
}
