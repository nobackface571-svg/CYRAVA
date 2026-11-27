#!/usr/bin/env bash
# Test de configuration : Gradle doit REFUSER de préparer une version publiable sans clé
# personnelle complète, et accepter une compilation de test. Lancé par la CI et à la main :
#     bash scripts/verifier-signature.sh
set -uo pipefail
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
fails=0
# Clé de test jetable, créée pour ce contrôle uniquement (aucune clé n'est rangée dans le dépôt).
TESTKS="$TMP/cle-de-test.jks"
if ! keytool -genkeypair -noprompt -keystore "$TESTKS" -storetype JKS -storepass android -keypass android \
     -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 1 -dname "CN=CYRAVA Test" >/dev/null 2>&1; then
  echo "❌ impossible de créer la clé de test jetable"; exit 1
fi
export TEST_KEYSTORE_PATH="$TESTKS" TEST_KEYSTORE_PASSWORD=android TEST_KEY_ALIAS=androiddebugkey TEST_KEY_PASSWORD=android
expect_refusal() { # $1 = description ; le reste = variables d'environnement
  local name=$1; shift
  if env -u RELEASE_KEYSTORE_PATH -u RELEASE_KEYSTORE_PASSWORD -u RELEASE_KEY_ALIAS -u RELEASE_KEY_PASSWORD -u REFUSBOT_PUBLICATION \
       "$@" ./gradlew -q --console=plain :app:help > "$TMP/verif-signature.log" 2>&1; then
    echo "❌ $name : Gradle aurait dû refuser"; fails=$((fails + 1))
  elif grep -q "refusée" "$TMP/verif-signature.log"; then
    echo "✅ $name : refusé"
  else
    echo "❌ $name : échec inattendu"; cat "$TMP/verif-signature.log"; fails=$((fails + 1))
  fi
}
expect_refusal "version 9.9.9 sans clé" VERSION_NAME=9.9.9
expect_refusal "publication demandée sans clé" VERSION_NAME=9.9.9-essai REFUSBOT_PUBLICATION=true
expect_refusal "clé incomplète (mot de passe manquant)" VERSION_NAME=9.9.9 RELEASE_KEYSTORE_PATH="$TESTKS" RELEASE_KEY_ALIAS=x
expect_refusal "clé de test présentée comme clé de publication" VERSION_NAME=9.9.9 RELEASE_KEYSTORE_PATH="$TESTKS" \
  RELEASE_KEYSTORE_PASSWORD=android RELEASE_KEY_ALIAS=androiddebugkey RELEASE_KEY_PASSWORD=android
if ! keytool -importkeystore -noprompt \
  -srckeystore "$TESTKS" -srcstoretype JKS -srcstorepass android -srcalias androiddebugkey \
  -destkeystore "$TMP/reencoded.p12" -deststoretype PKCS12 -deststorepass autre-mot-de-passe -destalias cle-renommee >/dev/null 2>&1; then
  echo "❌ impossible de créer le keystore de test réencodé"; exit 1
fi
debug_fp=$(keytool -list -v -keystore "$TESTKS" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')
reencoded_fp=$(keytool -list -v -keystore "$TMP/reencoded.p12" -storepass autre-mot-de-passe -alias cle-renommee 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]')
if [ -z "$debug_fp" ] || [ "$debug_fp" != "$reencoded_fp" ]; then
  echo "❌ le keystore réencodé n'a pas conservé la même identité"; exit 1
fi
if env VERSION_NAME=9.9.9 RELEASE_KEYSTORE_PATH="$TMP/reencoded.p12" \
     RELEASE_KEYSTORE_PASSWORD=autre-mot-de-passe RELEASE_KEY_ALIAS=cle-renommee \
     RELEASE_KEY_PASSWORD=autre-mot-de-passe ./gradlew -q --console=plain :app:help > "$TMP/verif-signature.log" 2>&1; then
  echo "❌ la clé de test réencodée a été acceptée"; fails=$((fails + 1))
elif grep -q "clé de test" "$TMP/verif-signature.log"; then
  echo "✅ clé de test réencodée avec autre alias et mot de passe : refusée par empreinte"
else
  echo "❌ clé réencodée refusée pour une autre raison"; tail -5 "$TMP/verif-signature.log"; fails=$((fails + 1))
fi
if env -u RELEASE_KEYSTORE_PATH -u REFUSBOT_PUBLICATION VERSION_NAME=9.9.9-dev.1 ./gradlew -q --console=plain :app:help > "$TMP/verif-signature.log" 2>&1; then
  echo "✅ compilation de test (9.9.9-dev.1) : acceptée"
else
  echo "❌ compilation de test refusée"; tail -5 "$TMP/verif-signature.log"; fails=$((fails + 1))
fi
echo "Échecs : $fails"
exit "$fails"
