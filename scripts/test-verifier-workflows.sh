#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

mkdir -p "$tmp/workflows"

check_accepted() {
  local name=$1
  if ! WORKFLOWS_DIR="$tmp/workflows" bash scripts/verifier-workflows.sh >"$tmp/output" 2>&1; then
    echo "❌ $name : configuration valide rejetée"
    cat "$tmp/output"
    exit 1
  fi
  echo "✅ $name : configuration valide acceptée"
}

check_rejected() {
  local name=$1 reason=$2
  if WORKFLOWS_DIR="$tmp/workflows" bash scripts/verifier-workflows.sh >"$tmp/output" 2>&1; then
    echo "❌ $name : workflow non rejeté"
    cat "$tmp/output"
    exit 1
  fi
  if ! grep -Fq "$reason" "$tmp/output"; then
    echo "❌ $name : rejet pour une autre raison"
    cat "$tmp/output"
    exit 1
  fi
  echo "✅ $name : workflow rejeté"
}

# Test positif essentiel : le dépôt tel qu'il est configuré doit passer intégralement.
cp .github/workflows/*.yml "$tmp/workflows/"
check_accepted "workflows du dépôt correctement configurés"

printf '%s\n' '    env: { TOKEN: "${{ secrets['\''TOKEN'\''] }}" }' >> "$tmp/workflows/build.yml"
check_rejected "secret avec accès entre crochets" "référence un secret"

cp .github/workflows/*.yml "$tmp/workflows/"
printf '%s\n' '    run: echo "${{ toJSON(secrets) }}"' >> "$tmp/workflows/build.yml"
check_rejected "exposition de tout le contexte secrets" "référence un secret"

cp .github/workflows/*.yml "$tmp/workflows/"
printf '%s\n' '    permissions: { contents: write }' >> "$tmp/workflows/build.yml"
check_rejected "permission d’écriture en YAML compact" "demande un droit d'écriture"

cp .github/workflows/*.yml "$tmp/workflows/"
# Important : échapper && dans le remplacement sed, car & développe le texte recherché.
sed -i.bak "s#github.actor == github.repository_owner \&\& github.ref == 'refs/heads/main'#github.actor == github.repository_owner \&\& (github.ref == 'refs/heads/main' || github.ref == 'refs/heads/legacy')#" "$tmp/workflows/developpement.yml"
check_rejected "branche legacy autorisée dans le workflow de développement" "condition propriétaire/main absente"

cp .github/workflows/*.yml "$tmp/workflows/"
sed -i.bak '/APK_FP.*RELEASE_FP.*APK_FP.*RELEASE_FP/d' "$tmp/workflows/publication.yml"
check_rejected "comparaison certificat APK/clé absente" "publication : comparaison APK/clé absente"

cp .github/workflows/*.yml "$tmp/workflows/"
awk '/^  workflow_dispatch:/ { print; print "  push:"; next } { print }' "$tmp/workflows/developpement.yml" > "$tmp/developpement.yml"
mv "$tmp/developpement.yml" "$tmp/workflows/developpement.yml"
check_rejected "déclencheur push dans le workflow de développement" "développement : déclencheur autre que workflow_dispatch présent"

cp .github/workflows/*.yml "$tmp/workflows/"
awk '/^    steps:/ { print "    env:"; print "      DEV_KEYSTORE_BASE64: ${{ secrets.DEV_KEYSTORE_BASE64 }}"; next } { print }' "$tmp/workflows/developpement.yml" > "$tmp/developpement.yml"
mv "$tmp/developpement.yml" "$tmp/workflows/developpement.yml"
check_rejected "secret DEV_* au niveau du job" "secret DEV_* injecté hors d'un env d'étape"

cp .github/workflows/*.yml "$tmp/workflows/"
awk '/^        with:/ { print; print "          DEV_KEYSTORE_BASE64: ${{ secrets.DEV_KEYSTORE_BASE64 }}"; next } { print }' "$tmp/workflows/developpement.yml" > "$tmp/developpement.yml"
mv "$tmp/developpement.yml" "$tmp/workflows/developpement.yml"
check_rejected "secret DEV_* sous with" "secret DEV_* injecté hors d'un env d'étape"

cp .github/workflows/*.yml "$tmp/workflows/"
sed -i.bak '/"\$RELEASE_FP" = "\$DEBUG_FP"/d' "$tmp/workflows/publication.yml"
check_rejected "empreinte de clé de test non vérifiée" "publication : garde-fou clé de test incomplet"

cp .github/workflows/*.yml "$tmp/workflows/"
awk '/^  workflow_dispatch:/ { print; print "  pull_request:"; next } { print }' "$tmp/workflows/security.yml" > "$tmp/security.yml"
mv "$tmp/security.yml" "$tmp/workflows/security.yml"
check_rejected "déclencheur automatique dans le workflow de sécurité" "sécurité : déclencheur autre que workflow_dispatch présent"

cp .github/workflows/*.yml "$tmp/workflows/"
sed -i.bak 's/default: false/default: true/' "$tmp/workflows/build.yml"
check_rejected "autorisation financière activée par défaut" "entrée financière ou condition d'un job absente/invalide"

cp .github/workflows/*.yml "$tmp/workflows/"
sed -i.bak '/inputs.authorize_paid_usage == true/d' "$tmp/workflows/build.yml"
check_rejected "job sans autorisation financière" "entrée financière ou condition d'un job absente/invalide"

echo "Tous les tests du vérificateur de workflows sont réussis."
