package com.personal.investment.portfolio.application;

/** Avoids disclosing whether an account belongs to another owner. */
public class PortfolioResourceNotFoundException extends RuntimeException {
  public PortfolioResourceNotFoundException() {
    super("portfolio resource was not found");
  }
}
