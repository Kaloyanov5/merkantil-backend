-- Convert money/price columns from DOUBLE to DECIMAL(19,4) for exact financial
-- precision, matching users.balance and wallet_transactions.amount which were
-- already DECIMAL(19,4). marketCap stays DOUBLE — it is a display-only aggregate
-- and never participates in money arithmetic.

ALTER TABLE `stocks`
  MODIFY COLUMN `current_price`        decimal(19,4) DEFAULT NULL,
  MODIFY COLUMN `previous_close`       decimal(19,4) DEFAULT NULL,
  MODIFY COLUMN `day_high`             decimal(19,4) DEFAULT NULL,
  MODIFY COLUMN `day_low`              decimal(19,4) DEFAULT NULL,
  MODIFY COLUMN `extended_hours_price` decimal(19,4) DEFAULT NULL;

ALTER TABLE `stock_price_history`
  MODIFY COLUMN `open`  decimal(19,4) NOT NULL,
  MODIFY COLUMN `high`  decimal(19,4) NOT NULL,
  MODIFY COLUMN `low`   decimal(19,4) NOT NULL,
  MODIFY COLUMN `close` decimal(19,4) NOT NULL;

ALTER TABLE `portfolios`
  MODIFY COLUMN `average_buy_price` decimal(19,4) NOT NULL;

ALTER TABLE `orders`
  MODIFY COLUMN `at_price`    decimal(19,4) DEFAULT NULL,
  MODIFY COLUMN `limit_price` decimal(19,4) DEFAULT NULL;

ALTER TABLE `transactions`
  MODIFY COLUMN `price`        decimal(19,4) NOT NULL,
  MODIFY COLUMN `total_amount` decimal(19,4) NOT NULL;
