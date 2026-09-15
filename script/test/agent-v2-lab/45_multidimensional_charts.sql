USE agent_v2_lab;

-- M01 Line / Column / Bar / AreaLine: xField=month, yField=revenue, groupBy=[region].
-- Column/Bar default to grouped bars. stack=true enables stacking for Column/Bar/AreaLine.
-- Four regions, six months. All groups together retain the baseline revenue 32265.51.
SELECT DATE_FORMAT(p.paid_at, '%Y-%m') AS month, c.region, SUM(p.amount) AS revenue
FROM payments p JOIN orders o ON o.id = p.order_id JOIN customers c ON c.id = o.customer_id
WHERE p.status = 'SUCCESS' AND p.paid_at >= '2026-01-01' AND p.paid_at < '2026-07-01'
GROUP BY DATE_FORMAT(p.paid_at, '%Y-%m'), c.region
ORDER BY month, c.region;

-- M02 Column: xField=month, yField=revenue, groupBy=[region,provider], stack=true.
-- Each region/provider tuple is a separate series; no SQL pivot is needed.
SELECT DATE_FORMAT(p.paid_at, '%Y-%m') AS month, c.region, p.provider, SUM(p.amount) AS revenue
FROM payments p JOIN orders o ON o.id = p.order_id JOIN customers c ON c.id = o.customer_id
WHERE p.status = 'SUCCESS' AND p.paid_at >= '2026-01-01' AND p.paid_at < '2026-07-01'
GROUP BY DATE_FORMAT(p.paid_at, '%Y-%m'), c.region, p.provider
ORDER BY month, c.region, p.provider;

-- M03 Combo: xField=month, groupBy=[region], stack=true.
-- series=[{field:revenue,chartType:Column,axisPosition:left},
--         {field:paid_orders,chartType:Line,axisPosition:right}].
-- Four revenue series share a stack; four order lines use the right axis without stacking.
SELECT DATE_FORMAT(p.paid_at, '%Y-%m') AS month, c.region,
       SUM(p.amount) AS revenue, COUNT(DISTINCT p.order_id) AS paid_orders
FROM payments p JOIN orders o ON o.id = p.order_id JOIN customers c ON c.id = o.customer_id
WHERE p.status = 'SUCCESS' AND p.paid_at >= '2026-01-01' AND p.paid_at < '2026-07-01'
GROUP BY DATE_FORMAT(p.paid_at, '%Y-%m'), c.region
ORDER BY month, c.region;

-- M04 Scatter: xField=order_count, yField=total_spend, groupBy=[region].
-- Customers with the same order count remain separate observations.
SELECT c.id AS customer_id, c.region, COUNT(p.id) AS order_count, SUM(p.amount) AS total_spend
FROM customers c JOIN orders o ON o.customer_id = c.id
JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCESS'
GROUP BY c.id, c.region ORDER BY c.id;
