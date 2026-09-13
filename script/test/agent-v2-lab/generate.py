#!/usr/bin/env python3
"""Generate deterministic MySQL 8 fixtures; never connects to a database."""
from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OWNER = "chat2db-agent-v2-lab"
REGIONS = ["华东", "华南", "华北", "西南"]
CATEGORIES = ["数码配件", "家居生活", "运动户外", "办公文具"]
PROVIDERS = ["ALIPAY", "WECHAT", "CARD"]
COUNTS = [24, 36, 48, 60, 72, 84]
SCHEMA = """-- MySQL 8.0+; deliberately fails if either database already exists.
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
"""


def money(cents: int) -> str:
    return f"{cents // 100}.{cents % 100:02d}"


def quoted(value: str | None) -> str:
    if value is None:
        return "NULL"
    if value == "":
        return "''"
    # Hex strings are independent of NO_BACKSLASH_ESCAPES and connection escaping.
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)"


def at(value: datetime) -> str:
    return "'" + value.strftime("%Y-%m-%d %H:%M:%S") + "'"


def fixed_text(size: int, prefix: str, tail: str) -> tuple[str, str]:
    remaining = size - len((prefix + tail).encode())
    pattern = "汉字🙂"
    repeat, remainder = divmod(remaining, len(pattern.encode()))
    value = prefix + pattern * repeat + "z" * remainder + tail
    expression = f"CONCAT({quoted(prefix)},REPEAT({quoted(pattern)},{repeat}),{quoted('z' * remainder + tail)})"
    assert len(value.encode()) == size
    return value, expression


def insert(table: str, rows: list[list[str]], size: int = 100) -> str:
    return "".join(
        "INSERT INTO " + table + " VALUES\n" + ",\n".join("(" + ",".join(row) + ")" for row in rows[start:start+size]) + ";\n"
        for start in range(0, len(rows), size)
    )


