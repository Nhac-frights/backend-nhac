# Backend Nhac - API de Delivery e Gestão de Lojas

Backend desenvolvido em Java 25 com Spring Boot 4 / MariaDB / JPA / Flyway para a plataforma Nhac (aplicativo de delivery e painel web do lojista).

## Documentação dos Endpoints & Guia de Integração

A Swagger UI interativa está disponível em `/swagger-ui/index.html` ou `/v3/api-docs`.

> **Perfis:** desenvolvimento usa o perfil `dev` por padrão. Em produção use `SPRING_PROFILES_ACTIVE=prod`; o perfil `prod` exige secrets de banco/JWT/pagamentos por variáveis de ambiente e habilita validação estrita do Flyway.

---

### 1. Autenticação (`/api/v1/auth`)

- `POST /api/v1/auth/login`: Autentica usuário com e-mail e senha. Retorna JWT token (`LoginResponseDTO`).
- `POST /api/v1/auth/registrar`: Cadastra um novo usuário. Retorna `LoginResponseDTO` com token imediatamente válido.
- `POST /api/v1/auth/login-sms`: Autenticação via código SMS (Twilio).
- `POST /api/v1/auth/google`: Login social via Google OAuth2.
- `POST /api/v1/auth/enviar-codigo-cadastro`: Envio de código de verificação para e-mail.
- **Rate Limiting**: Endpoints sensíveis de login e envio de códigos possuem rate limiting (máximo de 10 tentativas por janela de 15 minutos por IP/usuário), retornando HTTP 429 (`TENTATIVAS_LOGIN_EXCEDIDAS`).
- **Expiração de Token**: Tokens JWT possuem tempo de expiração fixo sem refresh token. Clientes devem redirecionar para a tela de login ao receber HTTP 401.

---

### 2. Lojas (`/api/v1/lojas`)

- `GET /api/v1/lojas`: Listagem paginada e busca com filtros geoespaciais e de nome.
- `GET /api/v1/lojas/{id}`: Detalhes completos de uma loja aberta por ID.
- `GET /api/v1/lojas/minha-loja`: **Rota canônica para o painel do lojista.** Retorna a loja do usuário autenticado (HTTP 200). Se o usuário não tiver loja, retorna HTTP 404 (`LojaNaoEncontradaException`).
- `POST /api/v1/lojas`: Criação de loja vinculada ao usuário autenticado. Promove o usuário de `CLIENTE` para `LOJISTA`. O campo `usuarioId` no body é ignorado por segurança.
- `PUT /api/v1/lojas/{id}`: Atualização completa dos dados da loja. Restrito ao dono da loja ou `ADMIN` (HTTP 403 para outros usuários). O dono original (`usuarioId`) é preservado.
- `POST /api/v1/lojas/{id}/calcular-frete`: Cálculo de frete e tempo estimado com base nas coordenadas.

---

### 3. Painel do Lojista (`/api/v1/lojista`)

Rotas simplificadas para o painel web, onde a loja é inferida diretamente pelo token do usuário:

- `GET /api/v1/lojista/produtos`: Lista todos os produtos da loja do usuário autenticado (incluindo ativos e inativos). Suporta filtros opcionais `categoriaMenu`, `nome`, e paginação com `size` máximo de 100. Retorna página vazia (HTTP 200) caso o usuário não tenha loja/produtos.
- `GET /api/v1/lojista/pedidos`: Lista os pedidos recebidos pelas lojas do usuário autenticado, ordenados do mais recente para o mais antigo. Suporta filtro por `status` (valores aceitos: `PENDENTE`, `PAGO`, `PREPARANDO`, `SAIU_ENTREGA`, `ENTREGUE`, `CANCELADO`). Valores inválidos retornam HTTP 400.

---

### 4. Produtos (`/api/v1/produtos`)

