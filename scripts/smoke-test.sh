#!/usr/bin/env bash
# =============================================================================
# smoke-test.sh — Bate TODOS os endpoints do backend-nhac em ordem,
#                 encadeando IDs entre chamadas, e gera relatório.
#
# Uso:
#   ./scripts/smoke-test.sh                        # usa defaults
#   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
#   ./scripts/smoke-test.sh --verbose              # mostra body de cada resposta
#   ./scripts/smoke-test.sh --only auth,produtos   # roda só grupos específicos
# =============================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
VERBOSE=0
ONLY=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --verbose) VERBOSE=1; shift ;;
    --only)    ONLY="$2"; shift 2 ;;
    *) echo "Opção desconhecida: $1"; exit 1 ;;
  esac
done

# -----------------------------------------------------------------------------
# Cores
# -----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_CYAN=$'\033[36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_CYAN=""; C_GRAY=""; C_BOLD=""; C_RST=""
fi

# -----------------------------------------------------------------------------
# Contadores
# -----------------------------------------------------------------------------
PASS=0; FAIL=0; SKIP=0; TOTAL=0
declare -a FALHAS=()

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
log_grupo() {
  echo
  echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
  echo -e "${C_BOLD}${C_CYAN} $1${C_RST}"
  echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
}

# testar <nome> <metodo> <path> <esperados_csv> <data_ou_-> [extra_curl_args...]
# Exemplo: testar "Login" POST "/api/v1/auth/login" "200,201" '{"email":"..."}'
testar() {
  local nome="$1"; local metodo="$2"; local path="$3"
  local esperados="$4"; local body="$5"; shift 5 || true

  TOTAL=$((TOTAL + 1))
  local url="${BASE_URL}${path}"

  # ── Coleta args extras primeiro pra saber se já trazem Authorization ──
  local -a extra_args=("$@")
  local tem_auth=0
  for arg in "${extra_args[@]}"; do
    if [[ "$arg" == *"Authorization:"* ]]; then
      tem_auth=1
      break
    fi
  done

  local -a curl_args=(-s -o /tmp/smoke_body -w '%{http_code}' -X "$metodo" "$url")

  # ── SÓ injeta o TOKEN default se os extras NÃO passaram um Authorization ──
  if [[ $tem_auth -eq 0 && -n "${TOKEN:-}" ]]; then
    curl_args+=(-H "Authorization: Bearer ${TOKEN}")
  fi
  curl_args+=(-H "Content-Type: application/json")

  if [[ "$body" != "-" && -n "$body" ]]; then
    curl_args+=(-d "$body")
  fi

  # Adiciona os extras
  for arg in "${extra_args[@]}"; do
    curl_args+=("$arg")
  done

  # ── Executa ──
  local code
  code=$(curl "${curl_args[@]}" 2>/dev/null || echo "000")


  local esperado_ok=0
  IFS=',' read -ra arr <<< "$esperados"
  for e in "${arr[@]}"; do
    [[ "$code" == "$e" ]] && esperado_ok=1 && break
  done

  if [[ $esperado_ok -eq 1 ]]; then
    PASS=$((PASS + 1))
    printf "  ${C_GREEN}✓${C_RST} %-6s %-55s ${C_GRAY}[%s]${C_RST}\n" "$metodo" "$path" "$code"
  else
    FAIL=$((FAIL + 1))
    FALHAS+=("${metodo} ${path}  →  esperado ${esperados}, recebeu ${code}")
    printf "  ${C_RED}✗${C_RST} %-6s %-55s ${C_RED}[%s] esperado %s${C_RST}\n" \
      "$metodo" "$path" "$code" "$esperados"
  fi

  if [[ $VERBOSE -eq 1 ]]; then
    echo -e "${C_GRAY}    ↳ $(head -c 400 /tmp/smoke_body 2>/dev/null)${C_RST}"
  fi

  # Guarda corpo para extração
  cp /tmp/smoke_body "/tmp/smoke_last_body" 2>/dev/null || true
}

# Atalho: extrai campo JSON do último body
extrair() {
  jq -r "$1 // empty" /tmp/smoke_last_body 2>/dev/null
}

