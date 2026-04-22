-- Initial schema baseline for Merkantil.
-- Snapshot of the live merkantil_db schema at the time Flyway was adopted.
-- Existing environments are already baselined past V1 (flyway.baseline-version=1)
-- so this script only runs on fresh databases.

SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';

-- -----------------------------------------------------
-- users
-- -----------------------------------------------------
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance` decimal(19,4) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `version` bigint DEFAULT NULL,
  `first_name` varchar(255) NOT NULL,
  `last_name` varchar(255) NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `two_factor_enabled` bit(1) NOT NULL,
  `banned` bit(1) NOT NULL,
  `google_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKovh8xmu9ac27t18m56gri58i1` (`google_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- persistent_logins (Spring Security remember-me)
-- -----------------------------------------------------
CREATE TABLE `persistent_logins` (
  `username` varchar(64) NOT NULL,
  `series` varchar(64) NOT NULL,
  `token` varchar(64) NOT NULL,
  `last_used` timestamp NOT NULL,
  PRIMARY KEY (`series`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- stocks
-- -----------------------------------------------------
CREATE TABLE `stocks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `currency` varchar(50) DEFAULT NULL,
  `current_price` double DEFAULT NULL,
  `day_high` double DEFAULT NULL,
  `day_low` double DEFAULT NULL,
  `exchange` varchar(100) DEFAULT NULL,
  `industry` varchar(100) DEFAULT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `last_updated` datetime(6) DEFAULT NULL,
  `market_cap` double DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `previous_close` double DEFAULT NULL,
  `sector` varchar(100) DEFAULT NULL,
  `symbol` varchar(10) NOT NULL,
  `volume` bigint DEFAULT NULL,
  `extended_hours_price` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_stock_symbol` (`symbol`),
  KEY `idx_stock_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- stock_price_history
-- -----------------------------------------------------
CREATE TABLE `stock_price_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `close` double NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `date` date NOT NULL,
  `high` double NOT NULL,
  `low` double NOT NULL,
  `open` double NOT NULL,
  `symbol` varchar(10) NOT NULL,
  `volume` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_stock_history_symbol_date` (`symbol`,`date`),
  KEY `idx_stock_history_symbol` (`symbol`),
  KEY `idx_stock_history_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- login_sessions
-- -----------------------------------------------------
CREATE TABLE `login_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `device_info` varchar(255) DEFAULT NULL,
  `ip` varchar(255) DEFAULT NULL,
  `session_id` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_login_sessions_session_id` (`session_id`),
  KEY `idx_login_sessions_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- payment_methods
-- -----------------------------------------------------
CREATE TABLE `payment_methods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `card_type` varchar(255) NOT NULL,
  `cardholder_name` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expiry_month` int NOT NULL,
  `expiry_year` int NOT NULL,
  `last4` varchar(4) NOT NULL,
  `user_id` bigint NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKin7rtmim3ljrrhh5kxbq27s2v` (`user_id`),
  CONSTRAINT `FKin7rtmim3ljrrhh5kxbq27s2v` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- portfolios
-- -----------------------------------------------------
CREATE TABLE `portfolios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `average_buy_price` double NOT NULL,
  `quantity` int NOT NULL,
  `symbol` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_portfolio_user_symbol` (`user_id`,`symbol`),
  KEY `idx_portfolio_user` (`user_id`),
  KEY `idx_portfolio_user_symbol` (`user_id`,`symbol`),
  CONSTRAINT `FK9xt36kgm9cxsf79r2me0d9f6u` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- orders
-- -----------------------------------------------------
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `at_price` double DEFAULT NULL,
  `order_type` enum('LIMIT','MARKET') NOT NULL,
  `quantity` int NOT NULL,
  `symbol` varchar(255) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `type` enum('BUY','SELL') NOT NULL,
  `user_id` bigint NOT NULL,
  `limit_price` double DEFAULT NULL,
  `status` enum('CANCELLED','FILLED','OPEN') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_user_timestamp` (`user_id`,`timestamp`),
  KEY `idx_order_symbol_timestamp` (`symbol`,`timestamp`),
  KEY `idx_order_user_symbol` (`user_id`,`symbol`),
  CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- transactions
-- -----------------------------------------------------
CREATE TABLE `transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `price` double NOT NULL,
  `quantity` int NOT NULL,
  `stock_symbol` varchar(255) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `total_amount` double NOT NULL,
  `type` enum('BUY','SELL') NOT NULL,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tx_user_timestamp` (`user_id`,`timestamp`),
  KEY `idx_tx_symbol_timestamp` (`stock_symbol`,`timestamp`),
  KEY `idx_tx_user_symbol` (`user_id`,`stock_symbol`),
  KEY `FKfyxndk58yiq2vpn0yd4m09kbt` (`order_id`),
  CONSTRAINT `FKfyxndk58yiq2vpn0yd4m09kbt` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKqwv7rmvc8va8rep7piikrojds` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- wallet_transactions
-- -----------------------------------------------------
CREATE TABLE `wallet_transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(19,4) NOT NULL,
  `timestamp` datetime(6) NOT NULL,
  `type` varchar(20) NOT NULL,
  `payment_method_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_wallet_tx_user_timestamp` (`user_id`,`timestamp`),
  KEY `FK3bjpcyvkl1of3bhkuotims6n9` (`payment_method_id`),
  CONSTRAINT `FK3bjpcyvkl1of3bhkuotims6n9` FOREIGN KEY (`payment_method_id`) REFERENCES `payment_methods` (`id`),
  CONSTRAINT `FKrtsa3qtjhd0rn4xb92na03vd` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------
-- watchlist_items
-- -----------------------------------------------------
CREATE TABLE `watchlist_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `added_at` datetime(6) NOT NULL,
  `stock_symbol` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf0e2rb44q8kxqh6h02pokwycl` (`user_id`,`stock_symbol`),
  CONSTRAINT `FK3hh4g1xnal6keiwwf72epes0e` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
