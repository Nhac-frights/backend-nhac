#!/usr/bin/env bash
# =============================================================================
# diagnostico.sh — Roda todos os testes/validações e imprime só os erros.
#
# Uso:
#   ./scripts/diagnostico.sh                # roda tudo
#   ./scripts/diagnostico.sh --only smoke   # roda só uma categoria
#   ./scripts/diagnostico.sh --full         # mostra saída completa (não só erro)
# =============================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
LOG_DIR="${LOG_DIR:-/tmp/diagnostico-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$LOG_DIR"

ONLY=""
FULL=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only) ONLY="$2"; shift 2 ;;
    --full) FULL=1; shift ;;
    *) echo "Opção desconhecida: $1"; exit 1 ;;
  esac
done

# ─── Cores ──────────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_CYAN=$'\033[36m'; C_GRAY=$'\033[90m'; C_BOLD=$'\033[1m'; C_RST=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_CYAN=""; C_GRAY=""; C_BOLD=""; C_RST=""
fi

declare -A RESULTADO   # nome -> OK|FAIL|SKIP
declare -A RESULTADO_MSG

deve_rodar() {
  [[ -z "$ONLY" ]] && return 0
  IFS=',' read -ra arr <<< "$ONLY"
  for g in "${arr[@]}"; do [[ "$g" == "$1" ]] && return 0; done
  return 1
}

# ─── Helper: roda um comando, salva log, marca OK/FAIL ──────────────────────
rodar() {
  local nome="$1"; shift
  local logfile="$LOG_DIR/${nome}.log"

  echo
  echo -e "${C_BOLD}${C_CYAN}▶ ${nome}${C_RST}"
  echo -e "${C_GRAY}  $ $*${C_RST}"

  if "$@" > "$logfile" 2>&1; then
    RESULTADO[$nome]="OK"
    RESULTADO_MSG[$nome]=""
    echo -e "${C_GREEN}  ✓ OK${C_RST}"
  else
    local code=$?
    RESULTADO[$nome]="FAIL"
    RESULTADO_MSG[$nome]="exit code $code"
    echo -e "${C_RED}  ✗ FAIL (exit $code)${C_RST}"
  fi

  if [[ $FULL -eq 1 ]]; then
    echo -e "${C_GRAY}───── output ─────${C_RST}"
    cat "$logfile"
    echo -e "${C_GRAY}──────────────────${C_RST}"
  fi
}

# ═════════════════════════════════════════════════════════════════════════════
# SANITY
# ═════════════════════════════════════════════════════════════════════════════
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "${C_BOLD}${C_CYAN} DIAGNÓSTICO COMPLETO${C_RST}"
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "  Logs em: ${C_GRAY}${LOG_DIR}${C_RST}"
echo -e "  Backend: ${C_GRAY}${BASE_URL}${C_RST}"
echo -e "  Escopo : ${C_GRAY}${ONLY:-tudo}${C_RST}"

# Detecta se o backend está no ar
BACKEND_UP=0
if curl -sf "${BASE_URL}/v3/api-docs" > /dev/null 2>&1; then
  BACKEND_UP=1
  echo -e "  Backend: ${C_GREEN}✓ no ar${C_RST}"
else
  echo -e "  Backend: ${C_YELLOW}⚠ offline (smoke e cobertura serão pulados)${C_RST}"
fi

# ═════════════════════════════════════════════════════════════════════════════
# 1. TESTES UNITÁRIOS (mvn test)
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar unit; then
  rodar "unit-test" ./mvnw -B -ntp test 2>/dev/null \
    || rodar "unit-test" mvn -B -ntp test
fi

# ═════════════════════════════════════════════════════════════════════════════
# 2. BUILD COMPLETO (mvn verify — testes + jacoco)
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar verify; then
  rodar "mvn-verify" ./mvnw -B -ntp verify -DskipITs 2>/dev/null \
    || rodar "mvn-verify" mvn -B -ntp verify -DskipITs
fi

# ═════════════════════════════════════════════════════════════════════════════
# 3. SMOKE TEST
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar smoke; then
  if [[ $BACKEND_UP -eq 1 && -x scripts/smoke-test.sh ]]; then
    rodar "smoke-test" ./scripts/smoke-test.sh
  else
    RESULTADO["smoke-test"]="SKIP"
    RESULTADO_MSG["smoke-test"]="backend offline ou script não executável"
  fi
fi

# ═════════════════════════════════════════════════════════════════════════════
# 4. COBERTURA OPENAPI
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar coverage; then
  if [[ $BACKEND_UP -eq 1 && -x scripts/cobertura-smoke.sh ]]; then
    rodar "cobertura-openapi" ./scripts/cobertura-smoke.sh
  else
    RESULTADO["cobertura-openapi"]="SKIP"
    RESULTADO_MSG["cobertura-openapi"]="backend offline ou script não executável"
  fi
