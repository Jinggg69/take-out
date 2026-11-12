CREATE DATABASE  IF NOT EXISTS `junzhou_take_out` ;
USE `junzhou_take_out`;

-- Initialization only. Existing databases use the additive scripts in migrations/.
DROP TABLE IF EXISTS `admin_session`;
CREATE TABLE `admin_session` (
  `session_id` varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `employee_id` bigint NOT NULL,
  `expires_at` bigint NOT NULL COMMENT 'JWT expiry in Unix epoch milliseconds',
  PRIMARY KEY (`session_id`),
  KEY `idx_admin_session_employee_expiry` (`employee_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS `address_book`;
CREATE TABLE `address_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `consignee` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `phone` varchar(11) COLLATE utf8_bin NOT NULL COMMENT '手机号',
  `province_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级区划编号',
  `province_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '省级名称',
  `city_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级区划编号',
  `city_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '市级名称',
  `district_code` varchar(12) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级区划编号',
  `district_name` varchar(32) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '区级名称',
  `detail` varchar(200) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '详细地址',
  `label` varchar(100) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '标签',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '默认 0 否 1是',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='地址簿';

DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `type` int DEFAULT NULL COMMENT '类型   1 菜品分类 2 套餐分类',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `status` int DEFAULT NULL COMMENT '分类状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_category_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品及套餐分类';

INSERT INTO `category` VALUES (11,1,'酒水饮料',10,1,'2022-06-09 22:09:18','2022-06-09 22:09:18',1,1);
INSERT INTO `category` VALUES (12,1,'传统主食',9,1,'2022-06-09 22:09:32','2022-06-09 22:18:53',1,1);
INSERT INTO `category` VALUES (13,2,'人气套餐',12,1,'2022-06-09 22:11:38','2022-06-10 11:04:40',1,1);
INSERT INTO `category` VALUES (15,2,'商务套餐',13,1,'2022-06-09 22:14:10','2022-06-10 11:04:48',1,1);
INSERT INTO `category` VALUES (16,1,'蜀味烤鱼',4,1,'2022-06-09 22:15:37','2022-08-31 14:27:25',1,1);
INSERT INTO `category` VALUES (17,1,'蜀味牛蛙',5,1,'2022-06-09 22:16:14','2022-08-31 14:39:44',1,1);
INSERT INTO `category` VALUES (18,1,'特色蒸菜',6,1,'2022-06-09 22:17:42','2022-06-09 22:17:42',1,1);
INSERT INTO `category` VALUES (19,1,'新鲜时蔬',7,1,'2022-06-09 22:18:12','2022-06-09 22:18:28',1,1);
INSERT INTO `category` VALUES (20,1,'水煮鱼',8,1,'2022-06-09 22:22:29','2022-06-09 22:23:45',1,1);
INSERT INTO `category` VALUES (21,1,'汤类',11,1,'2022-06-10 10:51:47','2022-06-10 10:51:47',1,1);

DROP TABLE IF EXISTS `dish`;
CREATE TABLE `dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '菜品名称',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品价格',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `status` int DEFAULT '1' COMMENT '0 停售 1 起售',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_dish_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品';

INSERT INTO `dish` VALUES (46,'王老吉',11,6.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/41bfcacf-7ad4-4927-8b26-df366553a94c.png','',1,'2022-06-09 22:40:47','2022-06-09 22:40:47',1,1);
INSERT INTO `dish` VALUES (47,'北冰洋',11,4.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4451d4be-89a2-4939-9c69-3a87151cb979.png','还是小时候的味道',1,'2022-06-10 09:18:49','2022-06-10 09:18:49',1,1);
INSERT INTO `dish` VALUES (48,'雪花啤酒',11,4.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/bf8cbfc1-04d2-40e8-9826-061ee41ab87c.png','',1,'2022-06-10 09:22:54','2022-06-10 09:22:54',1,1);
INSERT INTO `dish` VALUES (49,'米饭',12,2.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/76752350-2121-44d2-b477-10791c23a8ec.png','精选五常大米',1,'2022-06-10 09:30:17','2022-06-10 09:30:17',1,1);
INSERT INTO `dish` VALUES (50,'馒头',12,1.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/475cc599-8661-4899-8f9e-121dd8ef7d02.png','优质面粉',1,'2022-06-10 09:34:28','2022-06-10 09:34:28',1,1);
INSERT INTO `dish` VALUES (51,'老坛酸菜鱼',20,56.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4a9cefba-6a74-467e-9fde-6e687ea725d7.png','原料：汤，草鱼，酸菜',1,'2022-06-10 09:40:51','2022-06-10 09:40:51',1,1);
INSERT INTO `dish` VALUES (52,'经典酸菜鮰鱼',20,66.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/5260ff39-986c-4a97-8850-2ec8c7583efc.png','原料：酸菜，江团，鮰鱼',1,'2022-06-10 09:46:02','2022-06-10 09:46:02',1,1);
INSERT INTO `dish` VALUES (53,'蜀味水煮草鱼',20,38.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a6953d5a-4c18-4b30-9319-4926ee77261f.png','原料：草鱼，汤',1,'2022-06-10 09:48:37','2022-06-10 09:48:37',1,1);
INSERT INTO `dish` VALUES (54,'清炒小油菜',19,18.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/3613d38e-5614-41c2-90ed-ff175bf50716.png','原料：小油菜',1,'2022-06-10 09:51:46','2022-06-10 09:51:46',1,1);
INSERT INTO `dish` VALUES (55,'蒜蓉娃娃菜',19,18.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/4879ed66-3860-4b28-ba14-306ac025fdec.png','原料：蒜，娃娃菜',1,'2022-06-10 09:53:37','2022-06-10 09:53:37',1,1);
INSERT INTO `dish` VALUES (56,'清炒西兰花',19,18.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/e9ec4ba4-4b22-4fc8-9be0-4946e6aeb937.png','原料：西兰花',1,'2022-06-10 09:55:44','2022-06-10 09:55:44',1,1);
INSERT INTO `dish` VALUES (57,'炝炒圆白菜',19,18.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/22f59feb-0d44-430e-a6cd-6a49f27453ca.png','原料：圆白菜',1,'2022-06-10 09:58:35','2022-06-10 09:58:35',1,1);
INSERT INTO `dish` VALUES (58,'清蒸鲈鱼',18,98.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c18b5c67-3b71-466c-a75a-e63c6449f21c.png','原料：鲈鱼',1,'2022-06-10 10:12:28','2022-06-10 10:12:28',1,1);
INSERT INTO `dish` VALUES (59,'东坡肘子',18,138.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a80a4b8c-c93e-4f43-ac8a-856b0d5cc451.png','原料：猪肘棒',1,'2022-06-10 10:24:03','2022-06-10 10:24:03',1,1);
INSERT INTO `dish` VALUES (60,'梅菜扣肉',18,58.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/6080b118-e30a-4577-aab4-45042e3f88be.png','原料：猪肉，梅菜',1,'2022-06-10 10:26:03','2022-06-10 10:26:03',1,1);
INSERT INTO `dish` VALUES (61,'剁椒鱼头',18,66.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/13da832f-ef2c-484d-8370-5934a1045a06.png','原料：鲢鱼，剁椒',1,'2022-06-10 10:28:54','2022-06-10 10:28:54',1,1);
INSERT INTO `dish` VALUES (62,'金汤酸菜牛蛙',17,88.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7694a5d8-7938-4e9d-8b9e-2075983a2e38.png','原料：鲜活牛蛙，酸菜',1,'2022-06-10 10:33:05','2022-06-10 10:33:05',1,1);
INSERT INTO `dish` VALUES (63,'香锅牛蛙',17,88.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/f5ac8455-4793-450c-97ba-173795c34626.png','配料：鲜活牛蛙，莲藕，青笋',1,'2022-06-10 10:35:40','2022-06-10 10:35:40',1,1);
INSERT INTO `dish` VALUES (64,'馋嘴牛蛙',17,88.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/7a55b845-1f2b-41fa-9486-76d187ee9ee1.png','配料：鲜活牛蛙，丝瓜，黄豆芽',1,'2022-06-10 10:37:52','2022-06-10 10:37:52',1,1);
INSERT INTO `dish` VALUES (65,'草鱼2斤',16,68.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/b544d3ba-a1ae-4d20-a860-81cb5dec9e03.png','原料：草鱼，黄豆芽，莲藕',1,'2022-06-10 10:41:08','2022-06-10 10:41:08',1,1);
INSERT INTO `dish` VALUES (66,'江团鱼2斤',16,119.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/a101a1e9-8f8b-47b2-afa4-1abd47ea0a87.png','配料：江团鱼，黄豆芽，莲藕',1,'2022-06-10 10:42:42','2022-06-10 10:42:42',1,1);
INSERT INTO `dish` VALUES (67,'鮰鱼2斤',16,72.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/8cfcc576-4b66-4a09-ac68-ad5b273c2590.png','原料：鮰鱼，黄豆芽，莲藕',1,'2022-06-10 10:43:56','2022-06-10 10:43:56',1,1);
INSERT INTO `dish` VALUES (68,'鸡蛋汤',21,4.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/c09a0ee8-9d19-428d-81b9-746221824113.png','配料：鸡蛋，紫菜',1,'2022-06-10 10:54:25','2022-06-10 10:54:25',1,1);
INSERT INTO `dish` VALUES (69,'平菇豆腐汤',21,6.00,'https://sky-itcast.oss-cn-beijing.aliyuncs.com/16d0a3d6-2253-4cfc-9b49-bf7bd9eb2ad2.png','配料：豆腐，平菇',1,'2022-06-10 10:55:02','2022-06-10 10:55:02',1,1);

DROP TABLE IF EXISTS `dish_flavor`;
CREATE TABLE `dish_flavor` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` bigint NOT NULL COMMENT '菜品',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '口味名称',
  `value` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '口味数据list',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='菜品口味关系表';

INSERT INTO `dish_flavor` VALUES (40,10,'甜味','[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (41,7,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (42,7,'温度','[\"热饮\",\"常温\",\"去冰\",\"少冰\",\"多冰\"]');
INSERT INTO `dish_flavor` VALUES (45,6,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (46,6,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (47,5,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (48,5,'甜味','[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (49,2,'甜味','[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (50,4,'甜味','[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (51,3,'甜味','[\"无糖\",\"少糖\",\"半糖\",\"多糖\",\"全糖\"]');
INSERT INTO `dish_flavor` VALUES (52,3,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (86,52,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (87,52,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (88,51,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (89,51,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (92,53,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (93,53,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (94,54,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\"]');
INSERT INTO `dish_flavor` VALUES (95,56,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (96,57,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (97,60,'忌口','[\"不要葱\",\"不要蒜\",\"不要香菜\",\"不要辣\"]');
INSERT INTO `dish_flavor` VALUES (101,66,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (102,67,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');
INSERT INTO `dish_flavor` VALUES (103,65,'辣度','[\"不辣\",\"微辣\",\"中辣\",\"重辣\"]');

DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '姓名',
  `username` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '用户名',
  `password` varchar(255) COLLATE utf8_bin NOT NULL COMMENT '密码',
  `phone` varchar(11) COLLATE utf8_bin NOT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8_bin NOT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8_bin NOT NULL COMMENT '身份证号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态 0:禁用，1:启用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='员工信息';

INSERT INTO `employee` VALUES (1,'管理员','admin','{bcrypt}$2a$12$TtEZixOt6RQex3alq7Ze8OpV5tzQLwNFx1GW7jsd3vpmzlWIj3h9C','13812312312','1','110101199001010047',1,'2022-02-15 15:51:20','2022-02-17 09:16:20',10,1);

DROP TABLE IF EXISTS `order_detail`;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '名字',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单明细表';

DROP TABLE IF EXISTS `mock_payment`;
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `payment_channel` varchar(16) NOT NULL DEFAULT 'WECHAT',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `number` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '订单号',
  `status` int NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `address_book_id` bigint NOT NULL COMMENT '地址id',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `checkout_time` datetime DEFAULT NULL COMMENT '结账时间',
  `pay_method` int NOT NULL DEFAULT '1' COMMENT '支付方式 1微信,2支付宝',
  `pay_status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
  `amount` decimal(10,2) NOT NULL COMMENT '实收金额',
  `remark` varchar(100) COLLATE utf8_bin DEFAULT NULL COMMENT '备注',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `address` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '地址',
  `user_name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '用户名称',
  `consignee` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '收货人',
  `cancel_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单取消原因',
  `rejection_reason` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '订单拒绝原因',
  `cancel_time` datetime DEFAULT NULL COMMENT '订单取消时间',
  `estimated_delivery_time` datetime DEFAULT NULL COMMENT '预计送达时间',
  `delivery_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '配送状态  1立即送出  0选择具体时间',
  `delivery_time` datetime DEFAULT NULL COMMENT '送达时间',
  `pack_amount` int DEFAULT NULL COMMENT '打包费',
  `tableware_number` int DEFAULT NULL COMMENT '餐具数量',
  `tableware_status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '餐具数量状态  1按餐量提供  0选择具体数量',
  `dispatch_time` datetime DEFAULT NULL COMMENT '开始配送时间',
  `wechat_transaction_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `refund_status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `payment_check_time` datetime DEFAULT NULL,
  `wechat_trade_state` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `client_request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `submit_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_number` (`number`),
  UNIQUE KEY `uk_orders_wechat_transaction` (`wechat_transaction_id`),
  UNIQUE KEY `uk_orders_user_request` (`user_id`, `client_request_id`),
  KEY `idx_orders_payment_check` (`status`, `pay_status`, `payment_check_time`),
  KEY `idx_orders_status_order_time` (`status`, `order_time`),
  KEY `idx_orders_status_dispatch_time` (`status`, `dispatch_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='订单表';

DROP TABLE IF EXISTS `refund_request`;
CREATE TABLE `refund_request` (
  `out_refund_no` varchar(64) NOT NULL,
  `order_id` bigint NOT NULL,
  `out_trade_no` varchar(50) NOT NULL,
  `transaction_id` varchar(64) DEFAULT NULL,
  `amount_fen` bigint NOT NULL,
  `status` varchar(16) NOT NULL,
  `query_only` tinyint NOT NULL DEFAULT '0',
  `provider_refund_id` varchar(64) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime NOT NULL,
  `lease_until` datetime DEFAULT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `last_error` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`out_refund_no`),
  UNIQUE KEY `uk_refund_request_order` (`order_id`),
  UNIQUE KEY `uk_refund_request_provider` (`provider_refund_id`),
  KEY `idx_refund_request_due` (`status`, `next_attempt_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

DROP TABLE IF EXISTS `setmeal`;
CREATE TABLE `setmeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `category_id` bigint NOT NULL COMMENT '菜品分类id',
  `name` varchar(32) COLLATE utf8_bin NOT NULL COMMENT '套餐名称',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `status` int DEFAULT '1' COMMENT '售卖状态 0:停售 1:起售',
  `description` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '描述信息',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `update_user` bigint DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_setmeal_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐';

DROP TABLE IF EXISTS `setmeal_dish`;
CREATE TABLE `setmeal_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '菜品名称 （冗余字段）',
  `price` decimal(10,2) DEFAULT NULL COMMENT '菜品单价（冗余字段）',
  `copies` int DEFAULT NULL COMMENT '菜品份数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='套餐菜品关系';

DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '商品名称',
  `image` varchar(255) COLLATE utf8_bin DEFAULT NULL COMMENT '图片',
  `user_id` bigint NOT NULL COMMENT '主键',
  `dish_id` bigint DEFAULT NULL COMMENT '菜品id',
  `setmeal_id` bigint DEFAULT NULL COMMENT '套餐id',
  `dish_flavor` varchar(50) COLLATE utf8_bin DEFAULT NULL COMMENT '口味',
  `number` int NOT NULL DEFAULT '1' COMMENT '数量',
  `amount` decimal(10,2) NOT NULL COMMENT '金额',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_shopping_cart_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='购物车';

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` varchar(45) COLLATE utf8_bin DEFAULT NULL COMMENT '微信用户唯一标识',
  `name` varchar(32) COLLATE utf8_bin DEFAULT NULL COMMENT '姓名',
  `phone` varchar(11) COLLATE utf8_bin DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8_bin DEFAULT NULL COMMENT '性别',
  `id_number` varchar(18) COLLATE utf8_bin DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(500) COLLATE utf8_bin DEFAULT NULL COMMENT '头像',
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COLLATE=utf8_bin COMMENT='用户信息';

-- Stage 2 single-store foundation; keep in sync with the incremental migration.
-- Single-store foundation only. Stop all writers before migration.
-- MySQL DDL commits implicitly; repair a failure before rerunning.
-- The default identity preserves legacy writes until isolation is implemented.
DELIMITER $$
DROP PROCEDURE IF EXISTS `_junzhou_20261029_tenant_store`$$
CREATE PROCEDURE `_junzhou_20261029_tenant_store`()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart')
      AND table_type='BASE TABLE' AND engine='InnoDB') <> 6 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required business tables must be InnoDB base tables';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store') AND (table_type <> 'BASE TABLE' OR engine <> 'InnoDB')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing tenant/store table is incompatible';
  END IF;
  CREATE TABLE IF NOT EXISTS tenant (
    id bigint NOT NULL AUTO_INCREMENT,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    status tinyint NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_tenant_code (code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS store (
    id bigint NOT NULL AUTO_INCREMENT,
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    status tinyint NOT NULL DEFAULT 1,
    PRIMARY KEY (id), UNIQUE KEY uk_store_tenant_code (tenant_id,code),
    CONSTRAINT fk_store_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  -- CREATE IF NOT EXISTS must not silently accept an unrelated existing table.
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store') AND (
        (column_name='id' AND column_type='bigint' AND is_nullable='NO' AND extra='auto_increment') OR
        (table_name='store' AND column_name='tenant_id' AND column_type='bigint' AND is_nullable='NO') OR
        (column_name='code' AND column_type='varchar(64)' AND is_nullable='NO' AND collation_name='ascii_bin') OR
        (column_name='name' AND column_type='varchar(100)' AND is_nullable='NO') OR
        (column_name='status' AND column_type='tinyint' AND is_nullable='NO' AND column_default='1')
      )) <> 9
     OR (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('tenant','store')) <> 9
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='tenant' AND index_name='PRIMARY' AND non_unique=0) <=> 'id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='store' AND index_name='PRIMARY' AND non_unique=0) <=> 'id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='tenant' AND index_name='uk_tenant_code' AND non_unique=0 AND sub_part IS NULL) <=> 'code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
         WHERE table_schema=DATABASE() AND table_name='store' AND index_name='uk_store_tenant_code' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE()
                    AND table_name='store' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing tenant/store definition is incompatible';
  END IF;
  IF (SELECT COUNT(*) FROM tenant WHERE id=1 AND code='legacy-single-store') <> 1
     AND (SELECT COUNT(*) FROM tenant) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default tenant identity conflicts; inspect manually';
  END IF;
  INSERT INTO tenant (id,code,name,status)
    SELECT 1,'legacy-single-store','默认商户',1 WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE id=1);
  IF (SELECT COUNT(*) FROM store WHERE id=1 AND tenant_id=1 AND code='legacy-single-store') <> 1
     AND (SELECT COUNT(*) FROM store) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default store identity conflicts; inspect manually';
  END IF;
  INSERT INTO store (id,tenant_id,code,name,status)
    SELECT 1,1,'legacy-single-store','默认门店',1 WHERE NOT EXISTS (SELECT 1 FROM store WHERE id=1);
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') AND column_name='tenant_id')
        OR (table_name IN ('orders','shopping_cart') AND column_name='store_id'))
      AND (column_type <> 'bigint' OR is_nullable <> 'NO' OR NOT (column_default <=> '1'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing ownership column is incompatible';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='employee' AND column_name='tenant_id') THEN
    ALTER TABLE employee ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='category' AND column_name='tenant_id') THEN
    ALTER TABLE category ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='dish' AND column_name='tenant_id') THEN
    ALTER TABLE dish ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='setmeal' AND column_name='tenant_id') THEN
    ALTER TABLE setmeal ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='tenant_id') THEN
    ALTER TABLE orders ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='store_id') THEN
    ALTER TABLE orders ADD COLUMN store_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shopping_cart' AND column_name='tenant_id') THEN
    ALTER TABLE shopping_cart ADD COLUMN tenant_id bigint NOT NULL DEFAULT 1;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shopping_cart' AND column_name='store_id') THEN
    ALTER TABLE shopping_cart ADD COLUMN store_id bigint NOT NULL DEFAULT 1;
  END IF;
END$$
CALL `_junzhou_20261029_tenant_store`()$$
DROP PROCEDURE `_junzhou_20261029_tenant_store`$$
DELIMITER ;

-- Stage 2.5: public catalog file ownership
-- Requires 20261029_01_tenant_store.sql. No historical URL is implicitly assigned.
CREATE TABLE IF NOT EXISTS file_asset (
  id varchar(36) NOT NULL PRIMARY KEY,
  tenant_id bigint NOT NULL,
  uploader_employee_id bigint NOT NULL,
  purpose varchar(32) NOT NULL,
  object_key varchar(255) NOT NULL,
  public_url varchar(255) NOT NULL,
  created_at datetime(6) NOT NULL,
  CONSTRAINT uk_file_asset_object UNIQUE (object_key),
  CONSTRAINT uk_file_asset_url UNIQUE (public_url),
  CONSTRAINT fk_file_asset_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT ck_file_asset_purpose CHECK (purpose = 'PUBLIC_CATALOG')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Stage 2.7: explicit ownership for all new business writes
-- Stage 2.7: stop all writers and back up first. Requires stage 2.1 schema.
-- All preflight checks run before the first ALTER. MySQL DDL commits implicitly;
-- after a partial failure inspect the error and rerun with writers still stopped.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_explicit_ownership$$
CREATE PROCEDURE _junzhou_explicit_ownership()
BEGIN
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee','category','dish','setmeal','orders','shopping_cart','tenant','store')
      AND table_type='BASE TABLE' AND engine='InnoDB') <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Required ownership tables must be InnoDB base tables';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND ((table_name IN ('employee','category','dish','setmeal','orders','shopping_cart') AND column_name='tenant_id')
        OR (table_name IN ('orders','shopping_cart') AND column_name='store_id'))
      AND column_type='bigint' AND is_nullable='NO' AND extra=''
      AND (column_default IS NULL OR column_default='1')) <> 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected eight NOT NULL bigint ownership columns with default 1 or no default';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM tenant WHERE id=1)
     OR NOT EXISTS(SELECT 1 FROM store WHERE id=1 AND tenant_id=1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical default tenant/store must be retained';
  END IF;
  IF EXISTS(SELECT 1 FROM employee e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM category e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM dish e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM setmeal e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM orders e LEFT JOIN store s ON s.id=e.store_id AND s.tenant_id=e.tenant_id
          LEFT JOIN tenant t ON t.id=e.tenant_id WHERE s.id IS NULL OR t.id IS NULL)
     OR EXISTS(SELECT 1 FROM shopping_cart e LEFT JOIN store s ON s.id=e.store_id AND s.tenant_id=e.tenant_id
          LEFT JOIN tenant t ON t.id=e.tenant_id WHERE s.id IS NULL OR t.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid historical ownership; inspect without rewriting business data';
  END IF;
  ALTER TABLE employee ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE category ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE dish ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE setmeal ALTER COLUMN tenant_id DROP DEFAULT;
  ALTER TABLE orders ALTER COLUMN tenant_id DROP DEFAULT, ALTER COLUMN store_id DROP DEFAULT;
  ALTER TABLE shopping_cart ALTER COLUMN tenant_id DROP DEFAULT, ALTER COLUMN store_id DROP DEFAULT;
END$$
CALL _junzhou_explicit_ownership()$$
DROP PROCEDURE _junzhou_explicit_ownership$$
DELIMITER ;

-- Stage 3.1. Stop writers and back up first; MySQL DDL is not atomic.
-- No inferred employee grants. Existing tenant templates are never reseeded.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_rbac$$
CREATE PROCEDURE _junzhou_rbac()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('tenant','store','employee') AND engine='InnoDB' AND table_type='BASE TABLE') <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Requires tenant/store/employee InnoDB tables';
  END IF;
  IF EXISTS(SELECT 1 FROM employee e LEFT JOIN tenant t ON t.id=e.tenant_id WHERE t.id IS NULL)
     OR EXISTS(SELECT 1 FROM store s LEFT JOIN tenant t ON t.id=s.tenant_id WHERE t.id IS NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid historical tenant ownership';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='employee' AND index_name='uk_employee_tenant_id') THEN
    ALTER TABLE employee ADD UNIQUE KEY uk_employee_tenant_id(tenant_id,id);
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='store' AND index_name='uk_store_tenant_id') THEN
    ALTER TABLE store ADD UNIQUE KEY uk_store_tenant_id(tenant_id,id);
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='employee' AND index_name='uk_employee_tenant_id' AND non_unique=0) <=> 'tenant_id,id')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='store' AND index_name='uk_store_tenant_id' AND non_unique=0) <=> 'tenant_id,id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible tenant parent indexes';
  END IF;
  CREATE TABLE IF NOT EXISTS rbac_role (
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    PRIMARY KEY(tenant_id,code),
    CONSTRAINT fk_role_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS rbac_permission (
    tenant_id bigint NOT NULL,
    code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name varchar(100) NOT NULL,
    PRIMARY KEY(tenant_id,code),
    CONSTRAINT fk_permission_tenant FOREIGN KEY(tenant_id) REFERENCES tenant(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS employee_role (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,role_code),
    CONSTRAINT fk_er_employee FOREIGN KEY(tenant_id,employee_id) REFERENCES employee(tenant_id,id),
    CONSTRAINT fk_er_role FOREIGN KEY(tenant_id,role_code) REFERENCES rbac_role(tenant_id,code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS role_permission (
    tenant_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    permission_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(tenant_id,role_code,permission_code),
    CONSTRAINT fk_rp_role FOREIGN KEY(tenant_id,role_code) REFERENCES rbac_role(tenant_id,code),
    CONSTRAINT fk_rp_permission FOREIGN KEY(tenant_id,permission_code) REFERENCES rbac_permission(tenant_id,code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  CREATE TABLE IF NOT EXISTS employee_store (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    store_id bigint NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,store_id),
    CONSTRAINT fk_es_employee FOREIGN KEY(tenant_id,employee_id) REFERENCES employee(tenant_id,id),
    CONSTRAINT fk_es_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND is_nullable='NO' AND column_default IS NULL AND ((table_name='rbac_role' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='rbac_role' AND column_name='code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='rbac_role' AND column_name='name' AND column_type='varchar(100)') OR
        (table_name='rbac_permission' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='rbac_permission' AND column_name='code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='rbac_permission' AND column_name='name' AND column_type='varchar(100)') OR
        (table_name='employee_role' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='employee_role' AND column_name='employee_id' AND column_type='bigint') OR
        (table_name='employee_role' AND column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='role_permission' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='role_permission' AND column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='role_permission' AND column_name='permission_code' AND column_type='varchar(64)' AND collation_name='ascii_bin') OR
        (table_name='employee_store' AND column_name='tenant_id' AND column_type='bigint') OR
        (table_name='employee_store' AND column_name='employee_id' AND column_type='bigint') OR
        (table_name='employee_store' AND column_name='store_id' AND column_type='bigint'))) <> 15 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC columns';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND referenced_table_schema=DATABASE() AND ((table_name='rbac_role' AND constraint_name='fk_role_tenant' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') OR
      (table_name='rbac_permission' AND constraint_name='fk_permission_tenant' AND column_name='tenant_id' AND referenced_table_name='tenant' AND referenced_column_name='id') OR
      (table_name='employee_role' AND constraint_name='fk_er_employee' AND column_name='tenant_id' AND referenced_table_name='employee' AND referenced_column_name='tenant_id') OR
      (table_name='employee_role' AND constraint_name='fk_er_employee' AND column_name='employee_id' AND referenced_table_name='employee' AND referenced_column_name='id') OR
      (table_name='employee_role' AND constraint_name='fk_er_role' AND column_name='tenant_id' AND referenced_table_name='rbac_role' AND referenced_column_name='tenant_id') OR
      (table_name='employee_role' AND constraint_name='fk_er_role' AND column_name='role_code' AND referenced_table_name='rbac_role' AND referenced_column_name='code') OR
      (table_name='role_permission' AND constraint_name='fk_rp_role' AND column_name='tenant_id' AND referenced_table_name='rbac_role' AND referenced_column_name='tenant_id') OR
      (table_name='role_permission' AND constraint_name='fk_rp_role' AND column_name='role_code' AND referenced_table_name='rbac_role' AND referenced_column_name='code') OR
      (table_name='role_permission' AND constraint_name='fk_rp_permission' AND column_name='tenant_id' AND referenced_table_name='rbac_permission' AND referenced_column_name='tenant_id') OR
      (table_name='role_permission' AND constraint_name='fk_rp_permission' AND column_name='permission_code' AND referenced_table_name='rbac_permission' AND referenced_column_name='code') OR
      (table_name='employee_store' AND constraint_name='fk_es_employee' AND column_name='tenant_id' AND referenced_table_name='employee' AND referenced_column_name='tenant_id') OR
      (table_name='employee_store' AND constraint_name='fk_es_employee' AND column_name='employee_id' AND referenced_table_name='employee' AND referenced_column_name='id') OR
      (table_name='employee_store' AND constraint_name='fk_es_store' AND column_name='tenant_id' AND referenced_table_name='store' AND referenced_column_name='tenant_id') OR
      (table_name='employee_store' AND constraint_name='fk_es_store' AND column_name='store_id' AND referenced_table_name='store' AND referenced_column_name='id'))) <> 14 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC tenant foreign keys';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rbac_role' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='rbac_permission' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='employee_role' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,employee_id,role_code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='role_permission' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,role_code,permission_code')
     OR NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='employee_store' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL) <=> 'tenant_id,employee_id,store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible RBAC primary keys';
  END IF;
  -- Only provision tenants with no roles, inside one DML transaction.
  DROP TEMPORARY TABLE IF EXISTS _rbac_new_tenants;
  CREATE TEMPORARY TABLE _rbac_new_tenants(tenant_id bigint PRIMARY KEY);
  START TRANSACTION;
  INSERT INTO _rbac_new_tenants SELECT id FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_role r WHERE r.tenant_id=t.id);
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'MERCHANT_ADMIN','商户管理员' FROM _rbac_new_tenants;
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'MANAGER','店长' FROM _rbac_new_tenants;
  INSERT INTO rbac_role(tenant_id,code,name) SELECT tenant_id,'CLERK','店员' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'dish:read','查看菜品' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'dish:update','修改菜品' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:read','查看订单' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:accept','接单' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'order:refund','退款' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'report:read','查看报表' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'report:export','导出报表' FROM _rbac_new_tenants;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT tenant_id,'employee:manage','管理员工授权' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','dish:update' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:accept' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','order:refund' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','report:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','report:export' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MERCHANT_ADMIN','employee:manage' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','dish:update' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','order:accept' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'MANAGER','report:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','dish:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','order:read' FROM _rbac_new_tenants;
  INSERT INTO role_permission SELECT tenant_id,'CLERK','order:accept' FROM _rbac_new_tenants;
  COMMIT;
  DROP TEMPORARY TABLE _rbac_new_tenants;
END$$
CALL _junzhou_rbac()$$
DROP PROCEDURE _junzhou_rbac$$
DELIMITER ;

-- Stage 3.2. Requires stage 3.1. Stop writers and back up before migration.
-- DDL auto-commits. Scope defaults narrow existing assignments, never infer broader grants.
DELIMITER $$
DROP PROCEDURE IF EXISTS _junzhou_rbac_scope$$
CREATE PROCEDURE _junzhou_rbac_scope()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF DATABASE() IS NULL OR DATABASE() IN ('mysql','sys','information_schema','performance_schema') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the application database';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name IN ('employee_role','rbac_permission','role_permission','rbac_role','employee_store')
      AND engine='InnoDB' AND table_type='BASE TABLE') <> 5 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Requires stage 3.1 RBAC tables';
  END IF;
  IF EXISTS(SELECT 1 FROM rbac_permission WHERE NOT REGEXP_LIKE(code,'^[a-z]+:[a-z]+$','c')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid existing permission action; inspect manually';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role' AND column_name='data_scope') THEN
    ALTER TABLE employee_role ADD COLUMN data_scope varchar(32) CHARACTER SET ascii COLLATE ascii_bin
      NOT NULL DEFAULT 'CURRENT_STORE';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role' AND column_name='data_scope' AND column_type='varchar(32)'
      AND collation_name='ascii_bin' AND is_nullable='NO' AND column_default='CURRENT_STORE')
     OR EXISTS(SELECT 1 FROM employee_role WHERE NOT REGEXP_LIKE(data_scope,'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$','c')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible data scope column or values';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='employee_role' AND constraint_name='ck_employee_role_scope') THEN
    ALTER TABLE employee_role ADD CONSTRAINT ck_employee_role_scope
      CHECK(REGEXP_LIKE(data_scope,'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$','c'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE()
      AND table_name='rbac_permission' AND constraint_name='ck_permission_action') THEN
    ALTER TABLE rbac_permission ADD CONSTRAINT ck_permission_action
      CHECK(REGEXP_LIKE(code,'^[a-z]+:[a-z]+$','c'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints t
      JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
      WHERE t.constraint_schema=DATABASE() AND t.table_name='employee_role' AND t.constraint_name='ck_employee_role_scope'
      AND t.constraint_type='CHECK' AND t.enforced='YES'
      AND BINARY REPLACE(c.check_clause,CHAR(92),'') = BINARY CONCAT('regexp_like(`data_scope`,_utf8mb4',CHAR(39),'^(CURRENT_STORE|SPECIFIED_STORES|TENANT_STORES)$',CHAR(39),',_utf8mb4',CHAR(39),'c',CHAR(39),')')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible ck_employee_role_scope constraint';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints t
      JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name
      WHERE t.constraint_schema=DATABASE() AND t.table_name='rbac_permission' AND t.constraint_name='ck_permission_action'
      AND t.constraint_type='CHECK' AND t.enforced='YES'
      AND BINARY REPLACE(c.check_clause,CHAR(92),'') = BINARY CONCAT('regexp_like(`code`,_utf8mb4',CHAR(39),'^[a-z]+:[a-z]+$',CHAR(39),',_utf8mb4',CHAR(39),'c',CHAR(39),')')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible ck_permission_action constraint';
  END IF;
  CREATE TABLE IF NOT EXISTS employee_role_store (
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    role_code varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    store_id bigint NOT NULL,
    PRIMARY KEY(tenant_id,employee_id,role_code,store_id),
    CONSTRAINT fk_ers_role FOREIGN KEY(tenant_id,employee_id,role_code)
      REFERENCES employee_role(tenant_id,employee_id,role_code),
    CONSTRAINT fk_ers_store FOREIGN KEY(tenant_id,employee_id,store_id)
      REFERENCES employee_store(tenant_id,employee_id,store_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='employee_role_store' AND is_nullable='NO' AND column_default IS NULL
      AND ((column_name IN ('tenant_id','employee_id','store_id') AND column_type='bigint')
        OR (column_name='role_code' AND column_type='varchar(64)' AND collation_name='ascii_bin'))) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store columns';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='employee_role_store' AND index_name='PRIMARY' AND non_unique=0 AND sub_part IS NULL)
      <=> 'tenant_id,employee_id,role_code,store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible employee role store primary key';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(CONCAT(column_name,':',referenced_column_name) ORDER BY ordinal_position)
      FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='employee_role_store'
      AND constraint_name='fk_ers_role' AND referenced_table_schema=DATABASE() AND referenced_table_name='employee_role')
      <=> 'tenant_id:tenant_id,employee_id:employee_id,role_code:role_code') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store tenant foreign keys';
  END IF;
  IF NOT ((SELECT GROUP_CONCAT(CONCAT(column_name,':',referenced_column_name) ORDER BY ordinal_position)
      FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='employee_role_store'
      AND constraint_name='fk_ers_store' AND referenced_table_schema=DATABASE() AND referenced_table_name='employee_store')
      <=> 'tenant_id:tenant_id,employee_id:employee_id,store_id:store_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incompatible role store tenant foreign keys';
  END IF;
  -- Catalogue only: adding an action never grants it to existing roles.
  START TRANSACTION;
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:read','dish:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:create','dish:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:update','dish:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:delete','dish:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'dish:status','dish:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='dish:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:read','category:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:create','category:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:update','category:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'category:delete','category:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='category:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:read','setmeal:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:create','setmeal:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:update','setmeal:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:delete','setmeal:delete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:delete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'setmeal:status','setmeal:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='setmeal:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:read','order:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:accept','order:accept' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:accept');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:reject','order:reject' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:reject');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:cancel','order:cancel' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:cancel');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:deliver','order:deliver' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:deliver');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:complete','order:complete' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:complete');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'order:refund','order:refund' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:refund');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'report:read','report:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='report:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'report:export','report:export' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='report:export');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:read','employee:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:create','employee:create' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:create');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:update','employee:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:status','employee:status' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:status');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'employee:manage','employee:manage' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='employee:manage');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'shop:read','shop:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='shop:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'shop:update','shop:update' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='shop:update');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'workspace:read','workspace:read' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='workspace:read');
  INSERT INTO rbac_permission(tenant_id,code,name) SELECT t.id,'file:upload','file:upload' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='file:upload');
  COMMIT;
END$$
CALL _junzhou_rbac_scope()$$
DROP PROCEDURE _junzhou_rbac_scope$$
DELIMITER ;

-- Stage 4.1. Run after stage 3 migrations with catalog writes paused.
DROP PROCEDURE IF EXISTS _junzhou_store_menu;
DELIMITER $$
CREATE PROCEDURE _junzhou_store_menu()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='dish' AND index_name='uk_dish_tenant_id') THEN
    ALTER TABLE dish ADD UNIQUE KEY uk_dish_tenant_id (tenant_id,id);
  END IF;

  CREATE TABLE IF NOT EXISTS store_dish (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    on_sale TINYINT NOT NULL DEFAULT 0,
    sold_out TINYINT NOT NULL DEFAULT 0,
    override_price DECIMAL(10,2) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,store_id,dish_id),
    CONSTRAINT fk_store_dish_store FOREIGN KEY (tenant_id,store_id)
      REFERENCES store (tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_store_dish_product FOREIGN KEY (tenant_id,dish_id)
      REFERENCES dish (tenant_id,id) ON DELETE CASCADE,
    CHECK (on_sale IN (0,1)),
    CHECK (sold_out IN (0,1)),
    CHECK (override_price IS NULL OR override_price >= 0)
  ) ENGINE=InnoDB;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema=DATABASE() AND table_name='setmeal' AND index_name='uk_setmeal_tenant_id') THEN
    ALTER TABLE setmeal ADD UNIQUE KEY uk_setmeal_tenant_id (tenant_id,id);
  END IF;

  CREATE TABLE IF NOT EXISTS store_setmeal (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    setmeal_id BIGINT NOT NULL,
    on_sale TINYINT NOT NULL DEFAULT 0,
    sold_out TINYINT NOT NULL DEFAULT 0,
    override_price DECIMAL(10,2) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id,store_id,setmeal_id),
    CONSTRAINT fk_store_setmeal_store FOREIGN KEY (tenant_id,store_id)
      REFERENCES store (tenant_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_store_setmeal_product FOREIGN KEY (tenant_id,setmeal_id)
      REFERENCES setmeal (tenant_id,id) ON DELETE CASCADE,
    CHECK (on_sale IN (0,1)),
    CHECK (sold_out IN (0,1)),
    CHECK (override_price IS NULL OR override_price >= 0)
  ) ENGINE=InnoDB;

  START TRANSACTION;
  INSERT INTO store_dish (tenant_id,store_id,dish_id,on_sale)
    SELECT p.tenant_id,st.id,p.id,1 FROM dish p
    JOIN store st ON st.id=1 AND st.tenant_id=p.tenant_id
    WHERE p.tenant_id=1 AND NOT EXISTS (
      SELECT 1 FROM store_dish m
      WHERE m.tenant_id=p.tenant_id AND m.store_id=st.id AND m.dish_id=p.id);
  INSERT INTO store_setmeal (tenant_id,store_id,setmeal_id,on_sale)
    SELECT p.tenant_id,st.id,p.id,1 FROM setmeal p
    JOIN store st ON st.id=1 AND st.tenant_id=p.tenant_id
    WHERE p.tenant_id=1 AND NOT EXISTS (
      SELECT 1 FROM store_setmeal m
      WHERE m.tenant_id=p.tenant_id AND m.store_id=st.id AND m.setmeal_id=p.id);
  COMMIT;
END$$
CALL _junzhou_store_menu()$$
DROP PROCEDURE _junzhou_store_menu$$
DELIMITER ;

-- Stage 4.3. Pause cart writes during the upgrade; review duplicate groups before merging.
DROP PROCEDURE IF EXISTS _junzhou_cart_identity;
DELIMITER $$
CREATE PROCEDURE _junzhou_cart_identity()
main: BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS cart_identity_review;
    RESIGNAL;
  END;
  IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
             AND table_name='shopping_cart' AND index_name='uk_cart_identity' AND non_unique=0
             GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)=
                 'tenant_id,store_id,user_id,product_type,product_id,flavor_key') THEN
    LEAVE main;
  END IF;
  IF EXISTS (SELECT 1 FROM shopping_cart WHERE (dish_id IS NULL) = (setmeal_id IS NULL)
             OR COALESCE(dish_id,setmeal_id) <= 0 OR number <= 0) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid cart product or quantity; review source rows';
  END IF;

  -- Match Java String.trim(): only strip leading/trailing U+0000 through U+0020.
  CREATE TEMPORARY TABLE cart_identity_review AS
    SELECT id, flavor, MIN(id) OVER sku AS keep_id, SUM(number) OVER sku AS total
    FROM (SELECT id, tenant_id, store_id, user_id, dish_id, setmeal_id, number,
          IF(setmeal_id IS NULL, REGEXP_REPLACE(COALESCE(dish_flavor,''),
             CONCAT('^[',CHAR(0 USING utf8mb3),'-',CHAR(32 USING utf8mb3),']+|[',CHAR(0 USING utf8mb3),'-',CHAR(32 USING utf8mb3),']+\\z'), ''), '') AS flavor
          FROM shopping_cart) normalized
    WINDOW sku AS (PARTITION BY tenant_id,store_id,user_id,dish_id,setmeal_id,CAST(flavor AS BINARY));
  IF EXISTS (SELECT 1 FROM cart_identity_review WHERE id <> keep_id) THEN
    SELECT c.tenant_id,c.store_id,c.user_id,c.dish_id,c.setmeal_id,HEX(r.flavor) AS flavor_hex,
           c.id,c.number,r.keep_id,r.total
      FROM shopping_cart c JOIN cart_identity_review r ON c.id=r.id
      WHERE r.total > c.number ORDER BY r.keep_id,c.id;
    IF COALESCE(@cart_duplicates_reviewed,0) <> 1 THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review listed cart duplicates before enabling merge';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM cart_identity_review WHERE total > 2147483647) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Merged cart quantity exceeds INT range';
  END IF;
  START TRANSACTION;
  DELETE c FROM shopping_cart c JOIN cart_identity_review r ON r.id=c.id WHERE r.id<>r.keep_id;
  UPDATE shopping_cart c JOIN cart_identity_review r ON r.id=c.id
    SET c.number=r.total,c.dish_flavor=r.flavor;
  COMMIT;
  DROP TEMPORARY TABLE cart_identity_review;
  ALTER TABLE shopping_cart
    MODIFY dish_flavor VARCHAR(50) CHARACTER SET utf8mb3 COLLATE utf8_bin NOT NULL DEFAULT '',
    ADD product_type TINYINT GENERATED ALWAYS AS (dish_id IS NULL) STORED,
    ADD product_id BIGINT GENERATED ALWAYS AS (COALESCE(dish_id,setmeal_id)) STORED,
    ADD flavor_key VARBINARY(200) GENERATED ALWAYS AS (CAST(dish_flavor AS BINARY)) STORED,
    ADD CONSTRAINT ck_cart_product CHECK ((dish_id IS NULL) <> (setmeal_id IS NULL) AND product_id > 0),
    ADD CONSTRAINT ck_cart_quantity CHECK (number > 0),
    ADD CONSTRAINT ck_cart_flavor CHECK (OCTET_LENGTH(dish_flavor)=0 OR
      (setmeal_id IS NULL AND ASCII(LEFT(dish_flavor,1))>32 AND ASCII(RIGHT(dish_flavor,1))>32)),
    ADD UNIQUE KEY uk_cart_identity (tenant_id,store_id,user_id,product_type,product_id,flavor_key);
END$$
CALL _junzhou_cart_identity()$$
DROP PROCEDURE _junzhou_cart_identity$$
DELIMITER ;

-- Stages 4.4 and 4.5. Apply after store menu/cart migrations.
CREATE TABLE IF NOT EXISTS store_settings (
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  address VARCHAR(255) NOT NULL DEFAULT '',
  latitude DECIMAL(9,6) NULL,
  longitude DECIMAL(9,6) NULL,
  opens_at VARCHAR(5) NOT NULL DEFAULT '00:00',
  closes_at VARCHAR(5) NOT NULL DEFAULT '00:00',
  delivery_radius_meters INT NOT NULL DEFAULT 5000,
  minimum_order_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  pack_fee_per_item DECIMAL(10,2) NOT NULL DEFAULT 1,
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 6,
  free_delivery_threshold DECIMAL(10,2) NULL,
  PRIMARY KEY(tenant_id,store_id),
  CONSTRAINT fk_store_settings_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id) ON DELETE CASCADE,
  CHECK (version >= 0),
  CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)),
  CHECK (opens_at REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$' AND closes_at REGEXP '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  CHECK (delivery_radius_meters BETWEEN 1 AND 100000),
  CHECK (minimum_order_amount >= 0 AND pack_fee_per_item >= 0 AND delivery_fee >= 0 AND (free_delivery_threshold IS NULL OR free_delivery_threshold >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO store_settings(tenant_id,store_id)
SELECT s.tenant_id,s.id FROM store s WHERE NOT EXISTS(
  SELECT 1 FROM store_settings c WHERE c.tenant_id=s.tenant_id AND c.store_id=s.id);
DROP PROCEDURE IF EXISTS _junzhou_order_store_snapshot;
DELIMITER $$
CREATE PROCEDURE _junzhou_order_store_snapshot()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='store_snapshot') THEN
    ALTER TABLE orders ADD store_snapshot TEXT NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='pricing_snapshot') THEN
    ALTER TABLE orders ADD pricing_snapshot TEXT NULL;
  END IF;
END$$
CALL _junzhou_order_store_snapshot()$$
DROP PROCEDURE _junzhou_order_store_snapshot$$
DELIMITER ;

-- Stage 5A: independent fulfillment contract. Stop all writers before upgrading.
CREATE TABLE IF NOT EXISTS store_dining_table (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY(id),
  UNIQUE KEY uk_dining_table_name(tenant_id,store_id,name),
  CONSTRAINT fk_dining_table_store FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id),
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DROP PROCEDURE IF EXISTS _junzhou_stage5a;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5a()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='fulfillment_type') THEN
    ALTER TABLE orders ADD fulfillment_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DELIVERY';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='fulfillment_status') THEN
    ALTER TABLE orders ADD fulfillment_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='contact_name') THEN
    ALTER TABLE orders ADD contact_name VARCHAR(32) CHARACTER SET utf8mb4 NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='contact_phone') THEN
    ALTER TABLE orders ADD contact_phone VARCHAR(11) NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='table_id') THEN
    ALTER TABLE orders ADD table_id BIGINT NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='table_snapshot') THEN
    ALTER TABLE orders ADD table_snapshot VARCHAR(64) CHARACTER SET utf8mb4 NULL;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='cart_fingerprint') THEN
    ALTER TABLE orders ADD cart_fingerprint VARCHAR(64) NULL;
  END IF;
  ALTER TABLE orders MODIFY address_book_id BIGINT NULL COMMENT 'Delivery address only';
  UPDATE orders SET fulfillment_status=CASE status WHEN 1 THEN 'AWAITING_PAYMENT' WHEN 2 THEN 'AWAITING_ACCEPTANCE' WHEN 3 THEN 'PREPARING' WHEN 4 THEN 'OUT_FOR_DELIVERY' WHEN 5 THEN 'COMPLETED' WHEN 6 THEN 'CANCELLED' ELSE 'CANCELLED' END WHERE fulfillment_status IS NULL;
  ALTER TABLE orders MODIFY fulfillment_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'AWAITING_PAYMENT';
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='orders' AND constraint_name='ck_orders_fulfillment_type') THEN
    ALTER TABLE orders ADD CONSTRAINT ck_orders_fulfillment_type CHECK(fulfillment_type IN ('DELIVERY','PICKUP','DINE_IN'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='orders' AND constraint_name='ck_orders_fulfillment_status') THEN
    ALTER TABLE orders ADD CONSTRAINT ck_orders_fulfillment_status CHECK(fulfillment_status IN ('AWAITING_PAYMENT','AWAITING_ACCEPTANCE','PREPARING','READY','OUT_FOR_DELIVERY','COMPLETED','CANCELLED'));
  END IF;
END$$
CALL _junzhou_stage5a()$$
DROP PROCEDURE _junzhou_stage5a$$
DELIMITER ;

-- Stage 5B. Stop writers; apply after stage 5A. Re-runnable.
DROP PROCEDURE IF EXISTS _junzhou_stage5b;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5b()
BEGIN
  -- RBAC originally accepted only resource:action; pickup uses resource:subresource:action.
  IF EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='rbac_permission' AND constraint_name='ck_permission_action') THEN
    ALTER TABLE rbac_permission DROP CHECK ck_permission_action,
      ADD CONSTRAINT ck_permission_action CHECK(REGEXP_LIKE(code,'^[a-z]+:[a-z]+(:[a-z]+)?$','c'));
  ELSE
    ALTER TABLE rbac_permission ADD CONSTRAINT ck_permission_action CHECK(REGEXP_LIKE(code,'^[a-z]+:[a-z]+(:[a-z]+)?$','c'));
  END IF;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='pickup_credential') THEN
    ALTER TABLE orders ADD pickup_number VARCHAR(32) NULL,
      ADD pickup_credential VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD pickup_verified_at DATETIME NULL, ADD pickup_verified_by BIGINT NULL;
  END IF;
  UPDATE orders SET pickup_number=CAST(id AS CHAR),pickup_credential=LOWER(HEX(RANDOM_BYTES(16)))
    WHERE fulfillment_type='PICKUP' AND pickup_credential IS NULL;
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT id,'order:pickup:ready','自取制作完成' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:pickup:ready');
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT id,'order:pickup:verify','核销自取订单' FROM tenant t
    WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code='order:pickup:verify');
  -- Existing roles receive no new action implicitly. Tenant administrators grant per role/store.
END$$
CALL _junzhou_stage5b()$$
DROP PROCEDURE _junzhou_stage5b$$
DELIMITER ;

-- Stage 5C; apply after 5B with writers stopped and a backup.
DROP PROCEDURE IF EXISTS _junzhou_stage5c;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage5c()
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='store_dining_table' AND column_name='qr_token') THEN
    ALTER TABLE store_dining_table ADD qr_token VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD current_session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD UNIQUE KEY uk_dining_token(qr_token), ADD UNIQUE KEY uk_dining_scope(tenant_id,store_id,id);
  END IF;
  UPDATE store_dining_table SET qr_token=LOWER(HEX(RANDOM_BYTES(16))) WHERE qr_token IS NULL;
  CREATE TABLE IF NOT EXISTS dining_session(
    id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tenant_id BIGINT NOT NULL,store_id BIGINT NOT NULL,table_id BIGINT NOT NULL,
    table_snapshot VARCHAR(64) NOT NULL,opened_at DATETIME NOT NULL,closed_at DATETIME NULL,
    PRIMARY KEY(id), UNIQUE KEY uk_session_scope(tenant_id,store_id,table_id,id),
    CONSTRAINT fk_session_table FOREIGN KEY(tenant_id,store_id,table_id) REFERENCES store_dining_table(tenant_id,store_id,id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  CREATE TABLE IF NOT EXISTS dining_session_member(
    session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,user_id BIGINT NOT NULL,
    PRIMARY KEY(session_id,user_id),
    CONSTRAINT fk_member_session FOREIGN KEY(session_id) REFERENCES dining_session(id),
    CONSTRAINT fk_member_user FOREIGN KEY(user_id) REFERENCES `user`(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='dining_session_id') THEN
    ALTER TABLE orders ADD dining_session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
      ADD KEY ix_orders_session(tenant_id,store_id,dining_session_id,status),
      ADD CONSTRAINT fk_order_dining_session FOREIGN KEY(tenant_id,store_id,table_id,dining_session_id) REFERENCES dining_session(tenant_id,store_id,table_id,id);
  END IF;
  INSERT INTO rbac_permission(tenant_id,code,name)
    SELECT t.id,c.code,c.name FROM tenant t CROSS JOIN (
      SELECT 'dining:read' code,'查看桌台' name UNION ALL SELECT 'dining:manage','管理桌台与桌码'
      UNION ALL SELECT 'dining:close','结束桌次' UNION ALL SELECT 'order:dine:complete','完成堂食订单'
    ) c WHERE NOT EXISTS(SELECT 1 FROM rbac_permission p WHERE p.tenant_id=t.id AND p.code=c.code);
END$$
CALL _junzhou_stage5c()$$
DROP PROCEDURE _junzhou_stage5c$$
DELIMITER ;

-- Stage 6: repeatable report indexes. Apply after 5C with backup and writers stopped.
DROP PROCEDURE IF EXISTS _junzhou_stage6;
DELIMITER $$
CREATE PROCEDURE _junzhou_stage6()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_order') THEN
  ALTER TABLE orders ADD INDEX ix_report_order(tenant_id,store_id,order_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_paid') THEN
  ALTER TABLE orders ADD INDEX ix_report_paid(tenant_id,store_id,checkout_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_completed') THEN
  ALTER TABLE orders ADD INDEX ix_report_completed(tenant_id,store_id,delivery_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='ix_report_cancelled') THEN
  ALTER TABLE orders ADD INDEX ix_report_cancelled(tenant_id,store_id,cancel_time);
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='refund_request' AND index_name='ix_report_refund') THEN
  ALTER TABLE refund_request ADD INDEX ix_report_refund(status,updated_at,order_id);
 END IF;
END$$
DELIMITER ;
CALL _junzhou_stage6();
DROP PROCEDURE _junzhou_stage6;

-- Mock payment schema and immutable channel (historical default WECHAT).
-- Apply after payment-account and order lifecycle migrations, with writers stopped.
-- Historical orders stay WECHAT. This migration does not modify merchant identity or store mappings.
DROP PROCEDURE IF EXISTS _junzhou_mock_payment;
DELIMITER $$
CREATE PROCEDURE _junzhou_mock_payment()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name='payment_channel') THEN
  ALTER TABLE orders ADD COLUMN payment_channel varchar(16) NOT NULL DEFAULT 'WECHAT';
 END IF;
 IF EXISTS(SELECT 1 FROM orders WHERE payment_channel NOT IN ('WECHAT','MOCK') OR payment_channel IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid existing payment channel; investigate before migration';
 END IF;
 CREATE TABLE IF NOT EXISTS mock_payment (
  order_id bigint NOT NULL PRIMARY KEY,
  state varchar(16) NOT NULL,
  amount_fen bigint NOT NULL,
  payer_openid varchar(128) NOT NULL,
  paid_at datetime(6) DEFAULT NULL,
  out_refund_no varchar(64) DEFAULT NULL,
  CONSTRAINT fk_mock_payment_order FOREIGN KEY(order_id) REFERENCES orders(id),
  CONSTRAINT ck_mock_payment_amount CHECK(amount_fen>0),
  CONSTRAINT ck_mock_payment_state CHECK(state IN ('NOTPAY','SUCCESS','CLOSED','REFUND'))
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
END$$
DELIMITER ;
CALL _junzhou_mock_payment();
DROP PROCEDURE _junzhou_mock_payment;
DROP TRIGGER IF EXISTS orders_payment_channel_immutable;
DROP TRIGGER IF EXISTS orders_payment_channel_validate;
DELIMITER $$
CREATE TRIGGER orders_payment_channel_immutable BEFORE UPDATE ON orders FOR EACH ROW
BEGIN
 IF NOT (NEW.payment_channel <=> OLD.payment_channel) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Order payment channel is immutable';
 END IF;
END$$
CREATE TRIGGER orders_payment_channel_validate BEFORE INSERT ON orders FOR EACH ROW
BEGIN
 IF NEW.payment_channel NOT IN ('WECHAT','MOCK') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown order payment channel';
 END IF;
END$$
DELIMITER ;
