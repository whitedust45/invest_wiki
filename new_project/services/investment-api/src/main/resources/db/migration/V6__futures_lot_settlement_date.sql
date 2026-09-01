ALTER TABLE portfolio_db.futures_position_lot
  ADD COLUMN last_settlement_on DATE NULL AFTER last_settlement_price_points;

UPDATE portfolio_db.futures_position_lot
SET last_settlement_on = opened_on
WHERE last_settlement_on IS NULL;

ALTER TABLE portfolio_db.futures_position_lot
  MODIFY COLUMN last_settlement_on DATE NOT NULL,
  ADD KEY idx_futures_position_lot_settlement (futures_position_id, last_settlement_on);