deve_rodar() {
  [[ -z "$ONLY" ]] && return 0
  IFS=',' read -ra arr <<< "$ONLY"
  for g in "${arr[@]}"; do [[ "$g" == "$1" ]] && return 0; done
  return 1
}

# -----------------------------------------------------------------------------
# 0. SANITY — backend está no ar?
# -----------------------------------------------------------------------------
echo
echo -e "${C_BOLD}🔎 Verificando backend em ${BASE_URL}...${C_RST}"

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/v3/api-docs" 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "000" ]]; then
  echo -e "${C_RED}✗ Backend inacessível.${C_RST}"
  echo "  Confirme que o app está rodando e que BASE_URL está correto."
  echo "  Dica: SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run"
  exit 1
fi
echo -e "${C_GREEN}✓ Backend respondendo (${HTTP_CODE})${C_RST}"

TOKEN=""
TOKEN_LOJISTA=""
USUARIO_ID=""
LOJISTA_ID=""
LOJA_ID=""
LOJA_ID_LOJISTA=""
PRODUTO_ID=""
PEDIDO_ID=""
ENDERECO_ID=""
FUNCIONARIO_ID=""
CONVERSA_ID=""
AVALIACAO_ID=""
CUPOM_ID=""


# =============================================================================
# GRUPO 1 — AUTENTICAÇÃO (público)
# =============================================================================
if deve_rodar auth; then
log_grupo "1/13  AUTENTICAÇÃO"

# checar-email
testar "checar-email" POST "/api/v1/auth/checar-email" "200" \
  '{"email":"cliente@nhac.com"}'

testar "enviar-codigo-cadastro"  POST "/api/v1/auth/enviar-codigo-cadastro"  "200,202,204,400,404,503" '{"email":"naoexiste@teste.com"}'
testar "esqueci-senha/email"     POST "/api/v1/auth/esqueci-senha/email"     "200,202,204,400,503"     '{"email":"cliente@nhac.com"}'

# login cliente (default do DataLoader)
testar "login (cliente)" POST "/api/v1/auth/login" "200" \
  '{"email":"cliente@nhac.com","senha":"senha123"}'

TOKEN=$(extrair '.token // .accessToken // .access_token')
USUARIO_ID=$(extrair '.usuarioId // .usuario.id // .id // empty')


if [[ -z "$TOKEN" ]]; then
  echo -e "${C_YELLOW}⚠ Não consegui extrair token. Ajuste o 'extrair' pro shape real da resposta.${C_RST}"
  echo -e "${C_GRAY}   Body: $(head -c 300 /tmp/smoke_last_body)${C_RST}"
fi

# login lojista
testar "login (lojista)" POST "/api/v1/auth/login" "200" \
  '{"email":"lojista@nhac.com","senha":"lojista123"}'
TOKEN_LOJISTA=$(extrair '.token // .accessToken // .access_token')
LOJISTA_ID=$(extrair '.usuario.id // .user.id // .id // empty')
# Logo após "TOKEN_LOJISTA=$(extrair '.token ...')"
LOJA_ID_LOJISTA=$(curl -s -H "Authorization: Bearer ${TOKEN_LOJISTA}" \
  "${BASE_URL}/api/v1/lojas/minha-loja" | jq -r '.id // empty')

# login social (vai falhar sem provider real — só verifica o shape da rota)
testar "login social (espera 4xx)" POST "/api/v1/auth/social" "400,401,422" \
  '{"provider":"GOOGLE","token":"fake"}'

# esqueci senha


# redefinir senha (com código inválido — só pra ver rota aceitar)
testar "redefinir-senha/email" POST "/api/v1/auth/redefinir-senha/email" "400,401,422" \
  '{"email":"cliente@nhac.com","codigo":"000000","novaSenha":"novaSenha123"}'

# confirmar email cadastro
testar "confirmar-email-cadastro" POST "/api/v1/auth/confirmar-email-cadastro" "400,401,422" \
  '{"email":"x@x.com","codigo":"000000"}'

# login SMS (com código inválido)
testar "login-sms" POST "/api/v1/auth/login-sms" "400,401,422" \
  '{"telefone":"11999998888","codigo":"000000"}'

