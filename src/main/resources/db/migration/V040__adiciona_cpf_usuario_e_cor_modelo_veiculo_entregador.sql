-- CPF é do usuário (pessoa), não do veículo — reaproveitável se o mesmo
-- usuário vier a atuar como lojista/cliente também. Nullable porque nem
-- todo usuário existente é entregador; único só quando preenchido.
ALTER TABLE tb_usuarios
    ADD COLUMN cpf VARCHAR(14) NULL;

ALTER TABLE tb_usuarios
    ADD CONSTRAINT uk_usuarios_cpf UNIQUE (cpf);

-- Cor e modelo do veículo: pedidos hoje na tela de edição do app, mas sem
-- nenhuma coluna pra guardar. Nullable pelo mesmo motivo (cadastros antigos).
ALTER TABLE tb_entregadores
    ADD COLUMN cor_veiculo VARCHAR(30) NULL;

ALTER TABLE tb_entregadores
    ADD COLUMN modelo_veiculo VARCHAR(60) NULL;
