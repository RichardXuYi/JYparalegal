-- ============================================================
-- V050__products.sql
-- 功能组【商品】版本号区间：V050–V059
-- 表：product_categories / products / product_skus /
--     product_attributes / product_reviews
--
-- 重构说明：
--   - 补列 product_skus.version（实体 @Version 乐观锁，原迁移缺失）
--   - 添加外键约束（products->categories, skus/attributes/reviews->products, reviews->users）
--   - 补建外键列索引
--   - 所有时间戳列添加 DEFAULT CURRENT_TIMESTAMP
--   - product_reviews.order_id 外键在 V060（orders 表创建后）补建
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 商品分类
CREATE TABLE IF NOT EXISTS `product_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `parent_id` bigint,
  `icon` varchar(255),
  `sort_order` int DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_categories_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 商品
CREATE TABLE IF NOT EXISTS `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint,
  `name` varchar(200) NOT NULL,
  `subtitle` varchar(200),
  `main_image` varchar(255),
  `detail_images` json,
  `description` LONGTEXT,
  `status` int DEFAULT 0,
  `sales_count` int DEFAULT 0,
  `stock` int DEFAULT 0,
  `rating` decimal(2,1) DEFAULT 5.0,
  `product_type` varchar(20) DEFAULT 'STANDARD',
  `author` varchar(100),
  `isbn` varchar(50),
  `duration` int,
  `format` varchar(50),
  `price` decimal(10,2) DEFAULT 0.00,
  `original_price` decimal(10,2),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_products_category_id` (`category_id`),
  CONSTRAINT `fk_products_category_id` FOREIGN KEY (`category_id`) REFERENCES `product_categories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 商品 SKU
CREATE TABLE IF NOT EXISTS `product_skus` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `sku_code` varchar(50),
  `specs` json NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `original_price` decimal(10,2),
  `stock` int NOT NULL,
  `image` varchar(255),
  `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_skus_sku_code` (`sku_code`),
  KEY `idx_product_skus_product_id` (`product_id`),
  CONSTRAINT `fk_product_skus_product_id` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品 SKU 表';

-- 商品属性
CREATE TABLE IF NOT EXISTS `product_attributes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `name` varchar(50) NOT NULL,
  `values_json` json NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product_attributes_product_id` (`product_id`),
  CONSTRAINT `fk_product_attributes_product_id` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品属性表';

-- 商品评价
CREATE TABLE IF NOT EXISTS `product_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `rating` int NOT NULL,
  `content` varchar(500),
  `images` json,
  `reply` varchar(500),
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_reviews_product_id` (`product_id`),
  KEY `idx_product_reviews_user_id` (`user_id`),
  KEY `idx_product_reviews_order_id` (`order_id`),
  CONSTRAINT `fk_product_reviews_product_id` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_product_reviews_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
  -- fk_product_reviews_order_id 在 V060（orders 表创建后）补建
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

SET FOREIGN_KEY_CHECKS = 1;