# alterar senha (autenticado)
testar "alterar-senha" PUT "/api/v1/auth/alterar-senha" "200,400,401,422" \
  '{"senhaAtual":"senha123","novaSenha":"senha123"}'  # mesma senha = teste não destrutivo
fi

# =============================================================================
# GRUPO 2 — USUÁRIOS
# =============================================================================
if deve_rodar usuarios; then
log_grupo "2/13  USUÁRIOS"

testar "criar usuario" POST "/api/v1/usuarios" "200,201,400,409" \
  '{"nome":"Smoke Test","email":"smoketest@nhac.com","telefone":"11999990000","senha":"senha123"}'

if [[ -n "$USUARIO_ID" ]]; then
  testar "buscar usuario"      GET "/api/v1/usuarios/${USUARIO_ID}"                "200"         "-"
  testar "atualizar usuario"   PUT "/api/v1/usuarios/${USUARIO_ID}"                "200"         '{"nome":"Matheus Smoke"}'
  testar "estatisticas"        GET "/api/v1/usuarios/${USUARIO_ID}/estatisticas"   "200"         "-"
  testar "pedidos do usuario"  GET "/api/v1/usuarios/${USUARIO_ID}/pedidos?page=0&size=10" "200" "-"
  testar "prefs notif (GET)"   GET "/api/v1/usuarios/${USUARIO_ID}/preferencias-notificacao" "200" "-"
  testar "prefs notif (PUT)"   PUT "/api/v1/usuarios/${USUARIO_ID}/preferencias-notificacao" "200" \
    '{"notificarNovoPedido":true,"notificarMensagens":true,"notificarAvaliacoes":false,"notificarNovidades":false}'
fi
fi

# =============================================================================
# GRUPO 3 — ENDEREÇOS
# =============================================================================
if deve_rodar enderecos; then
log_grupo "3/13  ENDEREÇOS"

if [[ -n "$USUARIO_ID" ]]; then
  testar "listar enderecos" GET "/api/v1/usuarios/${USUARIO_ID}/enderecos" "200" "-"

  testar "criar endereco" POST "/api/v1/usuarios/${USUARIO_ID}/enderecos" "200,201" \
    '{"rua":"Rua Teste","numero":"100","bairro":"Centro","cidade":"São Paulo","estado":"SP","cep":"01000-000","complemento":"","isPadrao":true}'
  ENDERECO_ID=$(extrair '.id // .enderecoId // empty')

  if [[ -n "$ENDERECO_ID" ]]; then
    testar "atualizar endereco" PUT "/api/v1/usuarios/${USUARIO_ID}/enderecos/${ENDERECO_ID}" "200" \
      '{"rua":"Rua Teste 2","numero":"200","bairro":"Centro","cidade":"São Paulo","estado":"SP","cep":"01000-000"}'
    testar "remover endereco"   DELETE "/api/v1/usuarios/${USUARIO_ID}/enderecos/${ENDERECO_ID}" "200,204" "-"
  fi
fi
fi

# =============================================================================
# GRUPO 4 — LOJAS
# =============================================================================
if deve_rodar lojas; then
log_grupo "4/13  LOJAS"

testar "listar lojas" GET "/api/v1/lojas?page=0&size=10" "200" "-"
LOJA_ID=$(extrair '.content[0].id // .[0].id // empty')

if [[ -z "$LOJA_ID" ]]; then
  echo -e "${C_YELLOW}⚠ Nenhuma loja encontrada no banco. Rode o DataLoader com profile dev.${C_RST}"
fi

if [[ -n "$LOJA_ID" ]]; then
  testar "detalhes da loja" GET "/api/v1/lojas/${LOJA_ID}" "200" "-"
  testar "criar loja (com token lojista)" POST "/api/v1/lojas" "200,201,400,409,403" \
    '{"nome":"Smoke Loja","descricao":"Teste","categoria":"Lanches","endereco":{"rua":"X","numero":"1","cidade":"São Paulo","estado":"SP","cep":"01000-000"}}' \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "minha-loja" GET "/api/v1/lojas/minha-loja" "200,401,403,404" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "calcular frete" POST "/api/v1/lojas/${LOJA_ID}/calcular-frete" "200,400,422" \
    '{"cep":"01000-000","lat":-23.55,"lng":-46.63}'