fi

# ═════════════════════════════════════════════════════════════════════════════
# 5. YAML PARSE (sintaxe)
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar yaml; then
  rodar "yaml-parse" python3 -c \
    "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML válido')"
fi

# ═════════════════════════════════════════════════════════════════════════════
# 6. YAMLLINT (estilo — só avisa)
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar yamllint && command -v yamllint >/dev/null 2>&1; then
  rodar "yamllint" yamllint .github/workflows/ci.yml
fi

# ═════════════════════════════════════════════════════════════════════════════
# 7. ACTIONLINT (sintaxe GitHub Actions)
# ═════════════════════════════════════════════════════════════════════════════
if deve_rodar actionlint; then
  if command -v actionlint >/dev/null 2>&1; then
    rodar "actionlint" actionlint .github/workflows/ci.yml
  elif command -v docker >/dev/null 2>&1; then
    rodar "actionlint" docker run --rm -v "$(pwd):/repo" --workdir /repo \
      rhysd/actionlint:latest -color .github/workflows/ci.yml
  else
    RESULTADO["actionlint"]="SKIP"
    RESULTADO_MSG["actionlint"]="actionlint nem docker disponíveis"
  fi
fi

# ═════════════════════════════════════════════════════════════════════════════
# RELATÓRIO FINAL
# ═════════════════════════════════════════════════════════════════════════════
echo
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "${C_BOLD}${C_CYAN} RELATÓRIO FINAL${C_RST}"
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"

OK_COUNT=0; FAIL_COUNT=0; SKIP_COUNT=0
for nome in "${!RESULTADO[@]}"; do
  case "${RESULTADO[$nome]}" in
    OK)   OK_COUNT=$((OK_COUNT+1))   ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT+1)) ;;
    SKIP) SKIP_COUNT=$((SKIP_COUNT+1)) ;;
  esac
done

echo -e "  ${C_GREEN}✓ OK   : ${OK_COUNT}${C_RST}"
echo -e "  ${C_RED}✗ FAIL : ${FAIL_COUNT}${C_RST}"
echo -e "  ${C_YELLOW}⊘ SKIP : ${SKIP_COUNT}${C_RST}"
echo

# ─── Erros detalhados ───────────────────────────────────────────────────────
if [[ $FAIL_COUNT -gt 0 ]]; then
  echo -e "${C_BOLD}${C_RED}❌ ERROS POR FERRAMENTA${C_RST}"
  echo

  for nome in "${!RESULTADO[@]}"; do
    if [[ "${RESULTADO[$nome]}" == "FAIL" ]]; then
      echo -e "${C_BOLD}${C_RED}── ${nome} ──${C_RST}"
      echo -e "${C_GRAY}   Log: ${LOG_DIR}/${nome}.log${C_RST}"

      # Extrai só as linhas que parecem erro
      local_log="$LOG_DIR/${nome}.log"
      if [[ -f "$local_log" ]]; then
        # Filtra linhas relevantes (erros/falhas) — mantém as 30 primeiras
        grep -iE 'error|fail|exception|✗|not found|expected|mismatch|cannot|invalid' "$local_log" \
          | head -30 \
          | sed 's/^/   /' \
          || echo "   (nenhuma linha de erro detectada — ver log completo)"
      fi
      echo
    fi
  done
fi

if [[ $SKIP_COUNT -gt 0 ]]; then
  echo -e "${C_BOLD}${C_YELLOW}⊘ PULADOS${C_RST}"
  for nome in "${!RESULTADO[@]}"; do
    if [[ "${RESULTADO[$nome]}" == "SKIP" ]]; then
      echo -e "  ${C_YELLOW}${nome}${C_RST} — ${RESULTADO_MSG[$nome]}"
    fi
  done
  echo
fi

# ─── Veredito ───────────────────────────────────────────────────────────────
if [[ $FAIL_COUNT -eq 0 ]]; then
  echo -e "${C_GREEN}${C_BOLD}🎉 Tudo passou!${C_RST}"
  exit 0
else
  echo -e "${C_RED}${C_BOLD}⚠ ${FAIL_COUNT} categoria(s) falharam — veja os logs em ${LOG_DIR}${C_RST}"
  echo
  echo -e "${C_BOLD}Para ver o log completo de uma categoria:${C_RST}"
  echo -e "  ${C_GRAY}cat ${LOG_DIR}/<nome>.log${C_RST}"
  echo
  echo -e "${C_BOLD}Para ver uma categoria específica com saída completa:${C_RST}"
  echo -e "  ${C_GRAY}./scripts/diagnostico.sh --only smoke --full${C_RST}"
  exit 1
fi