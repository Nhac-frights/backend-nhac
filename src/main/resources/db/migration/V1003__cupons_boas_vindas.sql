ALTER TABLE tb_cupons ADD COLUMN usuario_id VARCHAR(50);
ALTER TABLE tb_cupons ADD COLUMN origem VARCHAR(30);
ALTER TABLE tb_cupons ADD CONSTRAINT fk_cupom_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuarios(id);
ALTER TABLE tb_cupons ADD CONSTRAINT uk_cupom_usuario_origem UNIQUE (usuario_id, origem);
ALTER TABLE tb_pedidos ADD COLUMN desconto DECIMAL(10,2) NOT NULL DEFAULT 0;