fi
fi

# =============================================================================
# GRUPO 5 — PRODUTOS
# =============================================================================
if deve_rodar produtos; then
log_grupo "5/13  PRODUTOS"

testar "listar produtos" GET "/api/v1/produtos?page=0&size=10" "200" "-"
PRODUTO_NOVO_ID=$(extrair '.id // empty')

if [[ -n "$PRODUTO_NOVO_ID" ]]; then
  testar "editar produto"     PUT   "/api/v1/produtos/${PRODUTO_NOVO_ID}"       "200" \
    '{"nome":"Smoke Editado","descricao":"editado","preco":29.90,"categoriaMenu":"Lanches","estoque":20}' \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "ajustar estoque"    PATCH "/api/v1/produtos/${PRODUTO_NOVO_ID}/estoque" "200" \
    '{"estoque":50}' -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "reativar produto"   PATCH "/api/v1/produtos/${PRODUTO_NOVO_ID}/ativar"  "200" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "desativar produto"  DELETE "/api/v1/produtos/${PRODUTO_NOVO_ID}"       "200,204" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi
fi

# =============================================================================
# GRUPO 6 — LOJISTA (painel)
# =============================================================================
if deve_rodar lojista; then
log_grupo "6/13  LOJISTA"

testar "listar produtos loja" GET  "/api/v1/lojista/produtos?page=0&size=10" "200,403" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
if [[ -n "$PRODUTO_ID" ]]; then
  testar "buscar produto loja" GET "/api/v1/lojista/produtos/${PRODUTO_ID}"  "200,403,404" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi
testar "listar pedidos loja"   GET  "/api/v1/lojista/pedidos?page=0&size=10" "200,403" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
testar "painel lojista"        GET  "/api/v1/lojista/painel"                 "200,403" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
testar "financeiro lojista (default)" GET "/api/v1/lojista/financeiro" "200" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
testar "conversas lojista"     GET  "/api/v1/lojista/conversas?page=0&size=10" "200,403" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
testar "marcar conversa lida"  PATCH "/api/v1/lojista/conversas/000/lida"      "200,204,403,404" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi

# =============================================================================
# GRUPO 7 — FUNCIONÁRIOS
# =============================================================================
if deve_rodar funcionarios; then
log_grupo "7/13  FUNCIONÁRIOS"

testar "listar funcionarios" GET "/api/v1/lojista/funcionarios?page=0&size=10" "200,403" "-" \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"

# Gera email único por execução para não colidir com runs anteriores
FUNC_EMAIL="smokefunc.$(date +%s)@nhac.com"

testar "criar funcionario" POST "/api/v1/lojista/funcionarios" "201" \
  '{"nome":"Smoke Func","email":"'"${FUNC_EMAIL}"'","telefone":"+5511999998877","cargo":"Atendente","senha":"senha123"}' \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
FUNCIONARIO_ID=$(extrair '.id // empty')

if [[ -n "$FUNCIONARIO_ID" ]]; then
  testar "editar funcionario"   PUT   "/api/v1/lojista/funcionarios/${FUNCIONARIO_ID}"        "200" \
    '{"nome":"Smoke Func Edit","telefone":"+5511999998877","cargo":"Gerente"}' \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "reativar funcionario" PATCH "/api/v1/lojista/funcionarios/${FUNCIONARIO_ID}/ativar" "200" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "desativar funcionario" DELETE "/api/v1/lojista/funcionarios/${FUNCIONARIO_ID}"       "204" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
else
  echo -e "  ${C_YELLOW}⚠ Pulando editar/reativar/desativar: FUNCIONARIO_ID vazio${C_RST}"
fi
fi

# =============================================================================
# GRUPO 8 — PEDIDOS
# =============================================================================
if deve_rodar pedidos; then
log_grupo "8/13  PEDIDOS"

testar "listar meus pedidos" GET "/api/v1/pedidos?page=0&size=10" "200" "-"

