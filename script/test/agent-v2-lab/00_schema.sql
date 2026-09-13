-- MySQL 8.0+; deliberately fails if either database already exists.
SET NAMES utf8mb4;
CREATE DATABASE agent_v2_lab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE agent_v2_lab;
CREATE TABLE _lab_manifest (owner VARCHAR(64) PRIMARY KEY, dataset_version INT NOT NULL, seed VARCHAR(64) NOT NULL) COMMENT='仅由本测试方案拥有的库；重建前必须核对标记';
INSERT INTO _lab_manifest VALUES ('chat2db-agent-v2-lab',1,'fixed-2026-six-months');
CREATE TABLE customers (
 id BIGINT PRIMARY KEY, customer_name VARCHAR(80) NOT NULL COMMENT '客户姓名，显示名称可能重复',
 region VARCHAR(20) NOT NULL COMMENT '客户所属销售大区', city VARCHAR(40) NOT NULL,
 member_tier VARCHAR(20) NOT NULL, registered_at DATETIME NOT NULL,
 phone VARCHAR(32) NULL COMMENT '可为空的联系电话', metadata_json JSON NOT NULL,
 INDEX idx_customers_region(region)
) COMMENT='客户资料：用于客户发现、分区销售和会员分析';
CREATE TABLE products (
 id BIGINT PRIMARY KEY, sku VARCHAR(32) NOT NULL UNIQUE, product_name VARCHAR(80) NOT NULL,
 category VARCHAR(40) NOT NULL COMMENT '商品类别', list_price DECIMAL(18,2) NOT NULL,
 unit_cost DECIMAL(18,2) NOT NULL, stock_qty INT NOT NULL
) COMMENT='商品目录：价格与成本以人民币元计，订单成交价保存在订单明细';
CREATE TABLE orders (
 id BIGINT PRIMARY KEY, order_no VARCHAR(32) NOT NULL UNIQUE, customer_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL COMMENT 'PENDING、PAID、SHIPPED、COMPLETED、CANCELLED',
 created_at DATETIME NOT NULL, shipping_fee DECIMAL(18,2) NOT NULL,
 discount_amount DECIMAL(18,2) NOT NULL, total_amount DECIMAL(18,2) NOT NULL,
 note VARCHAR(255) NULL, FOREIGN KEY(customer_id) REFERENCES customers(id),
 INDEX idx_orders_created_status(created_at,status), INDEX idx_orders_customer(customer_id)
) COMMENT='销售订单：total_amount=明细金额+运费-优惠；已付款须以支付成功记录为准';
CREATE TABLE order_items (
 id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 quantity INT NOT NULL, unit_price DECIMAL(18,2) NOT NULL,
 line_amount DECIMAL(18,2) NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(product_id) REFERENCES products(id),
 INDEX idx_items_order(order_id), INDEX idx_items_product(product_id)
) COMMENT='订单明细：保留成交单价，用于商品类别销售额；不可直接使用商品当前标价';
CREATE TABLE payments (
 id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, payment_no VARCHAR(40) NOT NULL UNIQUE,
 provider VARCHAR(20) NOT NULL, status VARCHAR(16) NOT NULL COMMENT 'SUCCESS 或 FAILED，统计收入只计 SUCCESS',
 amount DECIMAL(18,2) NOT NULL, paid_at DATETIME NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), INDEX idx_payments_order_status(order_id,status)
) COMMENT='支付流水：一个订单可含失败尝试，避免把失败支付重复计入收入';
CREATE TABLE refunds (
 id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL, payment_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL COMMENT 'SUCCESS 或 PENDING，净收入仅扣除 SUCCESS',
 amount DECIMAL(18,2) NOT NULL, reason VARCHAR(40) NOT NULL, requested_at DATETIME NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(payment_id) REFERENCES payments(id),
 INDEX idx_refunds_order_status(order_id,status)
) COMMENT='退款流水：待处理退款不影响已确认净收入';
CREATE TABLE event_log (
 id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, level VARCHAR(10) NOT NULL,
 occurred_at DATETIME NOT NULL, message MEDIUMTEXT NOT NULL COMMENT '每条消息固定4096个UTF-8字节，默认200行已超过512KiB',
 attributes JSON NOT NULL, INDEX idx_events_level_id(level,id)
) COMMENT='确定性事件日志：600行，用于大结果分页、文件搜索和尾部定位';
CREATE TABLE output_documents (
 id BIGINT PRIMARY KEY, title VARCHAR(80) NOT NULL, body MEDIUMTEXT NULL,
 payload JSON NULL, purpose VARCHAR(255) NOT NULL
) COMMENT='大字段测试文档：2MiB UTF-8长单行、2MiB JSON字符串、转义与多行文本';
CREATE TABLE value_edges (
 id BIGINT PRIMARY KEY, text_value VARCHAR(255) NULL,
 exact_amount DECIMAL(38,10) NULL, happened_at DATETIME(6) NULL,
 payload JSON NULL, binary_value VARBINARY(16) NULL
) COMMENT='值保真边界：SQL NULL、空串、前后空格、大整数小数、同名显示值和原始字节';
CREATE TABLE approval_sandbox (
 id INT PRIMARY KEY, amount DECIMAL(18,2) NOT NULL, note VARCHAR(80) NOT NULL
) COMMENT='唯一常规写入验收靶表；仅本测试库，可恢复到固定基线';
CREATE TABLE idempotency_probe (
 event_key VARCHAR(64) PRIMARY KEY, payload VARCHAR(255) NOT NULL, attempts INT NOT NULL
) COMMENT='明确使用唯一键验证业务幂等；不代表Agent自动去重普通INSERT或UPDATE';
CREATE DATABASE agent_v2_scope_lab CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE agent_v2_scope_lab;
CREATE TABLE _lab_manifest (owner VARCHAR(64) PRIMARY KEY, dataset_version INT NOT NULL, seed VARCHAR(64) NOT NULL) COMMENT='仅由本测试方案拥有的第二范围库';
INSERT INTO _lab_manifest VALUES ('chat2db-agent-v2-lab',1,'fixed-2026-six-months');
CREATE TABLE customers (id BIGINT PRIMARY KEY, customer_name VARCHAR(80) NOT NULL, scope_marker VARCHAR(40) NOT NULL) COMMENT='对照范围客户表：与主库同名但数据和结构不同';
CREATE TABLE orders (id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, total_amount DECIMAL(18,2) NOT NULL, scope_marker VARCHAR(40) NOT NULL) COMMENT='对照范围订单表：只有2行，不得与主库324行混淆';
