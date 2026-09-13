-- CASE CATALOG ONLY. Do not execute this whole file: approval and stop/continue cases require UI observation.
USE agent_v2_lab;

-- O01 2 MiB UTF8 TEXT; full output has NEEDLE_TEXT_TAIL_9F2A beyond its preview.
SELECT id, title, body FROM output_documents WHERE id=1;

-- O02 JSON with 2 MiB $.body. Preserve JSON rather than serializing an object as a display summary.
SELECT id, CAST(payload AS CHAR CHARACTER SET utf8mb4) AS payload FROM output_documents WHERE id=2;

-- O03 600 rows; call db_query with pageSize=200 and page=1,2,3, not SQL LIMIT 200 for all pages.
SELECT id, level, message FROM event_log ORDER BY id;

-- O04 Multiple statements: first and third small; second large; preserve three statement outcomes and result references.
SELECT 'first-small' AS marker;
SELECT id, body FROM output_documents WHERE id=1;
SELECT 42 AS last_value;

-- O05 Exact decimal strings / NULL / duplicate names. Two columns intentionally share one label.
SELECT text_value AS duplicate_name, exact_amount AS duplicate_name FROM value_edges WHERE id=1;
SELECT id,text_value,exact_amount,happened_at,HEX(binary_value) AS binary_hex FROM value_edges ORDER BY id;

-- O06 Same display labels must retain row identity; trailing spaces, empty string and NULL differ.
SELECT id,text_value,CHAR_LENGTH(text_value) AS characters,text_value IS NULL AS is_sql_null FROM value_edges ORDER BY id;

-- O07 Multiline text and control-character preservation (JSONL physical lines differ from body logical lines).
SELECT id,body FROM output_documents WHERE id IN (3,4) ORDER BY id;

-- S01 Resolve same table name using the intended datasource+database scope.
SELECT 'main' AS source_scope,COUNT(*) AS row_count FROM agent_v2_lab.orders
UNION ALL SELECT 'scope',COUNT(*) FROM agent_v2_scope_lab.orders;
SELECT id,customer_name,scope_marker FROM agent_v2_scope_lab.customers ORDER BY id;

-- A01 Read-only should execute without write approval. Exactly 100.00, baseline after reset.
SELECT id,amount,note FROM approval_sandbox WHERE id=1;

-- A02 First deny, confirm baseline; then approve and verify amount remains 100.00 while note changes.
UPDATE approval_sandbox SET note='approved_once' WHERE id=1;
SELECT id,amount,note FROM approval_sandbox WHERE id=1;

-- A03 Approved transaction rollback must leave both rows unchanged (100.00 / 200.00).
START TRANSACTION;
UPDATE approval_sandbox SET amount=amount+10 WHERE id=1;
UPDATE approval_sandbox SET amount=amount-10 WHERE id=2;
ROLLBACK;
SELECT id,amount,note FROM approval_sandbox ORDER BY id;

-- A04 Run AFTER 30_reset_cases.sql. Autocommit partial-failure batch; do not auto-replay.
-- Expected: first UPDATE persists 101.00; middle SELECT fails; final UPDATE is not executed, id=2 stays 200.00.
UPDATE approval_sandbox SET amount=amount+1 WHERE id=1;
SELECT missing_column_that_does_not_exist FROM approval_sandbox;
UPDATE approval_sandbox SET amount=amount+100 WHERE id=2;

-- A05 Execute this exact idempotent statement twice WITH approval; only one row, attempts=1.
-- This validates explicit SQL idempotence, not automatic Agent deduplication of arbitrary writes.
INSERT INTO idempotency_probe(event_key,payload,attempts) VALUES('agent-v2-lab:once','fixed-payload',1)
ON DUPLICATE KEY UPDATE payload='fixed-payload';
SELECT event_key,payload,attempts FROM idempotency_probe;
