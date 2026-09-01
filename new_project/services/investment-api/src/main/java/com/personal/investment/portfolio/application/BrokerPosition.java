package com.personal.investment.portfolio.application;

import java.math.BigDecimal;

public record BrokerPosition(String instrumentId, BigDecimal quantity) {
}
