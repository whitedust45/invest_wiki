-- Phase 2 S0 account invariant. Existing project data is test-only; remove the obsolete
-- globally provisioned investment system accounts before enforcing the approved naming model.
DELETE FROM ledger_db.ledger_account
WHERE account_kind = 'ASSET_INVESTMENT'
  AND account_code LIKE 'SYS:INVESTMENT_ASSET:%';

ALTER TABLE ledger_db.ledger_account
  ADD COLUMN cash_display_name VARCHAR(128)
    GENERATED ALWAYS AS (
      CASE WHEN account_kind = 'ASSET_CASH' THEN display_name ELSE NULL END
    ) STORED,
  ADD UNIQUE KEY uk_ledger_account_owner_currency_cash_display_name
    (owner_user_id, currency, cash_display_name);
