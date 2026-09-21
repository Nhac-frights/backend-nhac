-- Optimistic locking do agregado Pedido para impedir transições concorrentes
-- incompatíveis (ex.: cancelar x coletar, cancelar x preparar).
ALTER TABLE tb_pedidos
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