if [[ -n "$PRODUTO_ID" && -n "$LOJA_ID" ]]; then
  PEDIDO_BODY=$(cat <<JSON
{
  "lojaId": "${LOJA_ID}",
  "formaPagamento": "PIX",
  "observacao": "smoke test",
  "enderecoEntrega": {
    "rua": "Rua Teste",
    "numero": "100",
    "bairro": "Centro",
    "cidade": "São Paulo",
    "estado": "SP",
    "cep": "01000-000"
  },
  "itens": [
    { "produtoId": "${PRODUTO_ID}", "quantidade": 1 }
  ]
}
JSON
)
  testar "criar pedido" POST "/api/v1/pedidos" "200,201,400,403,422" \
    "$PEDIDO_BODY" -H "Idempotency-Key: smoke-$(date +%s)"
  PEDIDO_ID=$(extrair '.id // empty')
fi


if [[ -n "$PEDIDO_ID" ]]; then
  testar "consultar pedido"   GET   "/api/v1/pedidos/${PEDIDO_ID}"             "200" "-"
  testar "atualizar status"   PATCH "/api/v1/pedidos/${PEDIDO_ID}/status"      "200,403" \
    '{"status":"PREPARANDO"}'
  testar "cancelar pedido"    PATCH "/api/v1/pedidos/${PEDIDO_ID}/cancelar"    "200,400,403" "-"
fi
fi

# =============================================================================
# GRUPO 9 — FAVORITOS / SEGUINDO
# =============================================================================
if deve_rodar favoritos; then
log_grupo "9/13  FAVORITOS E SEGUINDO"

testar "listar favoritos" GET "/api/v1/favoritos?page=0&size=10" "200" "-"

if [[ -n "$LOJA_ID" ]]; then
  testar "favoritar" POST "/api/v1/favoritos" "200,201,400,409" \
    '{"lojaId":"'"$LOJA_ID"'"}'
  testar "contagem seguidores" GET "/api/v1/favoritos/lojas/${LOJA_ID}/contagem" "200" "-"
  testar "remover favorito"    DELETE "/api/v1/favoritos/${LOJA_ID}"             "200,204" "-"
fi

if [[ -n "$USUARIO_ID" && -n "$LOJA_ID" ]]; then
  testar "verificar segue"     GET    "/api/v1/usuarios/${USUARIO_ID}/seguindo/${LOJA_ID}" "200" "-"
  testar "seguir loja"         POST   "/api/v1/usuarios/${USUARIO_ID}/seguindo/${LOJA_ID}" "200,201,204" "-"
  testar "parar de seguir"     DELETE "/api/v1/usuarios/${USUARIO_ID}/seguindo/${LOJA_ID}" "200,204" "-"
fi
fi

# =============================================================================
# GRUPO 10 — AVALIAÇÕES
# =============================================================================
if deve_rodar avaliacoes; then
log_grupo "10/13  AVALIAÇÕES"

if [[ -n "$LOJA_ID" ]]; then
  testar "listar avaliacoes loja" GET "/api/v1/lojas/${LOJA_ID}/avaliacoes?page=0&size=10" "200" "-"
fi

if [[ -n "$PEDIDO_ID" ]]; then
  testar "criar avaliacao" POST "/api/v1/avaliacoes" "200,201,400,403,409,422" \
    '{"pedidoId":"'"$PEDIDO_ID"'","nota":5,"comentario":"Smoke test top!"}'
  AVALIACAO_ID=$(extrair '.id // empty')
fi
fi

# =============================================================================
# GRUPO 11 — CONVERSAS
# =============================================================================
if deve_rodar conversas; then
log_grupo "11/13  CONVERSAS"

if [[ -n "$LOJA_ID" ]]; then
  testar "abrir conversa" POST "/api/v1/conversas/lojas/${LOJA_ID}" "200,201,403,404" "-"
  CONVERSA_ID=$(extrair '.id // empty')
fi

testar "abrir conversa (cliente)" POST "/api/v1/conversas/lojas/loja_burger_002" "200,201" "-"
CONVERSA_ID=$(cat /tmp/smoke_last_body | tr -d '"' | tr -d '\n')

if [[ -n "$CONVERSA_ID" ]]; then
  testar "marcar conversa como lida (cliente)" PATCH "/api/v1/conversas/${CONVERSA_ID}/lida" "204" "-"
fi

