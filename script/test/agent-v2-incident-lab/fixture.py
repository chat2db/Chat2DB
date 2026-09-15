#!/usr/bin/env python3
"""Create and verify a deterministic, isolated MySQL payment incident dataset."""
import argparse
from collections import Counter
from datetime import datetime, timedelta
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
MYSQL = ROOT.parent / "agent-v2-lab/mysql.sh"
DATABASE = "agent_v2_incident_lab"
OWNER = "chat2db-agent-v2-incident-v1"
START = datetime(2026, 9, 13, 18)
REGIONS = ["华东", "华西"]
CHANNELS = ["WECHAT", "CARD"]
PHASES = ["request_accepted", "order_loaded", "route_selected", "idempotency_checked",
          "gateway_connect", "gateway_result", "order_state_changed", "response_sent"]

SCHEMA = f"""
SET NAMES utf8mb4;
CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE {DATABASE};
CREATE TABLE _lab_manifest (owner VARCHAR(64) PRIMARY KEY, dataset_version INT NOT NULL, completed BOOLEAN NOT NULL) ENGINE=InnoDB;
INSERT INTO _lab_manifest VALUES ('{OWNER}',1,false);
CREATE TABLE payment_orders (
 order_id BIGINT PRIMARY KEY, order_no VARCHAR(32) NOT NULL UNIQUE, customer_id VARCHAR(24) NOT NULL,
 region VARCHAR(16) NOT NULL, channel VARCHAR(16) NOT NULL, amount DECIMAL(18,2) NOT NULL,
 created_at DATETIME(3) NOT NULL, final_status VARCHAR(16) NOT NULL,
 attempt_count INT NOT NULL, paid_at DATETIME(3) NULL,
 INDEX idx_order_time(created_at), INDEX idx_order_status(final_status,created_at)
) ENGINE=InnoDB COMMENT='支付订单：时间为北京时间；一个订单可有多次尝试；最终状态PAID或FAILED；金额单位元';
CREATE TABLE payment_request_logs (
 id BIGINT PRIMARY KEY, occurred_at DATETIME(3) NOT NULL, request_id VARCHAR(48) NOT NULL,
 order_id BIGINT NOT NULL, attempt_no INT NOT NULL, service VARCHAR(32) NOT NULL,
 instance VARCHAR(48) NOT NULL, region VARCHAR(16) NOT NULL, channel VARCHAR(16) NOT NULL,
 level VARCHAR(8) NOT NULL, event VARCHAR(32) NOT NULL, version VARCHAR(16) NOT NULL,
 elapsed_ms INT NOT NULL, message MEDIUMTEXT NOT NULL,
 INDEX idx_log_time(occurred_at), INDEX idx_log_request(request_id,occurred_at),
 INDEX idx_log_order(order_id,attempt_no), INDEX idx_log_level(level,occurred_at),
 FOREIGN KEY(order_id) REFERENCES payment_orders(order_id)
) ENGINE=InnoDB COMMENT='支付请求逐阶段日志：message保存请求上下文、网络诊断、状态转换及调用栈；一笔订单有多行日志，错误条数不等于失败订单数';
CREATE TABLE service_deployments (
 id BIGINT PRIMARY KEY, deployed_at DATETIME NOT NULL, service VARCHAR(32) NOT NULL,
 region VARCHAR(16) NOT NULL, action VARCHAR(16) NOT NULL, previous_version VARCHAR(16) NOT NULL,
 version VARCHAR(16) NOT NULL, change_ticket VARCHAR(32) NOT NULL,
 config_before JSON NOT NULL, config_after JSON NOT NULL, description VARCHAR(512) NOT NULL,
 INDEX idx_deployment_time(deployed_at)
) ENGINE=InnoDB COMMENT='服务发布记录：保存发布时间、范围、版本、配置差异与回滚动作；时间为北京时间';
"""