def build() -> tuple[str, dict, dict]:
    sql = ["SET NAMES utf8mb4;\nUSE agent_v2_lab;\n"]
    customers = {i: {"id": i, "name": "同名客户" if i in (1, 2) else f"客户{i:03d}", "region": REGIONS[(i-1) % 4]} for i in range(1,49)}
    sql.append(insert("customers", [[str(i), quoted(c["name"]), quoted(c["region"]), quoted(["上海","广州","北京","成都"][(i-1)%4]),
        quoted(["BASIC","SILVER","GOLD"][(i-1)%3]), at(datetime(2025,1+(i-1)%12,1+(i-1)%20)),
        "NULL" if i%8==0 else quoted(f"1880000{i:04d}"),
        quoted(json.dumps({"channel":["web","store","app"][i%3],"tags":["测试客户"],"opt_in":i%2==0},ensure_ascii=False))]
        for i,c in customers.items()]))
    products = {i:{"id":i,"category":CATEGORIES[(i-1)%4],"price":1000+i*137,"cost":700+i*83} for i in range(1,13)}
    sql.append(insert("products", [[str(i),quoted(f"SKU-{i:03d}"),quoted(f"{p['category']}商品{i:02d}"),quoted(p["category"]),money(p["price"]),money(p["cost"]),str(1000+i*10)] for i,p in products.items()]))
    orders, items, payments, refunds = [], [], [], []
    order_id = 0
    for month,count in enumerate(COUNTS,1):
        for sequence in range(1,count+1):
            order_id += 1
            n = order_id
            status = "CANCELLED" if n%10==0 else "PENDING" if n%10==1 else "PAID" if n%10==2 else "SHIPPED" if n%10 in (3,4) else "COMPLETED"
            customer = ((n*7-1)%48)+1
            created = datetime(2026,month,1+(sequence-1)%20,9+sequence%8,sequence%60)
            subtotal = 0
            for j in range(1,4):
                product_id = ((n+j*3-1)%12)+1
                quantity = (n+j)%3+1
                price = products[product_id]["price"]*(100+(month-1)*3)//100
                line = price*quantity
                subtotal += line
                items.append({"id":len(items)+1,"order":n,"product":product_id,"quantity":quantity,"price":price,"amount":line})
            shipping,discount = n%4*100,n%5*50
            total = subtotal+shipping-discount
            order = {"id":n,"customer":customer,"status":status,"created":created,"shipping":shipping,"discount":discount,"total":total,"month":f"2026-{month:02d}"}
            orders.append(order)
            if status not in ("CANCELLED","PENDING"):
                if n%9==0:
                    payments.append({"id":len(payments)+1,"order":n,"status":"FAILED","provider":PROVIDERS[n%3],"amount":total,"at":created+timedelta(minutes=1)})
                pay = {"id":len(payments)+1,"order":n,"status":"SUCCESS","provider":PROVIDERS[n%3],"amount":total,"at":created+timedelta(minutes=5)}
                payments.append(pay)
                if status=="COMPLETED" and n%7==0:
                    refunds.append({"id":len(refunds)+1,"order":n,"payment":pay["id"],"status":"PENDING" if n%14==0 else "SUCCESS","amount":total//4,"reason":"QUALITY" if n%2 else "CHANGE_MIND","at":created+timedelta(days=3)})
    sql.append(insert("orders", [[str(o["id"]),quoted(f"ORD-2026-{o['id']:05d}"),str(o["customer"]),quoted(o["status"]),at(o["created"]),money(o["shipping"]),money(o["discount"]),money(o["total"]),"NULL" if o["id"]%11==0 else quoted(f"测试订单{o['id']:05d}")] for o in orders]))
    sql.append(insert("order_items", [[str(x["id"]),str(x["order"]),str(x["product"]),str(x["quantity"]),money(x["price"]),money(x["amount"])] for x in items]))
    sql.append(insert("payments", [[str(p["id"]),str(p["order"]),quoted(f"PAY-{p['id']:06d}"),quoted(p["provider"]),quoted(p["status"]),money(p["amount"]),at(p["at"])] for p in payments]))
    sql.append(insert("refunds", [[str(r["id"]),str(r["order"]),str(r["payment"]),quoted(r["status"]),money(r["amount"]),quoted(r["reason"]),at(r["at"])] for r in refunds]))
    event_rows, event_bytes = [],0
    for n in range(1,601):
        body,expression = fixed_text(4096,f"event-{n:06d}|",f"|TAIL_EVENT_{n:06d}")
        event_bytes += len(body.encode())
        event_rows.append([str(n),str((n-1)%48+1),quoted("ERROR" if n%30==0 else "WARN" if n%6==0 else "INFO"),at(datetime(2026,6,1)+timedelta(minutes=n)),expression,quoted(json.dumps({"event_id":n,"source":"agent-v2-lab"}))])
    sql.append(insert("event_log",event_rows,50))
    big_text,big_expression = fixed_text(2*1024*1024,"BEGIN_TEXT_2M|","|NEEDLE_TEXT_TAIL_9F2A")
    lines = "\n".join(f"line-{n:04d}\t客户{n%48+1:03d}\tvalue={n*17}" + ("\tNEEDLE_LINES_2399" if n==2399 else "") for n in range(1,2401)) + "\n"
    escaped = '首行\r\n次行\t"引号"\\反斜杠🙂\n末行\x00结束'
    sql.append(insert("output_documents",[
        ["1",quoted("2MiB UTF8 长单行"),big_expression,"NULL",quoted("按块读取必须保持汉字和emoji完整；尾部关键词在预览外")],
        ["2",quoted("2MiB JSON 字符串"),"NULL","JSON_OBJECT('kind','agent_v2_fixture','body',REPEAT('x',2097152),'tail','NEEDLE_JSON_TAIL_7B3C')",quoted("JSON_VALUE完整存储；数据库JSON键序与空格不作为字节预期")],
        ["3",quoted("2400行文本"),quoted(lines),"NULL",quoted("真实换行在SQL结果JSONL中会被转义；逐行文档查看与JSONL物理行不同")],
        ["4",quoted("控制字符和SQL NULL"),quoted(escaped),"JSON_OBJECT('nullable',NULL,'empty','','quoted','a\"b')",quoted("保留CRLF、tab、引号、反斜杠、emoji、NUL；不能以截断作为完整输出")],
    ],1))
    edge_amounts = ["9007199254740993.1234567890","0.0000000001","-9007199254740993.1234567890",None,"12345678901234567890.1234567890","1.2300000000"]
    edge_texts = ["同名行","同名行","  keep spaces  ",None,"",'引号"和\\与🙂']
    sql.append(insert("value_edges", [[str(i),quoted(edge_texts[i-1]),"NULL" if edge_amounts[i-1] is None else edge_amounts[i-1],
        "NULL" if i==4 else "'2026-01-02 03:04:05.123456'", "JSON_OBJECT('nullable',NULL,'id',"+str(i)+")","NULL" if i==4 else "0x0001027FFF"] for i in range(1,7)]))
    sql.append("INSERT INTO approval_sandbox VALUES (1,100.00,'baseline'),(2,200.00,'baseline');\n")
    sql.append("USE agent_v2_scope_lab;\n")
    sql.append(insert("customers",[["1",quoted("对照库客户A"),"'scope_b'"],["2",quoted("对照库客户B"),"'scope_b'"]]))
    sql.append("INSERT INTO orders VALUES(1,1,1.11,'scope_b'),(2,2,2.22,'scope_b');\nUSE agent_v2_lab;\n")
    paid = [p for p in payments if p["status"]=="SUCCESS"]
    paid_orders = {p["order"] for p in paid}
    successful_refunds = [r for r in refunds if r["status"]=="SUCCESS"]
    monthly = defaultdict(lambda:{"revenue":0,"paid_orders":0})
    categories,regions,providers,customer_stats = defaultdict(int),defaultdict(int),Counter(),defaultdict(lambda:{"orders":0,"revenue":0})
    by_id = {o["id"]:o for o in orders}
    for p in paid:
        order = by_id[p["order"]]
        monthly[order["month"]]["revenue"] += p["amount"]
        monthly[order["month"]]["paid_orders"] += 1
        regions[customers[order["customer"]]["region"]] += p["amount"]
        providers[p["provider"]] += 1
        customer_stats[order["customer"]]["orders"] += 1
        customer_stats[order["customer"]]["revenue"] += p["amount"]
    for x in items:
        if x["order"] in paid_orders:
            categories[products[x["product"]]["category"]] += x["amount"]
    gross = sum(p["amount"] for p in paid)
    refunded = sum(r["amount"] for r in successful_refunds)
    expected = {
      "dataset":"fixed-2026-six-months", "schema":"agent_v2_lab", "scope_schema":"agent_v2_scope_lab",
      "table_rows":{"customers":len(customers),"products":len(products),"orders":len(orders),"order_items":len(items),"payments":len(payments),"refunds":len(refunds),"event_log":600,"output_documents":4,"value_edges":6,"approval_sandbox":2,"idempotency_probe":0,"_lab_manifest":1},
      "order_status_counts":dict(sorted(Counter(o["status"] for o in orders).items())),
      "payment_status_counts":dict(sorted(Counter(p["status"] for p in payments).items())),
      "refund_status_counts":dict(sorted(Counter(r["status"] for r in refunds).items())),
      "gross_revenue":money(gross),"successful_refunds":money(refunded),"net_revenue":money(gross-refunded),
      "monthly_paid":[{"month":m,"revenue":money(v["revenue"]),"paid_orders":v["paid_orders"]} for m,v in sorted(monthly.items())],
      "monthly_all_orders":[{"month":f"2026-{i:02d}","order_count":count} for i,count in enumerate(COUNTS,1)],
      "category_paid_item_revenue":[{"category":c,"revenue":money(categories[c])} for c in CATEGORIES],
      "region_paid_revenue":[{"region":r,"revenue":money(regions[r])} for r in REGIONS],
      "provider_success_count":[{"provider":p,"payment_count":providers[p]} for p in sorted(providers)],
      "funnel":[{"stage":"创建订单","stage_order":1,"orders":len(orders)},{"stage":"支付成功","stage_order":2,"orders":len(paid)},{"stage":"已经发货","stage_order":3,"orders":sum(o["status"] in ("SHIPPED","COMPLETED") for o in orders)},{"stage":"交易完成","stage_order":4,"orders":sum(o["status"]=="COMPLETED" for o in orders)}],
      "customer_scatter":[{"customer_id":i,"order_count":v["orders"],"total_spend":money(v["revenue"])} for i,v in sorted(customer_stats.items())],
      "large_fields":{"text_octets":len(big_text.encode()),"text_characters":len(big_text),"text_sha256":hashlib.sha256(big_text.encode()).hexdigest(),"text_tail":"NEEDLE_TEXT_TAIL_9F2A","json_body_characters":2097152,"json_body_sha256":hashlib.sha256(b'x'*2097152).hexdigest(),"json_tail":"NEEDLE_JSON_TAIL_7B3C","multiline_rows":2400,"multiline_sha256":hashlib.sha256(lines.encode()).hexdigest(),"escaped_hex":escaped.encode().hex().upper()},
      "event_log":{"rows":600,"message_octets_each":4096,"total_message_octets":event_bytes,"level_counts":{"ERROR":20,"WARN":80,"INFO":500},"pages":[{"page":1,"first_id":1,"last_id":200,"rows":200,"hasMore":True},{"page":2,"first_id":201,"last_id":400,"rows":200,"hasMore":True},{"page":3,"first_id":401,"last_id":600,"rows":200,"hasMore":False}]},
      "value_edges":{"decimal_text":edge_amounts,"null_text_ids":[4],"empty_text_ids":[5],"duplicate_display_ids":[1,2],"binary_hex":"0001027FFF"},
      "scope_rows":{"customers":2,"orders":2,"total_amount":"3.33","scope_marker":"scope_b"},
      "estimated_payload_bytes":event_bytes+len(big_text.encode())+2097152+len(lines.encode())+len(orders)*512+len(items)*128,
    }
    return "".join(sql),expected,{"customers":customers,"products":products,"orders":orders,"items":items,"payments":payments,"refunds":refunds}


def checks(data: dict, expected: dict) -> None:
    assert quoted(None) == "NULL" and quoted("") == "''"
    assert len(data["orders"])==324 and len(data["items"])==972
    amounts=defaultdict(int)
    for x in data["items"]:
        assert x["quantity"]>0 and x["amount"]==x["quantity"]*x["price"]
        amounts[x["order"]]+=x["amount"]
    for o in data["orders"]:
        assert o["total"]==amounts[o["id"]]+o["shipping"]-o["discount"]
        assert o["customer"] in data["customers"]
    paid=Counter(p["order"] for p in data["payments"] if p["status"]=="SUCCESS")
    assert all(count==1 for count in paid.values())
    assert sum(paid.values())==259
    pay_by_id={p["id"]:p for p in data["payments"]}
    for r in data["refunds"]:
        assert pay_by_id[r["payment"]]["status"]=="SUCCESS"
        assert 0<r["amount"]<pay_by_id[r["payment"]]["amount"]
    assert len(expected["customer_scatter"])==48
    assert expected["estimated_payload_bytes"]<10*1024*1024
    assert [row["orders"] for row in expected["funnel"]]==[324,259,226,160]
    assert expected["large_fields"]["text_octets"]==2097152


def verification_sql(expected: dict) -> str:
    checks = []
    for table,count in expected["table_rows"].items():
        checks.append(("rows_"+table,f"(SELECT COUNT(*) FROM agent_v2_lab.{table})",str(count)))
    checks += [
      ("main_tables","(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='agent_v2_lab')","12"),
      ("scope_customers","(SELECT COUNT(*) FROM agent_v2_scope_lab.customers)","2"),
      ("scope_orders","(SELECT COUNT(*) FROM agent_v2_scope_lab.orders)","2"),
      ("scope_total","(SELECT SUM(total_amount) FROM agent_v2_scope_lab.orders)","3.33"),
      ("gross_revenue","(SELECT SUM(amount) FROM agent_v2_lab.payments WHERE status='SUCCESS')",expected["gross_revenue"]),
      ("successful_refunds","(SELECT SUM(amount) FROM agent_v2_lab.refunds WHERE status='SUCCESS')",expected["successful_refunds"]),
      ("net_revenue","((SELECT SUM(amount) FROM agent_v2_lab.payments WHERE status='SUCCESS')-(SELECT SUM(amount) FROM agent_v2_lab.refunds WHERE status='SUCCESS'))",expected["net_revenue"]),
      ("order_total_mismatch","(SELECT COUNT(*) FROM agent_v2_lab.orders o JOIN (SELECT order_id,SUM(line_amount) AS subtotal FROM agent_v2_lab.order_items GROUP BY order_id) i ON i.order_id=o.id WHERE o.total_amount<>i.subtotal+o.shipping_fee-o.discount_amount)","0"),
      ("successful_payments_per_order","(SELECT COUNT(*) FROM (SELECT order_id FROM agent_v2_lab.payments WHERE status='SUCCESS' GROUP BY order_id HAVING COUNT(*)<>1) duplicate_payments)","0"),
      ("failed_payments","(SELECT COUNT(*) FROM agent_v2_lab.payments WHERE status='FAILED')","30"),
      ("text_octets","(SELECT OCTET_LENGTH(body) FROM agent_v2_lab.output_documents WHERE id=1)",str(expected["large_fields"]["text_octets"])),
      ("text_sha256","(SELECT SHA2(body,256) FROM agent_v2_lab.output_documents WHERE id=1)",quoted(expected["large_fields"]["text_sha256"])),
      ("json_body_chars","(SELECT CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.body'))) FROM agent_v2_lab.output_documents WHERE id=2)","2097152"),
      ("json_body_sha256","(SELECT SHA2(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.body')),256) FROM agent_v2_lab.output_documents WHERE id=2)",quoted(expected["large_fields"]["json_body_sha256"])),
      ("json_tail","(SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.tail')) FROM agent_v2_lab.output_documents WHERE id=2)",quoted(expected["large_fields"]["json_tail"])),
      ("multiline_sha256","(SELECT SHA2(body,256) FROM agent_v2_lab.output_documents WHERE id=3)",quoted(expected["large_fields"]["multiline_sha256"])),
      ("escaped_text_hex","(SELECT HEX(body) FROM agent_v2_lab.output_documents WHERE id=4)",quoted(expected["large_fields"]["escaped_hex"])),
      ("event_min_octets","(SELECT MIN(OCTET_LENGTH(message)) FROM agent_v2_lab.event_log)","4096"),
      ("event_max_octets","(SELECT MAX(OCTET_LENGTH(message)) FROM agent_v2_lab.event_log)","4096"),
      ("event_sum_octets","(SELECT SUM(OCTET_LENGTH(message)) FROM agent_v2_lab.event_log)","2457600"),
      ("edge_decimal_text","(SELECT CAST(exact_amount AS CHAR) FROM agent_v2_lab.value_edges WHERE id=1)",quoted("9007199254740993.1234567890")),
      ("edge_sql_null","(SELECT COUNT(*) FROM agent_v2_lab.value_edges WHERE text_value IS NULL AND exact_amount IS NULL)","1"),
      ("edge_empty_string","(SELECT COUNT(*) FROM agent_v2_lab.value_edges WHERE text_value='')","1"),
      ("edge_binary_hex","(SELECT HEX(binary_value) FROM agent_v2_lab.value_edges WHERE id=1)",quoted("0001027FFF")),
      ("baseline_1","(SELECT amount FROM agent_v2_lab.approval_sandbox WHERE id=1)","100.00"),
      ("baseline_2","(SELECT amount FROM agent_v2_lab.approval_sandbox WHERE id=2)","200.00"),
    ]
    for row in expected["monthly_paid"]:
        m=row["month"]
        checks.append(("monthly_paid_"+m,f"(SELECT SUM(p.amount) FROM agent_v2_lab.orders o JOIN agent_v2_lab.payments p ON p.order_id=o.id AND p.status='SUCCESS' WHERE DATE_FORMAT(o.created_at,'%Y-%m')='{m}')",row["revenue"]))
        checks.append(("monthly_count_"+m,f"(SELECT COUNT(*) FROM agent_v2_lab.orders o JOIN agent_v2_lab.payments p ON p.order_id=o.id AND p.status='SUCCESS' WHERE DATE_FORMAT(o.created_at,'%Y-%m')='{m}')",str(row["paid_orders"])))
    for level,count in expected["event_log"]["level_counts"].items():
        checks.append(("event_level_"+level,f"(SELECT COUNT(*) FROM agent_v2_lab.event_log WHERE level='{level}')",str(count)))
    return "-- Read-only seed assertions: each row is check_name, passed(1), actual.\n"+"\n".join(
        f"SELECT '{name}' AS check_name, {expression}={wanted} AS passed, CAST({expression} AS CHAR) AS actual;" for name,expression,wanted in checks
    )+"\n"


def main() -> None:
    parser=argparse.ArgumentParser()
    parser.add_argument("--check",action="store_true",help="Check existing generated files without writing files or connecting to MySQL")
    args=parser.parse_args()
    sql,expected,data=build()
    checks(data,expected)
    generated = {"00_schema.sql":SCHEMA,"10_data.sql":sql,
      "expected.json":json.dumps(expected,ensure_ascii=False,indent=2)+"\n","20_verify.sql":verification_sql(expected)}
    assert "CONVERT(0x USING" not in sql, "Empty strings must remain SQL string literals"
    for name,content in generated.items():
        if args.check:
            if not (ROOT/name).exists() or (ROOT/name).read_text(encoding="utf-8") != content:
                raise SystemExit(f"Generated file differs or is missing: {name}; run python3 generate.py first")
        else:
            (ROOT/name).write_text(content,encoding="utf-8")
    print(json.dumps({"mode":"check" if args.check else "generate","database_mutations":False,"sql_bytes":len(sql.encode()),"estimated_payload_bytes":expected["estimated_payload_bytes"],"orders":len(data["orders"]),"paid_orders":259,"net_revenue":expected["net_revenue"],"checks":"passed"},ensure_ascii=False))


if __name__=="__main__":
    main()