if [[ -n "$CONVERSA_ID" ]]; then
  testar "historico mensagens" GET "/api/v1/lojista/conversas/${CONVERSA_ID}/mensagens?page=0&size=10" "200,403" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi
fi

# =============================================================================
# GRUPO 12 — VERIFICAÇÃO TELEFONE
# =============================================================================
if deve_rodar verificacao; then
log_grupo "12/13  VERIFICAÇÃO DE TELEFONE"

testar "enviar codigo SMS" POST "/api/v1/verificacao-telefone/enviar-codigo" "200,202,204,400,429" \
  '{"telefone":"11999998888"}'
testar "validar codigo SMS" POST "/api/v1/verificacao-telefone/validar-codigo" "200,400,401,422" \
  '{"telefone":"11999998888","codigo":"000000"}'
testar "editar funcionario" PUT "/api/v1/lojista/funcionarios/${FUNCIONARIO_ID}" "200" \
  '{"nome":"Smoke Func Edit","telefone":"11999998877","cargo":"Gerente"}' \
  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi

# =============================================================================
# GRUPO 13 — UPLOADS / WEBHOOKS
# =============================================================================
if deve_rodar extras; then
log_grupo "13/13  UPLOADS E WEBHOOKS"

testar "upload imagem (sem file = 400)" POST "/api/v1/uploads/imagem?pasta=produtos" "400,401,415,422" "-"

# Webhooks — só verificam que a rota existe e rejeita payload vazio
testar "webhook stripe" POST "/api/v1/webhooks/stripe" "400,401,422" "-" \
  -H "Stripe-Signature: fake"
testar "webhook asaas"  POST "/api/v1/webhooks/asaas"  "400,401,422" "-" \
  -H "asaas-access-token: fake"
fi

# =============================================================================
# GRUPO 14 — COBERTURA EXTRA (endpoints que faltavam)
# =============================================================================
if deve_rodar extras; then
log_grupo "14/14  COBERTURA EXTRA"

# ────────────────────────────────────────────────────────────────────
# POST /auth/registrar — só funciona após verificação de e-mail
# Sem SMTP em dev, o backend rejeita com "verifique seu e-mail" (4xx).
# ────────────────────────────────────────────────────────────────────
EMAIL_REGISTRO="smokereg.$(date +%s)@nhac.com"
testar "registrar (sem verificação → 4xx)" POST "/api/v1/auth/registrar" "400,409,422,503" \
  '{"nome":"Smoke Registro","email":"'"${EMAIL_REGISTRO}"'","telefone":"+5511911110000","senha":"senha123"}'

# ────────────────────────────────────────────────────────────────────
# POST /produtos — cria produto na loja do lojista logado
# Usa LOJA_ID_LOJISTA (loja do lojista@nhac.com) — variável já existe
# ────────────────────────────────────────────────────────────────────
if [[ -n "${LOJA_ID_LOJISTA:-}" ]]; then
  testar "criar produto (lojista)" POST "/api/v1/produtos" "201" \
    '{"lojaId":"'"${LOJA_ID_LOJISTA}"'","nome":"Produto Cobertura","descricao":"x","preco":19.90,"categoriaMenu":"Lanches","estoque":10,"peso":"300g","percentualDesconto":0}' \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  PRODUTO_COBERTURA_ID=$(extrair '.id // empty')

  if [[ -n "$PRODUTO_COBERTURA_ID" ]]; then
    testar "buscar produto por id (público)" GET "/api/v1/produtos/${PRODUTO_COBERTURA_ID}" "200" "-"
    testar "resumo de avaliações do produto"  GET "/api/v1/produtos/${PRODUTO_COBERTURA_ID}/avaliacoes/resumo" "200" "-"
    testar "desativar produto criado"         DELETE "/api/v1/produtos/${PRODUTO_COBERTURA_ID}" "200,204" "-" \
      -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  fi
fi

