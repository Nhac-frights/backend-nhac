#!/usr/bin/env bash
# =============================================================================
# ci-local.sh — Roda o pipeline do CI localmente, igual ao GitHub Actions.
#
# Uso:
#   ./scripts/ci-local.sh              # roda tudo
#   ./scripts/ci-local.sh --skip-db    # pula MariaDB (usa DB já rodando)
#   ./scripts/ci-local.sh --keep-db    # não derruba o container no final
#   ./scripts/ci-local.sh --only test  # só roda um job específico
# =============================================================================
set -uo pipefail

# ─── Configurações (iguais ao CI) ───────────────────────────────────────────
JAVA_VERSION="21"
MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m}"

DB_HOST="localhost"
DB_PORT="3306"
DB_NAME="nhac_db"
DB_USER="root"
DB_PASS="ci_root_password"
DB_URL="jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}"

JWT_SENHA="ci_test_secret_key_not_for_production"
SPRING_PROFILES_ACTIVE="dev"

CONTAINER_NAME="nhac-ci-mariadb"

# ─── Flags ──────────────────────────────────────────────────────────────────
SKIP_DB=0
KEEP_DB=0
ONLY=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-db)  SKIP_DB=1; shift ;;
    --keep-db)  KEEP_DB=1; shift ;;
    --only)     ONLY="$2"; shift 2 ;;
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

log_step() { echo; echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"; echo -e "${C_BOLD}${C_CYAN} $1${C_RST}"; echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"; }
log_ok()   { echo -e "${C_GREEN}✓ $1${C_RST}"; }
log_err()  { echo -e "${C_RED}✗ $1${C_RST}"; }
log_warn() { echo -e "${C_YELLOW}⚠ $1${C_RST}"; }
log_info() { echo -e "${C_GRAY}  $1${C_RST}"; }

deve_rodar() {
  [[ -z "$ONLY" ]] && return 0
  IFS=',' read -ra arr <<< "$ONLY"
  for g in "${arr[@]}"; do [[ "$g" == "$1" ]] && return 0; done
  return 1
}

declare -A RESULT
declare -a ERROS=()

# ─── 0) Detecta Java 21 ─────────────────────────────────────────────────────
log_step "0/6  Verificação de ambiente"

if ! command -v java > /dev/null; then
  log_err "Java não encontrado no PATH"
  exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n1 | grep -oP '"\K[0-9]+' | head -1)
log_info "Java local: $JAVA_VER (CI usa $JAVA_VERSION)"

if [[ "$JAVA_VER" != "$JAVA_VERSION" ]]; then
  log_warn "Versão diferente do CI (esperado $JAVA_VERSION, encontrado $JAVA_VER)"

  # Tenta usar SDKMAN se disponível
  if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    # shellcheck disable=SC1091
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    if sdk list java 2>/dev/null | grep -q "21\..*tem"; then
      CANDIDATE=$(sdk list java | grep -oP '21\.[0-9.]+-tem' | head -1)
      if [[ -n "$CANDIDATE" ]]; then
        log_info "Trocando para $CANDIDATE via SDKMAN..."
        sdk use java "$CANDIDATE" > /dev/null 2>&1 || sdk install java "$CANDIDATE" > /dev/null 2>&1
        JAVA_VER=$(java -version 2>&1 | head -n1 | grep -oP '"\K[0-9]+' | head -1)
        log_info "Java agora: $JAVA_VER"
      fi
    fi
  fi

  if [[ "$JAVA_VER" != "$JAVA_VERSION" ]]; then
    log_err "Não foi possível trocar para Java $JAVA_VERSION"
    log_info "Instale com: sdk install java 21.0.5-tem"
    log_info "Ou baixe em: https://adoptium.net/temurin/releases/?version=21"
    log_info ""
    log_info "⚠  Continuando com Java $JAVA_VER — JaCoCo 0.8.12 pode falhar"
    log_info "   (fallback: adicione -Djacoco.skip=true se travar)"
  fi
fi

# Detecta wrapper do Maven
if [[ -x ./mvnw ]]; then
  MVN="./mvnw"
elif command -v mvn > /dev/null; then
  MVN="mvn"
else
  log_err "Nem mvnw nem mvn disponíveis"
  exit 1
fi
log_info "Maven: $MVN"

