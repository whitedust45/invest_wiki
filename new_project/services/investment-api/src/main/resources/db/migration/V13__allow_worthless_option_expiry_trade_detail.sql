-- A worthless option expiry has no sale price, but it remains an auditable CLOSE trade detail with its contract multiplier.
-- Keep exactly one of the two price representations for every priced trade; the third branch is only for this expiry fact.
ALTER TABLE ledger_db.ledger_trade_detail
  DROP CHECK ledger_trade_detail_chk_3,
  ADD CONSTRAINT ck_ledger_trade_detail_price_or_option_expiry CHECK (
    (unit_price_cent IS NOT NULL AND price_points IS NULL)
    OR (unit_price_cent IS NULL AND price_points IS NOT NULL)
    OR (unit_price_cent IS NULL AND price_points IS NULL AND option_contract_multiplier IS NOT NULL)
  );
