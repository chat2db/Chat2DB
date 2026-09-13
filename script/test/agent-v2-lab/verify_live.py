#!/usr/bin/env python3
"""Read-only fixture assertions against the local MySQL test container."""
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent
result = subprocess.run(["bash", str(root / "mysql.sh"), "--batch", "--skip-column-names", "--raw"],
                        input=(root / "20_verify.sql").read_text(), text=True, capture_output=True)
if result.returncode:
    raise SystemExit(result.stderr)
checks = []
for line in result.stdout.splitlines():
    if not line.strip():
        continue
    name, passed, actual = line.split("\t", 2)
    checks.append({"check": name, "passed": passed == "1", "actual": actual})
if not checks:
    raise SystemExit("No verification rows returned")
failed = [check for check in checks if not check["passed"]]
expected = json.loads((root / "expected.json").read_text())
def rows(key, fields):
    return [[str(row[field]) for field in fields] for row in expected[key]]

chart_expected = {
    "C01": rows("monthly_paid", ["month", "revenue"]),
    "C02": rows("category_paid_item_revenue", ["category", "revenue"]),
    "C03": rows("monthly_paid", ["month", "revenue"]),
    "C04": rows("monthly_all_orders", ["month", "order_count"]),
    "C05": rows("region_paid_revenue", ["region", "revenue"]),
    "C06": rows("provider_success_count", ["provider", "payment_count"]),
    "C07": rows("category_paid_item_revenue", ["category", "revenue"]),
    "C08": rows("funnel", ["stage", "orders"]),
    "C09": rows("customer_scatter", ["customer_id", "order_count", "total_spend"]),
    "C10": [[expected["net_revenue"]]],
    "C11": rows("monthly_paid", ["month", "revenue", "paid_orders"]),
}
sections = re.split(r"(?m)^-- (C\d{2})[^\n]*\n", (root / "40_chart_queries.sql").read_text())
charts = []
for index in range(1, len(sections), 2):
    case = sections[index]
    sql = "\n".join(line for line in sections[index + 1].splitlines() if not line.startswith("--")).strip()
    response = subprocess.run(["bash", str(root / "mysql.sh"), "--batch", "--skip-column-names", "--raw",
                               "agent_v2_lab", "-e", sql], text=True, capture_output=True)
    if response.returncode:
        raise SystemExit(response.stderr)
    actual = [line.split("\t") for line in response.stdout.splitlines()]
    charts.append({"case": case, "rows": len(actual), "passed": actual == chart_expected[case]})
    if actual != chart_expected[case]:
        failed.append({"check": case, "passed": False, "actual": actual})
if len(charts) != 11:
    raise SystemExit("Expected 11 chart queries")
print(json.dumps({"read_only": True, "checks": len(checks), "charts": charts, "failed": failed}, ensure_ascii=False, indent=2))
raise SystemExit(bool(failed))
