#!/usr/bin/env bash
# Test de bout en bout sur émulateur : RefusBot + vraie fenêtre Google UMP SDK et CMP génériques.
# Le scénario UMP provient du SDK Google officiel, jamais d'un HTML qui l'imite.
#
# 1. APK RELEASE : installation, lancement, absence de permission INTERNET.
# 2. APK DEBUG (même code, sans R8, lisible avec run-as) : scénarios complets + capture diagnostic.
set -uo pipefail

PKG=fr.cyrava
# Identifiant de l'appli (fr.cyrava) et paquet des classes Kotlin (fr.refusbot) sont différents.
CLS=fr.refusbot
DEMO=fr.refusbot.demo
OUT=e2e-out
mkdir -p "$OUT"
# Copie de toute la sortie dans l'artefact (conservée même si l'étape est interrompue).
exec > >(tee -a "$OUT/e2e.log") 2>&1
FAILS=0
# Points observés mais NON prouvés (limites de la plateforme) : jamais comptés comme conformes.
UNVERIFIED=0
unverified() { echo "⚠️ NON VÉRIFIÉ : $1"; UNVERIFIED=$((UNVERIFIED + 1)); }

# Le runner peut démarrer l'émulateur avant que le daemon ADB soit prêt.
# On attend explicitement son état "device" avant toute commande de test.
log() { echo; echo "=== $* ==="; }
log "0. Attente du daemon ADB et du démarrage Android"
timeout 30 adb start-server || true
adb_ready=1
for attempt in $(seq 1 30); do
  if timeout 10 adb get-state 2>/dev/null | grep -qx "device"; then
    adb_ready=0
    break
  fi
  echo "ADB pas encore prêt (essai $attempt/30)"
  sleep 2
done
if [ "$adb_ready" -ne 0 ]; then
  echo "::error::ADB n'a pas détecté l'émulateur après 60 secondes."
  timeout 10 adb devices -l || true
  exit 1
fi
timeout 120 adb wait-for-device
boot_ready=1
for attempt in $(seq 1 60); do
  if [ "$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\\r')" = "1" ]; then
    boot_ready=0
    break
  fi
  sleep 2
done
if [ "$boot_ready" -ne 0 ]; then
  echo "::error::Android n'a pas terminé son démarrage après 120 secondes."
  timeout 10 adb devices -l || true
  exit 1
fi
echo "✅ ADB connecté et Android démarré."

