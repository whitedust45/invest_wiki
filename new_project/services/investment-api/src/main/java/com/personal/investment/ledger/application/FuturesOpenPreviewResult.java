package com.personal.investment.ledger.application;

import com.personal.investment.ledger.domain.CurrencyCode;
import java.util.List;

public record FuturesOpenPreviewResult(CurrencyCode currency, List<PreviewPosting> postings,
                                       List<String> accountProvisioning) {
}