# ─── 1) MariaDB ─────────────────────────────────────────────────────────────
if [[ $SKIP_DB -eq 0 ]] && deve_rodar db; then
  log_step "1/6  Subindo MariaDB (igual ao CI)"

  if ! command -v docker > /dev/null; then
    log_err "Docker não encontrado — use --skip-db se já tiver MariaDB rodando"
    exit 1
  fi

  # Derruba container anterior, se existir
  docker rm -f "$CONTAINER_NAME" > /dev/null 2>&1 || true

  docker run -d \
    --name "$CONTAINER_NAME" \
    -e MYSQL_ROOT_PASSWORD="$DB_PASS" \
    -e MYSQL_DATABASE="$DB_NAME" \
    -p "${DB_PORT}:3306" \
    mariadb:11 > /dev/null

  log_info "Aguardando MariaDB ficar pronto..."
  for i in $(seq 1 30); do
    if docker exec "$CONTAINER_NAME" healthcheck.sh --connect --innodb_initialized > /dev/null 2>&1; then
      log_ok "MariaDB pronto após ${i}s"
      break
    fi
    sleep 1
    if [[ $i -eq 30 ]]; then
      log_err "MariaDB não subiu em 30s"
      docker logs "$CONTAINER_NAME" | tail -20
      exit 1
    fi
  done
fi

# Limpa container ao sair
cleanup() {
  if [[ $KEEP_DB -eq 0 && $SKIP_DB -eq 0 ]]; then
    log_info "Derrubando MariaDB..."
    docker rm -f "$CONTAINER_NAME" > /dev/null 2>&1 || true
  fi
  if [[ -f /tmp/ci-local-backend.pid ]]; then
    log_info "Matando backend..."
    kill "$(cat /tmp/ci-local-backend.pid)" 2>/dev/null || true
    rm -f /tmp/ci-local-backend.pid
  fi
}
trap cleanup EXIT

# Exporta variáveis pros comandos Maven
export DB_URL DB_USER DB_PASS JWT_SENHA
export MAVEN_OPTS

# ─── 2) mvn validate ────────────────────────────────────────────────────────
if deve_rodar validate; then
  log_step "2/6  mvn validate"
  if $MVN -B validate; then
    RESULT[validate]=OK; log_ok "validate OK"
  else
    RESULT[validate]=FAIL; log_err "validate FALHOU"
    ERROS+=("validate")
  fi
fi

# ─── 3) mvn clean compile ───────────────────────────────────────────────────
if deve_rodar compile; then
  log_step "3/6  mvn clean compile"
  if $MVN -B -ntp clean compile; then
    RESULT[compile]=OK; log_ok "compile OK"
  else
    RESULT[compile]=FAIL; log_err "compile FALHOU"
    ERROS+=("compile")
  fi
fi

# ─── 4) mvn verify ──────────────────────────────────────────────────────────
if deve_rodar verify; then
  log_step "4/6  mvn verify (testes + cobertura)"
  log_info "DB_URL=$DB_URL"
  log_info "DB_USER=$DB_USER"

  # Se Java local != 21, adiciona skip jacoco automaticamente
  EXTRA_ARGS=""
  if [[ "$JAVA_VER" != "$JAVA_VERSION" ]]; then
    log_warn "Java $JAVA_VER ≠ $JAVA_VERSION — adicionando -Djacoco.skip=true"
    EXTRA_ARGS="-Djacoco.skip=true"
  fi

  if $MVN -B -ntp verify $EXTRA_ARGS; then
    RESULT[verify]=OK; log_ok "verify OK"
  else
    RESULT[verify]=FAIL; log_err "verify FALHOU"
    ERROS+=("verify")
  fi
fi

