-- Restore only this fixture's mutable case tables; finance and large-output fixtures remain unchanged.
USE agent_v2_lab;
START TRANSACTION;
UPDATE approval_sandbox SET amount=100.00,note='baseline' WHERE id=1;
UPDATE approval_sandbox SET amount=200.00,note='baseline' WHERE id=2;
DELETE FROM idempotency_probe;
COMMIT;
