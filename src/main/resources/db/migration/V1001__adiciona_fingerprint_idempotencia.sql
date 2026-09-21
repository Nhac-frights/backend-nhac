-- Fingerprint SHA-256 do request original. Nullable preserva pedidos
-- históricos criados antes desta versão.
ALTER TABLE tb_pedidos
    ADD COLUMN idempotency_fingerprint VARCHAR(64) NULL;
