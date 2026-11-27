#!/usr/bin/env bash
# Contrôle de sécurité des workflows GitHub :
# - autorisation financière explicite, booléenne et désactivée par défaut ;
# - build et E2E : aucun secret, aucun droit d'écriture ;
# - publication et signature de développement : seuls workflows sensibles autorisés ;
# - workflows sensibles : workflow_dispatch uniquement, propriétaire + main ;
# - secrets DEV_* uniquement dans les étapes de l'environnement development ;
# - publication : empreinte APK = empreinte du keystore, et clé de test rejetée ;
# - toutes les actions sont épinglées à un SHA complet.
set -uo pipefail
cd "$(dirname "$0")/.."
fails=0
ok() { echo "✅ $1"; }
ko() { echo "❌ $1"; fails=$((fails + 1)); }
W=${WORKFLOWS_DIR:-.github/workflows}

# Vérifie l'entrée financière et la condition de CHAQUE job.
# index() évite les problèmes d'échappement des points dans les regex AWK.
for f in "$W"/*.yml; do
  if awk '
    BEGIN { input_count=0; default_false=0; boolean_type=0; jobs=0; job_guarded=0; bad_job=0; in_input=0; in_jobs=0 }
    /^      authorize_paid_usage:[[:space:]]*$/ {
      input_count++
      in_input=1
      next
    }
    in_input && /^      [A-Za-z0-9_-]+:/ { in_input=0 }
    in_input && /^        default:[[:space:]]*false[[:space:]]*(#.*)?$/ { default_false++ }
    in_input && /^        type:[[:space:]]*boolean[[:space:]]*(#.*)?$/ { boolean_type++ }
    /^jobs:[[:space:]]*$/ { in_jobs=1; next }
    in_jobs && /^  [A-Za-z0-9_-]+:/ {
      if (jobs > 0 && !job_guarded) bad_job=1
      jobs++
      job_guarded=0
      next
    }
    in_jobs && /^    if:[[:space:]]*/ {
      if (index($0, "inputs.authorize_paid_usage == true") > 0) job_guarded=1
    }
    END {
      if (jobs > 0 && !job_guarded) bad_job=1
      if (input_count != 1 || default_false != 1 || boolean_type != 1 || jobs == 0 || bad_job) exit 1
    }
  ' "$f"; then
    ok "$f : autorisation financière explicite, booléenne et désactivée par défaut"
  else
    ko "$f : entrée financière ou condition d'un job absente/invalide"
  fi
done

for f in "$W/build.yml" "$W/e2e.yml" "$W/security.yml"; do
  content=$(sed 's/[[:space:]]*#.*$//' "$f")
  if printf '%s\n' "$content" | tr -d '[:space:]' | grep -qE '(^|[^[:alnum:]_])secrets([.][[:alnum:]_]+|\[[^]]+\]|[^[:alnum:]_]|$)'; then
    ko "$f référence un secret"
  else
    ok "$f : aucun secret"
  fi
  if printf '%s\n' "$content" | grep -qE '(^|[,{[:space:]])(contents|actions|checks|deployments|issues|packages|pull-requests|id-token|security-events|statuses|discussions|pages|attestations|models|repository-projects|organization-projects):[[:space:]]*(write|write-all)([[:space:],}]|$)|(^|[[:space:]])permissions:[[:space:]]*write-all([[:space:]]|$)'; then
    ko "$f demande un droit d'écriture"
  else
    ok "$f : aucun droit d'écriture"
  fi
  grep -qE '^permissions:' "$f" && grep -A1 -E '^permissions:' "$f" | grep -q 'contents: read' && ok "$f : contents: read" || ko "$f : permissions par défaut absentes"
done

only_manual() {
  awk '
    /^on:$/ { in_on=1; next }
    in_on && /^[^[:space:]]/ { exit }
    in_on && /^  [A-Za-z0-9_-]+:/ { event=$1; sub(/:$/, "", event); print event }
  ' "$1"
}

S="$W/security.yml"
events=$(only_manual "$S")
[ "$events" = "workflow_dispatch" ] && ok "sécurité : lancement manuel uniquement" || ko "sécurité : déclencheur autre que workflow_dispatch présent"

P="$W/publication.yml"
events=$(only_manual "$P")
[ "$events" = "workflow_dispatch" ] && ok "publication : lancement manuel uniquement" || ko "publication : déclencheur autre que workflow_dispatch présent"
grep -q "github.actor == github.repository_owner && github.ref == 'refs/heads/main'" "$P" && ok "publication : propriétaire sur main uniquement" || ko "publication : condition propriétaire/main absente"
grep -q 'environment: publication' "$P" && ok "publication : environnement protégeable" || ko "publication : environnement absent"
grep -Fq '[ "$APK_FP" != "$RELEASE_FP" ]' "$P" && ok "publication : comparaison APK/clé exacte" || ko "publication : comparaison APK/clé absente"
grep -q '\[ -z "\$RELEASE_FP" \]' "$P" && grep -q '\[ "\$RELEASE_FP" = "\$DEBUG_FP" \]' "$P" && ok "publication : clé illisible ou identique à la clé de test = refus" || ko "publication : garde-fou clé de test incomplet"

D="$W/developpement.yml"
events=$(only_manual "$D")
[ "$events" = "workflow_dispatch" ] && ok "développement : lancement manuel uniquement" || ko "développement : déclencheur autre que workflow_dispatch présent"
grep -q "github.actor == github.repository_owner && github.ref == 'refs/heads/main'" "$D" && ok "développement : propriétaire sur main uniquement" || ko "développement : condition propriétaire/main absente"
grep -q 'environment: development' "$D" && ok "développement : environnement protégé" || ko "développement : environnement absent"
grep -qE '^permissions:[[:space:]]*$' "$D" && grep -A1 -E '^permissions:' "$D" | grep -q 'contents: read' && ok "développement : contents: read" || ko "développement : permissions restrictives absentes"
if grep -R -n 'secrets\.DEV_' "$W" --include='*.yml' | grep -v 'developpement.yml'; then
  ko "un secret DEV_* est référencé hors du workflow de développement"
else
  ok "secrets DEV_* absents des autres workflows"
fi
bad_dev_secret_scope=$(awk '
  /^    [^[:space:]]/ { in_step=0; in_step_env=0 }
  /^      - / { in_step=1; in_step_env=0; next }
  /^        env:[[:space:]]*$/ { in_step_env=in_step; next }
  /^        [A-Za-z0-9_-]+:/ { in_step_env=0; next }
  /secrets\.DEV_/ && !in_step_env { print NR ":" $0 }
' "$D" || true)
if [ -n "$bad_dev_secret_scope" ]; then
  printf '%s\n' "$bad_dev_secret_scope"
  ko "secret DEV_* injecté hors d'un env d'étape"
else
  ok "secrets DEV_* limités aux env des étapes"
fi

unpinned=$(grep -hnE '^[[:space:]]*-?[[:space:]]*uses:' "$W"/*.yml | grep -vE '@[0-9a-f]{40}([[:space:]]|$)')
if [ -n "$unpinned" ]; then echo "$unpinned"; ko "action non épinglée à un SHA"; else ok "toutes les actions sont épinglées à un SHA"; fi

echo "Échecs : $fails"
exit "$fails"
