-- ============================================================
-- V060__trade.sql
-- 功能组【交易】版本号区间：V060–V069
-- 表：cart_items / orders / order_items / payment_transactions /
--     order_refunds
--
-- 重构说明：
--   - 添加全部外键约束（cart->users/products, orders->users,
--     order_items->orders/products, payments->orders/users,
--     refunds->orders/users）
--   - 补建 product_reviews.order_id -> orders.id 跨模块外键
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 购物车（同一用户+商品+SKU 唯一）
CREATE TABLE IF NOT EXISTS `cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint,
  `quantity` int NOT NULL,
  `selected` tinyint(1) NOT NULL DEFAULT 1,
  `added_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_product_sku` (`user_id`, `product_id`, `sku_id`),
  KEY `idx_cart_items_product_id` (`product_id`),
  CONSTRAINT `fk_cart_items_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cart_items_product_id` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- 订单
CREATE TABLE IF NOT EXISTS `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(50) NOT NULL,
  `user_id` bigint NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `pay_amount` decimal(10,2) NOT NULL,
  `status` int DEFAULT 0,
  `receiver_info` json NOT NULL,
  `payment_method` varchar(20),
  `shipping_method` varchar(50),
  `remark` varchar(200),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `idx_orders_user_status` (`user_id`, `status`),
  CONSTRAINT `fk_orders_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 订单项
CREATE TABLE IF NOT EXISTS `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint,
  `product_name` varchar(200) NOT NULL,
  `sku_specs` json,
  `price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_items_order_id` (`order_id`),
  KEY `idx_order_items_product_id` (`product_id`),
  CONSTRAINT `fk_order_items_order_id` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_items_product_id` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';

-- 支付流水（transaction_no 唯一保障回调幂等）
CREATE TABLE IF NOT EXISTS `payment_transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_no` varchar(50),
  `transaction_no` varchar(100),
  `payment_method` varchar(30) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `status` varchar(20) NOT NULL,
  `channel_trade_no` varchar(100),
  `paid_at` datetime,
  `notify_content` text,
  `user_id` bigint NOT NULL,
  `remark` varchar(500),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_txn_no` (`transaction_no`),
  KEY `idx_payment_order_id` (`order_id`),
  KEY `idx_payment_user_id` (`user_id`),
  CONSTRAINT `fk_payment_transactions_order_id` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_payment_transactions_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

-- 退款
CREATE TABLE IF NOT EXISTS `order_refunds` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_no` varchar(50) NOT NULL,
  `order_id` bigint NOT NULL,
  `order_no` varchar(50),
  `user_id` bigint NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `reason` varchar(500),
  `status` int NOT NULL,
  `admin_remark` varchar(500),
  `processed_at` datetime,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_refunds_refund_no` (`refund_no`),
  KEY `idx_order_refunds_order_id` (`order_id`),
  KEY `idx_order_refunds_user_id` (`user_id`),
  CONSTRAINT `fk_order_refunds_order_id` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_order_refunds_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款表';

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 跨模块外键：product_reviews.order_id -> orders.id
-- （V050 创建 product_reviews 时 orders 表尚不存在，此处补建）
-- ------------------------------------------------------------
SET @has_fk := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_reviews'
    AND CONSTRAINT_NAME = 'fk_product_reviews_order_id'
);
SET @ddl := IF(@has_fk = 0,
  'ALTER TABLE `product_reviews` ADD CONSTRAINT `fk_product_reviews_order_id` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
