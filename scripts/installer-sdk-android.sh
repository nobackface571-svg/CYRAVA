#!/usr/bin/env bash
# Installe le SDK Android en ligne de commande (outils, plateforme API 37, build-tools 37.0.0)
# dans /opt/android-sdk et indique son emplacement au projet (local.properties, non versionné).
#
# À lancer une fois dans une nouvelle session de travail, depuis la racine du dépôt :
#     bash scripts/installer-sdk-android.sh
# Il faut que l'environnement autorise le serveur dl.google.com. Ensuite :
#     ./gradlew test :app:lintRelease :app:assembleRelease
set -euo pipefail

SDK="${ANDROID_SDK_DIR:-/opt/android-sdk}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  TMP="$(mktemp -d)"
  curl -fsSL https://dl.google.com/android/repository/repository2-3.xml -o "$TMP/repo.xml"
  ZIP="$(grep -o 'commandlinetools-linux-[0-9]*_latest.zip' "$TMP/repo.xml" | sort -V | tail -1)"
  echo "Outils en ligne de commande : $ZIP"
  curl -fsSL "https://dl.google.com/android/repository/$ZIP" -o "$TMP/tools.zip"
  unzip -q "$TMP/tools.zip" -d "$TMP"
  mkdir -p "$SDK/cmdline-tools"
  mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$TMP"
fi

yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses > /dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --install \
  "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" > /dev/null

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "SDK Android installé dans $SDK :"
ls "$SDK/platforms" "$SDK/build-tools"
