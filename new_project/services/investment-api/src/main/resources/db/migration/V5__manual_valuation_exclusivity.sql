ALTER TABLE portfolio_db.portfolio_manual_valuation
  ADD CONSTRAINT ck_manual_valuation_exactly_one_amount CHECK (
    (market_value_cent IS NOT NULL AND unit_price_cent IS NULL)
    OR (market_value_cent IS NULL AND unit_price_cent IS NOT NULL)
  ),
  ADD CONSTRAINT ck_manual_valuation_positive_amount CHECK (
    (market_value_cent IS NULL OR market_value_cent > 0)
    AND (unit_price_cent IS NULL OR unit_price_cent > 0)
  );