- `GET /api/v1/produtos`: Catálogo público paginado com filtros (`lojaId`, `categoriaMenu`, `nome`, `precoMaximo`).
- `GET /api/v1/produtos/{produtoId}`: Detalhes de um produto ativo por ID.
- `POST /api/v1/produtos`: Cadastro de novo produto para a loja do lojista autenticado. Valida `percentualDesconto` entre 0 e 100 (`@Min(0) @Max(100)`).
- `PUT /api/v1/produtos/{produtoId}`: Edição de produto. Exige que o produto pertença à loja do lojista autenticado (ou `ADMIN`).
- `DELETE /api/v1/produtos/{produtoId}`: Desativação lógica do produto (Soft Delete).
- `PATCH /api/v1/produtos/{produtoId}/ativar`: Reativação do produto para voltar a aparecer no catálogo público.
- `GET /api/v1/produtos/{produtoId}/avaliacoes/resumo`: Média de notas e total de avaliações.

---

### 5. Pedidos (`/api/v1/pedidos`)

- `POST /api/v1/pedidos`: Criação de pedido a partir do carrinho.
  - **Idempotência**: Suporta header `Idempotency-Key`. Ao reenviar a mesma chave, a API devolve o pedido já criado com HTTP 200 (em vez de 201).
  - **Estoque**: Decremento atômico condicional no banco de dados (`UPDATE ... WHERE estoque >= quantidade`).
  - **Status Serializado**: O campo `status` no JSON é sempre a string em caixa alta do enum: `"PENDENTE"`, `"PAGO"`, `"PREPARANDO"`, `"SAIU_ENTREGA"`, `"ENTREGUE"`, `"CANCELADO"`.
- `GET /api/v1/pedidos/{id}`: Detalhes do pedido (apenas pelo cliente que realizou a compra).
- `GET /api/v1/pedidos`: Listagem paginada dos pedidos do cliente autenticado.
- `PATCH /api/v1/pedidos/{id}/status`: Atualização do status do pedido. Restrita ao lojista dono da loja do pedido ou `ADMIN`. Transições inválidas retornam HTTP 409 (`TRANSICAO_STATUS_INVALIDA`).
- `PATCH /api/v1/pedidos/{id}/cancelar`: Cancelamento do pedido pelo cliente (apenas no status `PENDENTE`, antes da confirmação do pagamento).

---

### 6. Padrão de Erros (`ErroPadraoDTO`)

Todas as exceções tratadas retornam o formato padrão:

```json
{
  "requestId": "uuid-da-requisicao",
  "timestamp": "2026-09-10T10:00:00Z",
  "status": 400,
  "errorCode": "VALIDACAO_FALHOU",
  "title": "Erro de Validação de Dados",
  "message": "Alguns campos enviados são inválidos.",
  "details": {
    "preco": "O preço não pode ser negativo."
  },
  "path": "/api/v1/produtos",
  "sugestoes": ["Verifique os campos informados e tente novamente."]
}
```

### Testes de integração e concorrência

A suíte usa H2 para integrações rápidas e MariaDB via Testcontainers para cenários em que a semântica real do banco importa, como locking, concorrência e constraints. O mvn verify também executa um teste que aplica todas as migrations Flyway em um MariaDB limpo.

### Cupom de boas-vindas

O cliente autenticado pode resgatar uma vez por conta em
`POST /api/v1/cupons/boas-vindas`, listar em `GET /api/v1/cupons` e validar a
prévia em `POST /api/v1/cupons/validar` com `cupomId` e `subtotal`.
O checkout recebe `cupomId` e recalcula o desconto usando os preços do banco.
Uso e criação do pedido são transacionais; replays não gastam outro uso e
cancelamentos devolvem o cupom sem renovar sua validade.

Configuração (valores padrão):

```properties
nhac.cupom.boas-vindas.valor=5.00
nhac.cupom.boas-vindas.minimo=25.00
nhac.cupom.boas-vindas.validade-dias=30
```

Os valores devem ser positivos e o mínimo deve superar o desconto. Novas
configurações afetam apenas resgates futuros. A migração V1003 adiciona o dono
e a origem do cupom, unicidade por conta/origem e o desconto registrado no pedido.

### Imagem do CI

O job `docker` publica o nome normalizado em minúsculas como output
`image-name`. O Trivy usa esse mesmo nome com o digest do build e autenticação
GHCR de leitura. O upload SARIF só roda quando o scanner produziu o arquivo;
um erro do scanner continua fazendo o job falhar.
