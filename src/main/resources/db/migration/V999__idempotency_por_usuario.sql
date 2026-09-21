-- A chave idempotente é única dentro do usuário, não globalmente.
-- V017 criou o índice UNIQUE automático com o nome da coluna no MariaDB.
ALTER TABLE tb_pedidos
    DROP INDEX idempotency_key,
    ADD CONSTRAINT uk_pedido_usuario_idempotency UNIQUE (usuario_id, idempotency_key);