log() { echo; echo "=== $* ==="; }
check() { if [ "$2" -eq 0 ]; then echo "✅ $1"; else echo "::error::❌ $1"; FAILS=$((FAILS + 1)); fi; }
# Toute commande vers l'émulateur est bornée dans le temps : le test ne peut jamais rester bloqué.
a() { timeout 45 adb "$@"; }
dump() { timeout 12 adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; timeout 10 adb shell cat /sdcard/ui.xml 2>/dev/null; }
texts() { dump | tr '>' '\n' | grep -o 'text="[^"]*"' | sed -e 's/^text="//' -e 's/"$//' | grep -v '^$'; }
shot() { timeout 15 adb exec-out screencap -p > "$OUT/$1.png" 2>/dev/null || true; }
# Centre « x y » du premier nœud dont les attributs contiennent $1.
center_of() {
  local b
  b=$(dump | tr '>' '\n' | grep -F -- "$1" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$b" ] && return 1
  set -- $b
  echo "$((($1 + $3) / 2)) $((($2 + $4) / 2))"
}
# Touche le centre du premier nœud dont les attributs contiennent $1.
tap_match() {
  local c
  c=$(center_of "$1") || { echo "introuvable : $1"; return 1; }
  a shell input tap $c
}
# Taille de l'écran, pour les défilements.
SIZE=$(timeout 15 adb shell wm size 2>/dev/null | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
[ -n "$W" ] && [ -n "$H" ] || { W=1080; H=1920; }
echo "écran : ${W}x${H}"
# Au-dessous de cette ordonnée, un élément peut être caché sous la barre d'onglets (recalculé plus bas).
SAFE_Y=$((H * 7 / 10))
swipe_up() { a shell input swipe $((W / 2)) $((H * 7 / 10)) $((W / 2)) $((H * 3 / 10)) 500; sleep 1; }
# Touche le premier nœud contenant $1, en faisant défiler la page si nécessaire ($2 : essais).
scroll_tap() {
  local c y
  for _ in $(seq 1 "${2:-14}"); do
    if c=$(center_of "$1"); then
      y=${c#* }
      # Un élément caché sous la barre d'onglets n'est pas touchable : on défile encore un peu.
      if [ "$y" -lt "$SAFE_Y" ]; then a shell input tap $c; return 0; fi
    fi
    swipe_up
  done
  echo "introuvable après défilement : $1"
  return 1
}
# Textes d'une page entière : relevé à chaque position de défilement (une liste ne contient que
# ses éléments visibles), jusqu'à ce que l'écran ne change plus ($1 : nombre maximal de relevés).
scroll_texts() {
  local prev="" cur
  for _ in $(seq 1 "${1:-12}"); do
    cur=$(texts)
    [ "$cur" = "$prev" ] && break
    echo "$cur"
    prev=$cur
    swipe_up
  done
}
result() { texts | grep -m1 "RESULTAT" || true; }
# État de l'écran quand un scénario ne donne pas le résultat attendu.
context() {
  echo "--- fenêtre au premier plan :"
  a shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | head -2
  echo "--- textes visibles (uiautomator) :"
  texts | head -15
  echo "--- journal RefusBot :"
  a logcat -d -s RefusBot:* | tail -15
}
demo() { a shell am force-stop $DEMO; sleep 2; a shell am start -W -n $DEMO/.DemoActivity --es scenario "$1" >/dev/null; }

log "1. APK release : installation et lancement"
a install -r -g app/build/outputs/apk/release/app-release.apk
a shell cmd locale set-app-locales $PKG --locales fr-FR >/dev/null 2>&1 || true  # textes vérifiés en français
a shell dumpsys package $PKG | grep -q "android.permission.INTERNET" && check "pas de permission INTERNET" 1 || check "pas de permission INTERNET" 0
a shell am start -W -n $PKG/$CLS.MainActivity >/dev/null
sleep 5
texts | tee "$OUT/accueil-release.txt" | head -12
# Accueil : un des états de la protection (première ouverture : « Protection inactive »).
grep -qE "Protection (inactive|active|en pause)|Service arrêté|Prêt à protéger" "$OUT/accueil-release.txt"; check "release : écran d'accueil en français affiché" $?
shot 1-accueil-release
a logcat -d -b crash | grep -q "$PKG" && check "release : aucun plantage au lancement" 1 || check "release : aucun plantage au lancement" 0
# Haut de la barre d'onglets : libellé « Accueil » moins une marge.
NAV=$(center_of 'text="Accueil"') && SAFE_Y=$((${NAV#* } - H / 8))
echo "zone touchable : y < $SAFE_Y"

log "1 bis. APK release : laboratoire (le moteur optimisé par R8, sans service d'accessibilité)"
tap_match 'text="Réglages"'
sleep 3
texts | head -5
scroll_tap 'text="Laboratoire"'
sleep 3
scroll_tap 'Lancer les'
sleep 8
scroll_texts 30 > "$OUT/laboratoire-release.txt"
grep -E "Résultat :|✓|✗" "$OUT/laboratoire-release.txt" | sort | uniq -c
shot 1-laboratoire-release
grep -q "✓ Conforme au résultat attendu" "$OUT/laboratoire-release.txt"; check "release : laboratoire, résultats conformes" $?
! grep -q "✗" "$OUT/laboratoire-release.txt"; check "release : laboratoire, aucune acceptation ni écart" $?
# Les autres onglets s'ouvrent aussi dans l'APK optimisé.
for tab in Applications Historique Compatibilité Accueil; do
  tap_match "text=\"$tab\"" >/dev/null
  sleep 3
done
shot 1-onglets-release
a logcat -d -b crash | grep -q "$PKG" && check "release : aucun plantage (laboratoire et onglets)" 1 || check "release : aucun plantage (laboratoire et onglets)" 0

log "2. APK debug + appli de démonstration"
a uninstall $PKG >/dev/null
a install -r -g app/build/outputs/apk/debug/app-debug.apk
a shell cmd locale set-app-locales $PKG --locales fr-FR >/dev/null 2>&1 || true  # textes vérifiés en français
a install -r demo/build/outputs/apk/debug/demo-debug.apk
a logcat -c
SERVICE="$PKG/$CLS.service.RefusBotAccessibilityService"
bound=1
for attempt in 1 2 3 4; do
  a shell settings put secure enabled_accessibility_services "$SERVICE"
  a shell settings put secure accessibility_enabled 1
  sleep 8
  if a shell dumpsys accessibility | grep -A2 "Bound services" | grep -q "RefusBot"; then bound=0; break; fi
  echo "service pas encore lié (essai $attempt) : désactivation / réactivation"
  a shell settings put secure enabled_accessibility_services ""
  sleep 3
done
if [ $bound -ne 0 ]; then
  a shell settings get secure enabled_accessibility_services
  a shell dumpsys accessibility | grep -iE "Bound services|Enabled services|Crashed|refusbot" | head -10
  a logcat -d | grep -iE "refusbot|AccessibilityManagerService" | tail -20
fi
check "service d'accessibilité activé et lié" $bound

log "3. Onglets et sélection de l'appli de démonstration"
a shell am start -W -n $PKG/$CLS.MainActivity >/dev/null
sleep 4
tabs=0
for entry in "Historique|historique|refus vérifié" "Compatibilité|compatibilite|Comment lire ce bilan" "Réglages|reglages|Profil de refus"; do
  IFS='|' read -r tab file expected <<< "$entry"
  tap_match "text=\"$tab\"" && sleep 2
  texts > "$OUT/onglet-$file.txt"
  shot "3-$file"
  grep -q "$expected" "$OUT/onglet-$file.txt" || { echo "onglet $tab : « $expected » absent"; tabs=1; }
done
check "onglets Historique, Compatibilité et Réglages affichés" $tabs
# Le diagnostic automatique est désactivé par défaut : on l'active comme le ferait l'utilisateur,
# en passant par l'avertissement.
scroll_tap 'text="Diagnostic automatique"'
sleep 2
texts > "$OUT/avertissement-diagnostic.txt"
grep -q "TEXTE affiché" "$OUT/avertissement-diagnostic.txt"; check "diagnostic automatique : avertissement affiché avant activation" $?
tap_match 'text="Activer quand même"'
sleep 2
tap_match 'text="Applications"'
sleep 4
# Sans clavier : on fait défiler la liste jusqu'à l'appli de démonstration.
scroll_tap 'text="fr.refusbot.demo"' 30
check "appli de démonstration trouvée dans l'onglet Applications" $?
sleep 2
shot 3-applications
tap_match 'text="Accueil"'
sleep 2
texts > "$OUT/accueil-apres-selection.txt"
grep -q "1 appli surveillée" "$OUT/accueil-apres-selection.txt"; check "appli de démonstration cochée (1 appli surveillée)" $?

log "4. Capture diagnostic (tuile « Capturer l'écran ») : état des cases dans la WebView"
# Page sans bouton connu : RefusBot (actif) ne fait rien, mais la WebView publie son arbre.
TILE="$PKG/$CLS.tile.CaptureTileService"
demo inspection
sleep 6
a shell cmd statusbar add-tile "$TILE" >/dev/null 2>&1
sleep 2
a shell cmd statusbar click-tile "$TILE"
sleep 7
a shell cmd statusbar collapse >/dev/null 2>&1
FILE=$(a shell run-as $PKG ls -t files/diagnostics 2>/dev/null | head -1 | tr -d '\r')
echo "fichier : $FILE"
if [ -n "$FILE" ]; then
  a shell run-as $PKG cat "files/diagnostics/$FILE" > "$OUT/diagnostic-inspection.json"
  python3 - "$OUT/diagnostic-inspection.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
print("déclencheur:", d.get("trigger"), "| analyse:", json.dumps(d.get("analysis", {}), ensure_ascii=False)[:900])
rows = []
toggle_nodes = []
def walk(n, depth=0):
    t = (n.get("text") or "")[:24]
    if n.get("checkable") or "CheckBox" in n["class"] or "Switch" in n["class"]:
        rows.append(f'{depth:2} {n["class"]:<24} text="{t}" state="{n.get("stateDescription")}" checkable={n["checkable"]} checked={n["checked"]}')
        toggle_nodes.append(n)
    for ch in n.get("children", []):
        walk(ch, depth + 1)
walk(d["root"])
print(f'{d.get("nodeCount")} nœuds, {len(rows)} cases :')
print("\n".join(rows[:10]))
def contains_forbidden_extra(n):
    if "extras" in n or "webRole" in n:
        return True
    return any(contains_forbidden_extra(ch) for ch in n.get("children", []))
assert not contains_forbidden_extra(d["root"]), "Bundle extras / rôle ARIA désérialisé présent dans le diagnostic"
assert toggle_nodes, "aucun contrôle natif cochable reconnu dans la capture"
html = [r for r in rows if 'text="Consentement"' in r or 'text="Intérêt légitime"' in r]
hidden = bool(html) and all("checkable=False" in r for r in html)
open(sys.argv[1] + ".etat", "w").write("1" if hidden else "0")
# Contenu réellement vérifié : bonne appli, arbre non vide, cases de la page présentes, aucun champ de saisie lu.
assert d.get("format") == "refusbot-diagnostic-1", d.get("format")
assert d.get("package") == "fr.refusbot.demo", d.get("package")
assert d.get("trigger") == "tuile", d.get("trigger")
assert d.get("nodeCount", 0) >= 5 and d["root"].get("children"), "arbre vide"
assert html, "cases « Consentement » / « Intérêt légitime » absentes de la capture"
PY
  CAPTURE_OK=$?
  WEBVIEW_STATE_HIDDEN=$(cat "$OUT/diagnostic-inspection.json.etat" 2>/dev/null || echo 0)
  echo "état des cases HTML masqué par la WebView : $WEBVIEW_STATE_HIDDEN"
  check "capture diagnostic : fichier de la bonne appli, arbre et cases présents" "$CAPTURE_OK"
else
  check "capture diagnostic par le service" 1
  context
fi
demo inspection
sleep 5
I=$(texts | grep -m1 "INSPECTION" || true)
D=$(texts | grep -m1 "DOM_ETAT" || true)
echo "$I"
echo "$D"
[[ "$I" == *"li_on=3"* && "$I" == *"li_total=3"* ]]
check "inspection : les trois cases restent cochées" $?
[[ "$D" == *"native_checked=3/3"* && "$D" == *"native_unchecked=0/1"* && "$D" == *"aria_switch_true=true"* && "$D" == *"aria_switch_false=false"* && "$D" == *"aria_checkbox_true=true"* && "$D" == *"aria_checkbox_false=false"* && "$D" == *"aria_pressed_true=true"* && "$D" == *"aria_pressed_false=false"* ]]
check "inspection : états DOM de référence HTML/ARIA" $?
echo "--- même page vue par uiautomator :"
dump | tr '>' '\n' | grep -E 'CheckBox|Switch|ARIA' | sed -E 's/ (index|resource-id|package|focusable|focused|long-clickable|password|selected|scrollable)="[^"]*"//g' | head -8
a shell am force-stop $DEMO
a shell input keyevent 3
sleep 6 # laisser le service se reconnecter après uiautomator

log "5. Formulaire Google UMP : SDK officiel, aucun HTML simulé"
demo ump
sleep 35
UMP_TEXT=$(texts)
printf '%s\n' "$UMP_TEXT" | tee "$OUT/5-google-ump.txt"
a shell logcat -d -s GoogleUMP:I GoogleUMP:W GoogleUMP:E | tee "$OUT/5-google-ump-sdk.log"
shot 5-google-ump
if grep -q "INFO_UPDATED" "$OUT/5-google-ump-sdk.log"; then
  echo "Le SDK Google UMP a récupéré les informations de consentement."
  if grep -q "FORM_AVAILABLE" "$OUT/5-google-ump-sdk.log"; then
    unverified "formulaire UMP réel disponible : état individuel des finalités non exposé par le SDK"
    if grep -q "FORM_FLOW_COMPLETED" "$OUT/5-google-ump-sdk.log"; then
      echo "Le cycle du formulaire est terminé; cela ne prouve pas le refus des finalités."
    else
      echo "Le formulaire est disponible; vérifier la capture et les actions du service."
    fi
  elif grep -q "FORM_NOT_AVAILABLE" "$OUT/5-google-ump-sdk.log"; then
    unverified "aucun formulaire UMP publié ou disponible pour l’identifiant AdMob de test"
  else
    unverified "disponibilité du formulaire UMP non déterminée"
  fi
elif grep -q "INFO_UPDATE_FAILED" "$OUT/5-google-ump-sdk.log"; then
  unverified "requête Google UMP échouée : consulter le journal SDK"
  context
else
  unverified "initialisation du SDK Google UMP non confirmée dans les journaux"
  context
fi
log "6. Scénario bandeau : refus direct"
sleep 4
demo bandeau
sleep 15
R=$(result)
echo "$R"
shot 6-bandeau
[[ "$R" == *"mode=refus-direct"* && "$R" == *"accepte=0"* ]]
check "bandeau : « Tout refuser » cliqué" $?
[[ "$R" == *"refus-direct"* ]] || context

log "7. Scénario sans refus possible : aucun clic"
sleep 4
demo accepte
sleep 18
T=$(texts)
echo "$T" | grep -E "EN ATTENTE|RESULTAT"
shot 7-accepte
echo "$T" | grep -q "EN ATTENTE aucun clic" && ! echo "$T" | grep -q "RESULTAT"
check "aucun refus possible : RefusBot n'a rien cliqué" $?
a shell dumpsys notification --noredact | grep -E "android.title=|android.text=" | grep -iE "refusbot|consentement" | head -6
a shell run-as $PKG ls files/diagnostics | tee "$OUT/diagnostics.txt"
[ "$(wc -l < "$OUT/diagnostics.txt")" -ge 2 ]; check "diagnostic automatique enregistré après l'échec" $?

log "8. Historique"
a shell am force-stop $DEMO
a shell am start -W -n $PKG/$CLS.MainActivity >/dev/null
sleep 3
tap_match 'text="Historique"'
sleep 3
shot 8-historique
scroll_texts 10 | tee "$OUT/historique.txt"
grep -qE "interrupteurs vérifiés désactivés|Refus partiel|Refus impossible" "$OUT/historique.txt"; check "historique : résultat explicite (vérifié, partiel ou impossible)" $?
# Le résultat UMP dépend des contrôles réellement exposés : PARTIEL ou IMPOSSIBLE sont tous deux
# possibles. Le statut global du SDK ne doit jamais forcer un succès vérifié.

grep -q "Refusé avec le bouton du site (non vérifiable)" "$OUT/historique.txt"; check "historique : bandeau annoncé comme refus cliqué NON vérifiable" $?
! grep -q "Refus direct réussi" "$OUT/historique.txt"; check "historique : plus aucun « refus direct réussi »" $?
grep -q "Refus impossible" "$OUT/historique.txt"; check "historique : refus impossible journalisé" $?

log "8 bis. L'historique survit au redémarrage du processus (service relancé sans ouvrir l'appli)"
# Régression de la 1.0.0 : la première fenêtre traitée après un redémarrage écrasait l'historique.
a shell input keyevent 3
sleep 2
if timeout 30 adb root >/dev/null 2>&1 && timeout 60 adb wait-for-device; then
  sleep 3
  PID=$(a shell pidof $PKG | tr -d '\r')
  echo "processus RefusBot : $PID"
  [ -n "$PID" ] && a shell kill -9 "$PID"
  rebound=1
  for attempt in 1 2 3 4 5 6; do
    sleep 5
    NEW=$(a shell pidof $PKG | tr -d '\r')
    if [ -n "$NEW" ] && [ "$NEW" != "$PID" ] && a shell dumpsys accessibility | grep -A2 "Bound services" | grep -q "RefusBot"; then
      rebound=0
      break
    fi
  done
  if [ $rebound -eq 0 ]; then
    echo "service relancé par Android (processus $NEW)"
    sleep 5
    demo bandeau
    sleep 20
    R=$(result)
    echo "$R"
    [[ "$R" == *"mode=refus-direct"* && "$R" == *"accepte=0"* ]]
    check "après redémarrage : bandeau refusé sans ouvrir RefusBot" $?
    a shell am force-stop $DEMO
    a shell am start -W -n $PKG/$CLS.MainActivity >/dev/null
    sleep 4
    tap_match 'text="Historique"'
    sleep 3
    scroll_texts 10 > "$OUT/historique-apres-redemarrage.txt"
    grep -q "Refus impossible" "$OUT/historique-apres-redemarrage.txt" &&
      grep -qE "interrupteurs vérifiés désactivés|Refus partiel" "$OUT/historique-apres-redemarrage.txt"
    check "après redémarrage : l'historique précédent est conservé" $?
  else
    unverified "historique après redémarrage (Android n'a pas relancé le service à temps)"
  fi
else
  unverified "historique après redémarrage (accès root indisponible)"
fi

log "9. Tableau de bord et arrêt d'urgence"
a shell am start -W -n $PKG/$CLS.MainActivity >/dev/null
sleep 3
tap_match 'text="Accueil"'
sleep 3
scroll_texts 6 > "$OUT/accueil-final.txt"
grep -E "refus vérifié|Activité|Derniers événements" "$OUT/accueil-final.txt" | sort -u | head -8
grep -q "refus vérifié" "$OUT/accueil-final.txt"; check "accueil : statistiques d'activité affichées" $?
# Retour en haut de la page : l'onglet est rouvert depuis un autre.
tap_match 'text="Historique"'; sleep 2; tap_match 'text="Accueil"'; sleep 2
scroll_tap 'text="Arrêt d'
sleep 4
shot 9-arret-urgence
scroll_texts 3 > "$OUT/accueil-arret.txt"
grep -q "Protection en pause" "$OUT/accueil-arret.txt"; check "arrêt d'urgence : protection mise en pause" $?

log "Journal Android de RefusBot"
a logcat -d -s RefusBot:* | tail -100 | tee "$OUT/logcat-refusbot.txt"
a logcat -d -b crash > "$OUT/crash.txt"
if grep -q "$PKG" "$OUT/crash.txt"; then cat "$OUT/crash.txt"; check "aucun plantage" 1; else check "aucun plantage" 0; fi

echo
echo "Points non vérifiés (limites constatées, ni conformes ni en échec) : $UNVERIFIED"
echo "Vérifications échouées : $FAILS"
exit "$FAILS"
