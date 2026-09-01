package com.personal.investment.identity.interfaces;

import java.util.List;

public record ApiProblemResponse(String code, String message, String traceId, List<String> details) {
}
