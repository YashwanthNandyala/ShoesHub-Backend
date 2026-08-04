-- Migration script: create the `payments` table for the Shoeshub (e_commerce) database.
-- New table only. Existing tables (users, products, categories, cart_items, orders, order_items, jwt_token) are NOT modified.
-- Run once manually (or via Spring Boot with spring.sql.init.mode=always) BEFORE starting the application,
-- because the backend uses ddl-auto: validate and must find this table.

CREATE TABLE IF NOT EXISTS `payments` (
  `id` int NOT NULL AUTO_INCREMENT,
  `order_id` varchar(255) NOT NULL,
  `razorpay_order_id` varchar(255) NOT NULL,
  `razorpay_payment_id` varchar(255) DEFAULT NULL,
  `razorpay_signature` varchar(255) DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `currency` varchar(10) NOT NULL DEFAULT 'INR',
  `payment_status` enum('CREATED','PENDING','SUCCESS','FAILED','CANCELLED') NOT NULL DEFAULT 'CREATED',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payments_razorpay_order_id` (`razorpay_order_id`),
  UNIQUE KEY `uk_payments_razorpay_payment_id` (`razorpay_payment_id`),
  KEY `order_id_idx` (`order_id`),
  CONSTRAINT `fk_payments_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
