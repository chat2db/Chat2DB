USE agent_v2_lab;

-- C01 Column. xField=month, yField=revenue. Expected: expected.json monthly_paid, 6 rows.
SELECT DATE_FORMAT(o.created_at,'%Y-%m') AS month, SUM(p.amount) AS revenue
FROM orders o JOIN payments p ON p.order_id=o.id AND p.status='SUCCESS'
GROUP BY DATE_FORMAT(o.created_at,'%Y-%m') ORDER BY month;

-- C02 Bar. xField=category, yField=revenue. 4 rows, category_paid_item_revenue.
SELECT pr.category, SUM(i.line_amount) AS revenue
FROM order_items i JOIN products pr ON pr.id=i.product_id
JOIN payments p ON p.order_id=i.order_id AND p.status='SUCCESS'
GROUP BY pr.category ORDER BY FIELD(pr.category,'数码配件','家居生活','运动户外','办公文具');

-- C03 Line. xField=month, yField=revenue. 6 rows, monthly_paid.
SELECT DATE_FORMAT(o.created_at,'%Y-%m') AS month, SUM(p.amount) AS revenue
FROM orders o JOIN payments p ON p.order_id=o.id AND p.status='SUCCESS'
GROUP BY DATE_FORMAT(o.created_at,'%Y-%m') ORDER BY month;

-- C04 AreaLine. xField=month, yField=order_count. 6 rows, monthly_all_orders.
SELECT DATE_FORMAT(created_at,'%Y-%m') AS month, COUNT(*) AS order_count
FROM orders GROUP BY DATE_FORMAT(created_at,'%Y-%m') ORDER BY month;

-- C05 Pie. xField=region, yField=revenue. 4 rows, region_paid_revenue.
SELECT c.region, SUM(p.amount) AS revenue
FROM customers c JOIN orders o ON o.customer_id=c.id
JOIN payments p ON p.order_id=o.id AND p.status='SUCCESS'
GROUP BY c.region ORDER BY FIELD(c.region,'华东','华南','华北','西南');

-- C06 RingPie. xField=provider, yField=payment_count. 3 rows, provider_success_count.
SELECT provider, COUNT(*) AS payment_count FROM payments WHERE status='SUCCESS' GROUP BY provider ORDER BY provider;

-- C07 RosePie. xField=category, yField=revenue. 4 rows, same financial definition as C02.
SELECT pr.category, SUM(i.line_amount) AS revenue
FROM order_items i JOIN products pr ON pr.id=i.product_id
JOIN payments p ON p.order_id=i.order_id AND p.status='SUCCESS'
GROUP BY pr.category ORDER BY FIELD(pr.category,'数码配件','家居生活','运动户外','办公文具');

-- C08 Funnel. xField=stage, yField=orders. Order is material: 324 -> 259 -> 226 -> 160.
SELECT stage, orders FROM (
 SELECT '创建订单' AS stage, COUNT(*) AS orders, 1 AS stage_order FROM orders
 UNION ALL SELECT '支付成功',COUNT(*),2 FROM payments WHERE status='SUCCESS'
 UNION ALL SELECT '已经发货',COUNT(*),3 FROM orders WHERE status IN ('SHIPPED','COMPLETED')
 UNION ALL SELECT '交易完成',COUNT(*),4 FROM orders WHERE status='COMPLETED'
) funnel ORDER BY stage_order;

-- C09 Scatter. xField=order_count, yField=total_spend. 48 rows, customer_scatter.
SELECT c.id AS customer_id, COUNT(p.id) AS order_count, SUM(p.amount) AS total_spend
FROM customers c JOIN orders o ON o.customer_id=c.id
JOIN payments p ON p.order_id=o.id AND p.status='SUCCESS'
GROUP BY c.id ORDER BY c.id;

-- C10 Statistics. yField=net_revenue; do not set xField. Exactly one row.
SELECT (SELECT SUM(amount) FROM payments WHERE status='SUCCESS')-
       (SELECT SUM(amount) FROM refunds WHERE status='SUCCESS') AS net_revenue;

-- C11 Combo. xField=month. series=[{field:'revenue',chartType:'Column',axisPosition:'left'},
-- {field:'paid_orders',chartType:'Line',axisPosition:'right'}]. Expected monthly_paid, 6 rows.
SELECT DATE_FORMAT(o.created_at,'%Y-%m') AS month, SUM(p.amount) AS revenue, COUNT(p.id) AS paid_orders
FROM orders o JOIN payments p ON p.order_id=o.id AND p.status='SUCCESS'
GROUP BY DATE_FORMAT(o.created_at,'%Y-%m') ORDER BY month;
