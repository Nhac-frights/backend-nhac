#!/usr/bin/env bash
# =============================================================================
# cobertura-smoke.sh — Compara endpoints do OpenAPI com os endpoints que o
# smoke test realmente exercita. Mostra o que está coberto e o que falta.
#
# Uso:
#   ./scripts/cobertura-smoke.sh
#   BASE_URL=http://localhost:8080 ./scripts/cobertura-smoke.sh
#   ./scripts/cobertura-smoke.sh scripts/smoke-test.sh   # arquivo custom
# =============================================================================
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SMOKE_FILE="${1:-scripts/smoke-test.sh}"

if [[ ! -f "$SMOKE_FILE" ]]; then
  echo "Arquivo de smoke não encontrado: $SMOKE_FILE" >&2
  exit 1
fi

if [[ -t 1 ]]; then
  GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'
  CYAN=$'\033[36m'; BOLD=$'\033[1m'; RST=$'\033[0m'
else
  GREEN=""; RED=""; YELLOW=""; CYAN=""; BOLD=""; RST=""
fi

echo -e "${BOLD}${CYAN}Buscando OpenAPI em ${BASE_URL}...${RST}"
OPENAPI_JSON="/tmp/cobertura-openapi.json"
curl -sf "${BASE_URL}/v3/api-docs" -o "$OPENAPI_JSON"
echo -e "${GREEN}✓ OpenAPI baixado ($(wc -c < "$OPENAPI_JSON") bytes)${RST}"

# ─────────────────────────────────────────────────────────────────────────────
# Roda o "diff" em python (bash puro ficaria ilegível)
# ─────────────────────────────────────────────────────────────────────────────
python3 - "$OPENAPI_JSON" "$SMOKE_FILE" <<'PYEOF'
import json, re, sys

openapi_path, smoke_path = sys.argv[1], sys.argv[2]

# Cores (duplica do shell, é mais simples que passar por env)
GREEN  = '\033[32m'; RED = '\033[31m'; YELLOW = '\033[33m'
CYAN   = '\033[36m'; BOLD = '\033[1m'; GRAY = '\033[90m'; RST = '\033[0m'

# ── 1. Universo: endpoints do OpenAPI ────────────────────────────────────────
spec = json.load(open(openapi_path))
openapi_endpoints = {}  # (METHOD, path_normalizado) -> path_original
for path, methods in spec.get('paths', {}).items():
    for method in methods:
        if method.lower() in ('get','post','put','patch','delete'):
            openapi_endpoints[(method.upper(), path)] = path

# ── 2. Cobertura: endpoints que o smoke chama ────────────────────────────────
smoke_text = open(smoke_path).read()
# Formato: testar "nome" METHOD "path" "esperados" ["body"] [extras]
pattern = re.compile(r'testar\s+"[^"]+"\s+([A-Z]+)\s+"([^"]+)"')
smoke_hits = set()
for m in pattern.finditer(smoke_text):
    method = m.group(1).upper()
    path = m.group(2).split('?')[0]  # remove query string
    smoke_hits.add((method, path))

# ── 3. Normaliza path do OpenAPI: {param} -> [^/]+ e compara ────────────────
def openapi_to_regex(p):
    parts = p.strip('/').split('/')
    rx = []
    for part in parts:
        if part.startswith('{') and part.endswith('}'):
            rx.append('[^/]+')
        else:
            rx.append(re.escape(part))
    return re.compile('^/' + '/'.join(rx) + '$')

covered, uncovered = [], []
for (method, path_openapi) in sorted(openapi_endpoints.keys()):
    rx = openapi_to_regex(path_openapi)
    found = any(m == method and rx.match(p) for (m, p) in smoke_hits)
    (covered if found else uncovered).append((method, path_openapi))

# ── 4. Smoke testa endpoints que não existem no OpenAPI? ────────────────────
orphans = []
for (method, path_smoke) in smoke_hits:
    matched = False
    for (m, p) in openapi_endpoints.keys():
        if m == method and openapi_to_regex(p).match(path_smoke):
            matched = True
            break
    if not matched:
        orphans.append((method, path_smoke))

# ── 5. Relatório ────────────────────────────────────────────────────────────
total = len(covered) + len(uncovered)
pct = (100 * len(covered) / total) if total else 0

print()
print(f"{BOLD}{CYAN}═══════════════════════════════════════════════════════════{RST}")
print(f"{BOLD}{CYAN} COBERTURA DO SMOKE TEST vs OPENAPI{RST}")
print(f"{BOLD}{CYAN}═══════════════════════════════════════════════════════════{RST}")
print(f"  Total de endpoints na API : {total}")
print(f"  Cobertos pelo smoke       : {GREEN}{len(covered)}{RST}")
print(f"  NÃO cobertos              : {RED}{len(uncovered)}{RST}")
print(f"  Cobertura                 : {BOLD}{pct:.1f}%{RST}")
print()

if uncovered:
    print(f"{BOLD}{RED}❌ ENDPOINTS NÃO COBERTOS ({len(uncovered)}){RST}")
    print()
    for method, path in uncovered:
        print(f"  {RED}✗{RST} {method:6s} {path}")
    print()
else:
    print(f"{GREEN}🎉 Todos os endpoints do OpenAPI são chamados pelo smoke!{RST}")
    print()

if orphans:
    print(f"{BOLD}{YELLOW}⚠ SMOKE CHAMA ROTAS FORA DO OPENAPI ({len(orphans)}){RST}")
    print(f"{GRAY}(rotas removidas do backend? typo no smoke?){RST}")
    print()
    for method, path in orphans:
        print(f"  {YELLOW}?{RST} {method:6s} {path}")
    print()

# ── 6. Estatística por método ───────────────────────────────────────────────
print(f"{BOLD}{CYAN}DISTRIBUIÇÃO POR MÉTODO{RST}")
for m in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']:
    c = sum(1 for (mm, _) in covered if mm == m)
    u = sum(1 for (mm, _) in uncovered if mm == m)
    t = c + u
    if t:
        bar = '█' * c + '░' * u
        color = GREEN if u == 0 else (YELLOW if c > 0 else RED)
        print(f"  {color}{m:6s}{RST} {bar}  {c}/{t}")
print()
PYEOF

echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════════════${RST}"