# ─── 5) Sobe backend + smoke test ───────────────────────────────────────────
if deve_rodar smoke; then
  log_step "5/6  Backend + smoke test"

  # Checa se o backend já está no ar
  if curl -sf http://localhost:8080/v3/api-docs > /dev/null 2>&1; then
    log_info "Backend já está rodando na porta 8080 — reaproveitando"
  else
    # Verifica se existe JAR
    JAR=$(ls -t target/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
    if [[ -z "$JAR" ]]; then
      log_err "Nenhum JAR em target/ — rode 'verify' antes"
      RESULT[smoke]=SKIP
    else
      log_info "Subindo $JAR com profile=$SPRING_PROFILES_ACTIVE"
      SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
      DB_URL="$DB_URL" DB_USER="$DB_USER" DB_PASS="$DB_PASS" JWT_SENHA="$JWT_SENHA" \
        nohup java -jar "$JAR" > /tmp/ci-local-backend.log 2>&1 &
      echo $! > /tmp/ci-local-backend.pid

      log_info "Aguardando backend (até 90s)..."
      READY=0
      for i in $(seq 1 45); do
        if curl -sf http://localhost:8080/v3/api-docs > /dev/null 2>&1; then
          log_ok "Backend pronto após $((i*2))s"
          READY=1
          break
        fi
        sleep 2
      done

      if [[ $READY -eq 0 ]]; then
        log_err "Backend não subiu em 90s"
        tail -30 /tmp/ci-local-backend.log
        RESULT[smoke]=SKIP
      fi
    fi
  fi

  if [[ "${RESULT[smoke]:-}" != "SKIP" ]]; then
    if [[ -x scripts/smoke-test.sh ]]; then
      if ./scripts/smoke-test.sh; then
        RESULT[smoke]=OK; log_ok "smoke-test OK"
      else
        RESULT[smoke]=FAIL; log_err "smoke-test FALHOU"
        ERROS+=("smoke")
      fi
    else
      log_warn "scripts/smoke-test.sh não encontrado ou não executável"
      RESULT[smoke]=SKIP
    fi
  fi
fi

# ─── 6) Cobertura OpenAPI ───────────────────────────────────────────────────
if deve_rodar coverage; then
  log_step "6/6  Cobertura OpenAPI vs smoke"

  if curl -sf http://localhost:8080/v3/api-docs > /dev/null 2>&1 && [[ -x scripts/cobertura-smoke.sh ]]; then
    if ./scripts/cobertura-smoke.sh; then
      RESULT[coverage]=OK; log_ok "cobertura OK"
    else
      RESULT[coverage]=FAIL; log_err "cobertura FALHOU"
      ERROS+=("coverage")
    fi
  else
    log_warn "Backend offline ou script não executável — pulando"
    RESULT[coverage]=SKIP
  fi
fi

# ─── Relatório final ────────────────────────────────────────────────────────
echo
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"
echo -e "${C_BOLD}${C_CYAN} RELATÓRIO FINAL — ci-local${C_RST}"
echo -e "${C_BOLD}${C_CYAN}═══════════════════════════════════════════════════════════${C_RST}"

OK=0; FAIL=0; SKIP=0
for nome in "${!RESULT[@]}"; do
  status="${RESULT[$nome]}"
  case "$status" in
    OK)   OK=$((OK+1));   printf "  ${C_GREEN}✓ %-10s${C_RST} %s\n" "$nome" "$status" ;;
    FAIL) FAIL=$((FAIL+1)); printf "  ${C_RED}✗ %-10s${C_RST} %s\n" "$nome" "$status" ;;
    SKIP) SKIP=$((SKIP+1)); printf "  ${C_YELLOW}⊘ %-10s${C_RST} %s\n" "$nome" "$status" ;;
  esac
done

echo
echo -e "  ${C_GREEN}OK   : $OK${C_RST}"
echo -e "  ${C_RED}FAIL : $FAIL${C_RST}"
echo -e "  ${C_YELLOW}SKIP : $SKIP${C_RST}"
echo

if [[ $FAIL -eq 0 ]]; then
  echo -e "${C_GREEN}${C_BOLD}🎉 Pipeline passou localmente — igual ao CI!${C_RST}"
  exit 0
else
  echo -e "${C_RED}${C_BOLD}⚠ $FAIL step(s) falharam: ${ERROS[*]}${C_RST}"
  echo
  echo -e "${C_BOLD}Dicas:${C_RST}"
  echo -e "  ${C_GRAY}• Erros de JaCoCo com Java ≠ 21: instale Java 21 com 'sdk install java 21.0.5-tem'${C_RST}"
  echo -e "  ${C_GRAY}• Ver log do backend: cat /tmp/ci-local-backend.log${C_RST}"
  echo -e "  ${C_GRAY}• Rodar só um step: ./scripts/ci-local.sh --only smoke${C_RST}"
  exit 1
fi