def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, datetime):
        value = value.isoformat(sep=" ", timespec="milliseconds")
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return "CONVERT(0x" + str(value).encode().hex() + " USING utf8mb4)"


def orders():
    for number in range(4800):
        region, channel = REGIONS[(number // 2) % 2], CHANNELS[number % 2]
        when = START + timedelta(milliseconds=number * 1500)
        affected = 1600 <= number < 2000 and number % 4 == 0
        retry = affected and number % 20 == 0
        cents = 2999 + (number * 137) % 250000
        yield {
            "id": number + 1, "no": f"PAY{START:%Y%m%d}{number + 1:06d}",
            "customer": f"CUST-{(number * 37) % 1200 + 1:06d}",
            "region": region, "channel": channel, "created": when, "amount": f"{cents / 100:.2f}",
            "affected": affected, "retry": retry, "status": "FAILED" if affected and not retry else "PAID",
        }


def log_rows(order, first_id):
    for attempt in range(1, 3 if order["retry"] else 2):
        when = order["created"] + timedelta(seconds=12 if attempt == 2 else 0)
        failed = order["affected"] and attempt == 1
        canary = START + timedelta(minutes=40) <= when < START + timedelta(minutes=50)
        version = "1.19.0" if canary and order["region"] == "华东" and attempt == 1 else "1.18.4"
        timeout = 80 if version == "1.19.0" else 800
        connect_ms = 120 + order["id"] % 37 if order["channel"] == "WECHAT" else 21 + order["id"] % 14
        network_ms = min(timeout, connect_ms)
        request_id = f"req-{order['no']}-{attempt}"
        pool = "stable-" if attempt == 2 else ""
        instance = f"payment-{'east' if order['region'] == '华东' else 'west'}-{pool}{order['id'] % 3 + 1:02d}"
        offsets = [0, 8, 15, 21, 21 + network_ms, 35 + network_ms, 45 + network_ms, 50 + network_ms]
        facts = [
            {"method": "POST", "path": "/api/payments/confirm", "contentLength": 482 + order["id"] % 613,
             "client": ["iOS", "Android", "Web"][order["id"] % 3], "operation": "确认订单并发起支付"},
            {"rows": 1, "orderState": "RETRY_PENDING" if attempt == 2 else "PENDING", "currency": "CNY",
             "cache": "MISS" if order["id"] % 37 == 0 else "HIT", "databaseLatencyMs": 3 + order["id"] % 18,
             "operation": "读取订单、核对金额及客户权限"},
            {"upstream": "wechat-gateway.test" if order["channel"] == "WECHAT" else "card-gateway.test",
             "pool": "fallback-stable" if attempt == 2 else "regional-primary", "configRevision": version,
             "connectTimeoutMs": timeout, "readTimeoutMs": 3000, "operation": "按地区和渠道选择连接池"},
            {"key": order["no"], "reservation": "existing_retry" if attempt == 2 else "created",
             "previousCharge": None, "lockWaitMs": order["id"] % 7, "operation": "检查订单级幂等记录"},
            {"result": "TIMEOUT" if failed else "CONNECTED", "dnsMs": 4 + order["id"] % 5,
             "connectionBudgetMs": timeout, "socketElapsedMs": network_ms,
             "poolActive": 4 + order["id"] % 9, "poolCapacity": 64,
             "error": "CONNECT_TIMEOUT" if failed else None,
             "operation": "建立支付通道连接，记录连接预算和实际消耗"},
            {"upstreamStatus": None if failed else 200, "paymentStatus": None if failed else "SUCCESS",
             "paymentId": None if failed else f"trade-{order['no']}",
             "requestBodySent": not failed, "charged": not failed,
             "operation": "连接未就绪，请求未发送" if failed else "收到通道响应并校验签名"},
            {"from": "RETRY_PENDING" if attempt == 2 else "PENDING",
             "to": "RETRY_PENDING" if failed and order["retry"] else "FAILED" if failed else "PAID",
             "retryScheduled": failed and order["retry"], "writeRows": 1,
             "operation": "提交订单状态变更，保留首次请求与后续重试的关联"},
            {"httpStatus": 504 if failed else 200, "clientMessage": "支付连接超时，请稍后重试" if failed else "支付完成",
             "durationMs": offsets[-1], "responseBytes": 238 + order["id"] % 301,
             "operation": "将本次尝试结果返回客户端"},
        ]
        for phase, (event, elapsed, fact) in enumerate(zip(PHASES, offsets, facts)):
            level = "ERROR" if failed and phase in (4, 7) else "WARN" if failed and phase in (5, 6) else "INFO"
            if phase == 1 and order["id"] % 37 == 0:
                level = "WARN"
            message = json.dumps({
                "timestamp": (when + timedelta(milliseconds=elapsed)).isoformat(timespec="milliseconds") + "+08:00",
                "request": {"id": request_id, "orderNo": order["no"], "customerId": order["customer"],
                            "region": order["region"], "channel": order["channel"], "attempt": attempt,
                            "amount": order["amount"], "currency": "CNY"},
                "runtime": {"service": "payment-api", "instance": instance, "version": version,
                            "worker": f"http-worker-{order['id'] % 32}", "queueDepth": order["id"] % 11},
                "event": event, "context": fact,
                "span": {"traceId": f"trace-{order['no']}", "spanId": f"span-{first_id:08d}",
                         "parentSpan": f"request-{order['id']}-{attempt}", "elapsedMs": elapsed,
                         "sampling": "retained", "logSequence": phase + 1},
                **({"exception": {"type": "ConnectTimeoutException", "message": f"Connection not established within {timeout}ms",
                    "frames": ["PaymentGatewayClient.openConnection:184", "PaymentAttemptService.confirm:227",
                               "PaymentController.confirm:93", f"RegionalPool.acquire[{instance}]"]}} if failed and phase == 4 else {}),
            }, ensure_ascii=False, indent=2)
            yield [first_id, when + timedelta(milliseconds=elapsed), request_id, order["id"], attempt,
                   "payment-api", instance, order["region"], order["channel"], level, event, version, elapsed, message]
            first_id += 1


def deployment_rows():
    return [
        [1, START - timedelta(minutes=10), "payment-api", "ALL", "RELEASE", "1.18.3", "1.18.4", "CHG-2401",
         {"connectTimeoutMs": 800, "readTimeoutMs": 3000}, {"connectTimeoutMs": 800, "readTimeoutMs": 3000}, "支付审计字段补充"],
        [2, START + timedelta(minutes=35), "order-api", "ALL", "RELEASE", "3.2.0", "3.2.1", "CHG-2402",
         {"cacheTtlSeconds": 120}, {"cacheTtlSeconds": 180}, "订单查询缓存参数调整"],
        [3, START + timedelta(minutes=40), "payment-api", "华东", "RELEASE", "1.18.4", "1.19.0", "CHG-2403",
         {"pool": "regional-primary", "connectTimeoutMs": 800, "readTimeoutMs": 3000},
         {"pool": "regional-primary", "connectTimeoutMs": 80, "readTimeoutMs": 3000}, "区域主连接池配置更新，保留稳定回退实例"],
        [4, START + timedelta(minutes=50), "payment-api", "华东", "ROLLBACK", "1.19.0", "1.18.4", "CHG-2403-R",
         {"pool": "regional-primary", "connectTimeoutMs": 80, "readTimeoutMs": 3000},
         {"pool": "regional-primary", "connectTimeoutMs": 800, "readTimeoutMs": 3000}, "恢复上一个主连接池配置版本"],
        [5, START + timedelta(minutes=70), "notification-api", "ALL", "RELEASE", "2.7.0", "2.7.1", "CHG-2404",
         {"batchSize": 100}, {"batchSize": 200}, "支付通知批次大小调整"],
        [6, START + timedelta(minutes=85), "metrics-agent", "华西", "RELEASE", "4.1.0", "4.1.1", "CHG-2405",
         {"flushSeconds": 30}, {"flushSeconds": 30}, "指标标签规范调整"],
    ]


def insert(stream, table, rows):
    batch = []
    for row in rows:
        batch.append("(" + ",".join(map(sql_value, row)) + ")")
        if len(batch) == 100:
            stream.write(f"INSERT INTO {table} VALUES\n" + ",\n".join(batch) + ";\n")
            batch.clear()
    if batch:
        stream.write(f"INSERT INTO {table} VALUES\n" + ",\n".join(batch) + ";\n")


def generate(directory):
    directory.mkdir(parents=True, exist_ok=True)
    sql = directory / "dataset.sql"
    data = list(orders())
    expected = {"database": DATABASE, "start": str(START), "end": str(START + timedelta(hours=2)),
                "orders": len(data), "logs": 0, "deployments": 6,
                "affected_orders": sum(o["affected"] for o in data), "retry_success": sum(o["retry"] for o in data),
                "final_failed": sum(o["status"] == "FAILED" for o in data), "message_bytes": 0,
                "first_200_message_bytes": 0, "levels": Counter()}
    digest = hashlib.sha256()

    def logs():
        next_id = 1
        for order in data:
            for row in log_rows(order, next_id):
                size = len(row[-1].encode())
                expected["message_bytes"] += size
                if row[0] <= 200:
                    expected["first_200_message_bytes"] += size
                expected["levels"][row[9]] += 1
                digest.update(f"{row[0]}\t{hashlib.sha256(row[-1].encode()).hexdigest()}\n".encode())
                expected["logs"] += 1
                next_id = row[0] + 1
                yield row

    with sql.open("w") as stream:
        stream.write(SCHEMA + "\nSTART TRANSACTION;\n")
        insert(stream, "payment_orders", ([o["id"], o["no"], o["customer"], o["region"], o["channel"],
                o["amount"], o["created"], o["status"], 2 if o["retry"] else 1,
                o["created"] + timedelta(seconds=13 if o["retry"] else 1) if o["status"] == "PAID" else None] for o in data))
        insert(stream, "payment_request_logs", logs())
        insert(stream, "service_deployments", deployment_rows())
        stream.write("UPDATE _lab_manifest SET completed=true;\nCOMMIT;\n")
    expected["messages_sha256"] = digest.hexdigest()
    assert expected["logs"] == 38560 and expected["affected_orders"] == 100
    assert expected["retry_success"] == 20 and expected["final_failed"] == 80
    assert expected["message_bytes"] < 80 * 1024 * 1024 and expected["first_200_message_bytes"] > 32 * 1024
    (directory / "expected.json").write_text(json.dumps(expected, ensure_ascii=False, indent=2) + "\n")
    return expected


def query(sql):
    result = subprocess.run(["bash", str(MYSQL), "--batch", "--skip-column-names", "--raw", "-e", sql],
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout


def verify(expected):
    prefix = f"{DATABASE}."
    assert query(f"SELECT owner,dataset_version,completed FROM {prefix}_lab_manifest").strip() == f"{OWNER}\t1\t1"
    checks = {
        "orders": f"SELECT COUNT(*) FROM {prefix}payment_orders",
        "logs": f"SELECT COUNT(*) FROM {prefix}payment_request_logs",
        "deployments": f"SELECT COUNT(*) FROM {prefix}service_deployments",
        "affected_orders": f"SELECT COUNT(DISTINCT order_id) FROM {prefix}payment_request_logs WHERE event='gateway_connect' AND level='ERROR'",
        "retry_success": f"SELECT COUNT(*) FROM {prefix}payment_orders WHERE attempt_count=2 AND final_status='PAID'",
        "final_failed": f"SELECT COUNT(*) FROM {prefix}payment_orders WHERE final_status='FAILED'",
        "message_bytes": f"SELECT SUM(OCTET_LENGTH(message)) FROM {prefix}payment_request_logs",
        "first_200_message_bytes": f"SELECT SUM(OCTET_LENGTH(message)) FROM {prefix}payment_request_logs WHERE id<=200",
    }
    for key, sql in checks.items():
        assert int(query(sql).strip()) == expected[key], key
    actual = query(f"SELECT id,SHA2(message,256) FROM {prefix}payment_request_logs ORDER BY id")
    assert hashlib.sha256(actual.encode()).hexdigest() == expected["messages_sha256"], "Message integrity"
    levels = dict(row.split("\t") for row in query(
        f"SELECT level,COUNT(*) FROM {prefix}payment_request_logs GROUP BY level").splitlines())
    assert {key: int(value) for key, value in levels.items()} == expected["levels"]
    assert query(f"SELECT COUNT(DISTINCT request_id) FROM {prefix}payment_request_logs").strip() == "4820"
    assert query(f"SELECT COUNT(*) FROM (SELECT request_id FROM {prefix}payment_request_logs GROUP BY request_id HAVING COUNT(*)<>8) invalid_requests").strip() == "0"
    assert query(f"SELECT COUNT(*) FROM {prefix}payment_orders o JOIN {prefix}payment_request_logs l ON l.order_id=o.order_id AND l.attempt_no=o.attempt_count AND l.event='order_state_changed' WHERE JSON_UNQUOTE(JSON_EXTRACT(l.message,'$.context.to'))<>o.final_status").strip() == "0"
    assert query(f"SELECT COUNT(*) FROM {prefix}payment_orders WHERE final_status='FAILED' AND (region<>'华东' OR channel<>'WECHAT')").strip() == "0"
    assert query(f"SELECT COUNT(*) FROM {prefix}payment_orders WHERE created_at>='{START + timedelta(minutes=50)}' AND final_status='FAILED'").strip() == "0"
    assert datetime.fromisoformat(query(f"SELECT MIN(created_at) FROM {prefix}payment_orders").strip()) == START
    for table, field, begin in [("payment_orders", "created_at", START),
                                ("payment_request_logs", "occurred_at", START),
                                ("service_deployments", "deployed_at", START - timedelta(minutes=10))]:
        assert query(f"SELECT COUNT(*) FROM {prefix}{table} WHERE {field}<'{begin}' OR {field}>='{START + timedelta(hours=2)}'").strip() == "0", table
    assert query(f"SELECT COUNT(*) FROM {prefix}payment_request_logs WHERE event='gateway_result' AND JSON_EXTRACT(message,'$.context.charged')=true").strip() == "4720"
    assert query(f"SELECT COUNT(DISTINCT order_id) FROM {prefix}payment_request_logs WHERE event='gateway_result' AND JSON_EXTRACT(message,'$.context.charged')=true").strip() == "4720"
    print(json.dumps({"verified": True, **expected}, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["generate", "install", "verify"])
    parser.add_argument("--output-dir", type=Path,
                        default=Path.home() / "Library/Caches/chat2db-tests/agent-v2-incident-lab")
    args = parser.parse_args()
    if args.action == "verify":
        verify(json.loads((args.output_dir / "expected.json").read_text()))
        return
    expected = generate(args.output_dir)
    if args.action == "generate":
        print(json.dumps(expected, ensure_ascii=False, indent=2))
        return
    if query(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{DATABASE}'").strip():
        raise SystemExit(f"{DATABASE} already exists; refusing to overwrite. Use verify to inspect it.")
    with (args.output_dir / "dataset.sql").open() as sql:
        subprocess.run(["bash", str(MYSQL)], stdin=sql, check=True)
    verify(expected)


if __name__ == "__main__":
    main()
