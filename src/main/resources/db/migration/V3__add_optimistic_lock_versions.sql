-- Add JPA @Version columns to portfolios and orders for optimistic concurrency
-- control. Without this, two concurrent operations on the same row (e.g. two
-- parallel SELLs of the same position, or a user cancel racing the LIMIT-order
-- scheduler's fill) can both pass their read-time checks and produce
-- inconsistent state. The version is bumped by Hibernate on every UPDATE; a
-- conflicting write fails with OptimisticLockingFailureException.
--
-- DEFAULT 0 backfills existing rows; new inserts start at 0 and increment from
-- there. users.balance already has @Version (column users.version, added with
-- the entity in V1).

ALTER TABLE `portfolios` ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;
ALTER TABLE `orders`     ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0;