# ────────────────────────────────────────────────────────────────────
# PUT /lojas/{id} — atualizar dados da loja do próprio lojista
# ────────────────────────────────────────────────────────────────────
# ────────────────────────────────────────────────────────────────────
# PUT /lojas/{id} — payload completo conforme LojaCreateDTO
# ────────────────────────────────────────────────────────────────────
if [[ -n "${LOJA_ID_LOJISTA:-}" ]]; then
  testar "atualizar loja" PUT "/api/v1/lojas/${LOJA_ID_LOJISTA}" "200" \
    '{"nome":"Nhac Sushi Premium","descricao":"Atualizado pelo smoke","categoria":"Japonesa","imagemUrl":"https://picsum.photos/seed/sushi/400","isAberto":true,"horarios":{"domingo":"10:00-20:00","segunda":"08:00-22:00","terca":"08:00-22:00","quarta":"08:00-22:00","quinta":"08:00-22:00","sexta":"08:00-23:00","sabado":"09:00-23:00"},"dadosOperacionais":{"taxaEntregaBase":5.99,"tempoEntregaMin":20,"tempoEntregaMax":40,"entregaPropria":true,"retiradaNoLocal":true},"endereco":{"rua":"Alameda dos Autores","numero":"100","bairro":"Jardim Paulista","cidade":"São Paulo","estado":"SP","cep":"01000-000"}}' \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"

  # ────────────────────────────────────────────────────────────────────
  # PATCH /lojas/{id}/abertura — campo é isAberto
  # ────────────────────────────────────────────────────────────────────
  testar "fechar loja"  PATCH "/api/v1/lojas/${LOJA_ID_LOJISTA}/abertura" "200" \
    '{"isAberto":false}' -H "Authorization: Bearer ${TOKEN_LOJISTA}"
  testar "reabrir loja" PATCH "/api/v1/lojas/${LOJA_ID_LOJISTA}/abertura" "200" \
    '{"isAberto":true}'  -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi

# ────────────────────────────────────────────────────────────────────
# GET /lojista/pedidos/{id} — detalhe de pedido recebido
# Aceita 200 (pedido da loja) ou 404 (pedido não pertence à loja do token)
# ────────────────────────────────────────────────────────────────────
if [[ -n "${PEDIDO_ID:-}" ]]; then
  testar "detalhe pedido do lojista" GET "/api/v1/lojista/pedidos/${PEDIDO_ID}" "200,403,404" "-" \
    -H "Authorization: Bearer ${TOKEN_LOJISTA}"
fi

# ────────────────────────────────────────────────────────────────────
# DELETE /usuarios/{id} — cria um descartável e desativa (evita sujar
# o usuário principal usado no resto do smoke)
# ────────────────────────────────────────────────────────────────────
EMAIL_DEL="smokedelete.$(date +%s)@nhac.com"
testar "criar usuário descartável" POST "/api/v1/usuarios" "201,400" \
  '{"nome":"Smoke Delete","email":"'"${EMAIL_DEL}"'","telefone":"+5511911119999","senha":"senha123"}'
USUARIO_DEL_ID=$(extrair '.id // empty')

if [[ -n "$USUARIO_DEL_ID" ]]; then
  testar "desativar usuário" DELETE "/api/v1/usuarios/${USUARIO_DEL_ID}" "200,204" "-" \
    -H "Authorization: Bearer ${TOKEN}"
fi

fi

# =============================================================================
# RELATÓRIO FINAL
# =============================================================================
echo
echo -e "${C_BOLD}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "${C_BOLD} RELATÓRIO FINAL${C_RST}"
echo -e "${C_BOLD}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "  ${C_GREEN}✓ Passou: ${PASS}${C_RST}"
echo -e "  ${C_RED}✗ Falhou: ${FAIL}${C_RST}"
echo -e "  ${C_GRAY}Total   : ${TOTAL}${C_RST}"
echo

if [[ ${#FALHAS[@]} -gt 0 ]]; then
  echo -e "${C_BOLD}${C_RED}Falhas:${C_RST}"
  for f in "${FALHAS[@]}"; do
    echo -e "  ${C_RED}•${C_RST} $f"
  done
fi

echo
if [[ $FAIL -eq 0 ]]; then
  echo -e "${C_GREEN}${C_BOLD}🎉 Todos os endpoints responderam dentro do esperado!${C_RST}"
  exit 0
else
  echo -e "${C_YELLOW}${C_BOLD}⚠ Rode com --verbose para ver os bodies das respostas.${C_RST}"
  exit 1
fi