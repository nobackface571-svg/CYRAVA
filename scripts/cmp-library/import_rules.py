#!/usr/bin/env python3
"""
Importe dans rules.json (section « buttonIds ») les identifiants de boutons que CYRAVA peut
voir à l'écran, à partir de deux projets libres de refus des cookies :

- DuckDuckGo Autoconsent (MPL-2.0) : https://github.com/duckduckgo/autoconsent
  → clics des étapes « optOut » désignés par un identifiant simple (#identifiant).
- Consent-O-Matic, université d'Aarhus (MIT) : https://github.com/cavi-au/Consent-O-Matic
  → clics des méthodes OPEN_OPTIONS et SAVE_CONSENT désignés par un identifiant simple.

Chrome transmet l'attribut HTML « id » au service d'accessibilité comme identifiant de vue :
c'est la seule partie de ces règles que CYRAVA peut utiliser (pas de CSS, pas de JavaScript).

Sécurité (un identifiant ne suffit jamais à lui seul à autoriser un clic dans l'appli) :
- tout identifiant qui évoque une acceptation (accept, agree, allow…) est écarté, sauf « disagree » ;
- fermer / masquer n'est pas refuser : ces identifiants sont écartés ;
- un identifiant trop générique (« save », « settings »…) est écarté ;
- un identifiant classé dans deux catégories différentes est écarté ;
- dans l'appli, un texte affiché d'acceptation l'emporte toujours sur l'identifiant, le garde-fou
  s'applique au clic, et seuls les boutons d'une fenêtre de consentement reconnue sont lus.

Usage : python3 scripts/cmp-library/import_rules.py CHEMIN_AUTOCONSENT CHEMIN_CONSENT_O_MATIC
Le script réécrit app/src/main/assets/rules.json (les identifiants manuels sont conservés).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RULES = ROOT / "app/src/main/assets/rules.json"

SIMPLE_ID = re.compile(r"^(?:button|a|input|span|div)?#([A-Za-z][\w-]*)$")
ACCEPT_LIKE = re.compile(r"accept|(?<!dis)agree|allow|approv|consent-?yes|gotit|got-it|okay|\bok\b|enable|opt-?in|aceptar|akzept|zustimm|accett|aceitar", re.I)
# Cases à cocher, interrupteurs, curseurs : un clic pourrait ACTIVER un consentement.
TOGGLE_LIKE = re.compile(r"checkbox|chk|toggle|switch|slide|radio", re.I)
CLOSE_LIKE = re.compile(r"close|dismiss|hide|cancel|later|banner-?x|x-?button", re.I)
REFUSE_LIKE = re.compile(r"reject|decline|deny|denied|refus|disagree|opt-?out|necessary|essential|ablehn|rechaz|rifiut|weiger|nein|deny", re.I)
OPTIONS_LIKE = re.compile(r"manage|setting|preferen|pref|custom|learn-?more|more-?options|options|detail|config|personali|purpose", re.I)
CONFIRM_LIKE = re.compile(r"save|confirm|submit|apply|update", re.I)
# Rôle douteux d'après son nom complet (lien de bandeau classé « validation »).
EXCLUDED = {"controlled_submit-cookie_consent-consent_banner-privacy_setting_banner_link"}

GENERIC = {
    "save", "settings", "manage", "submit", "confirm", "options", "reject", "decline", "close",
    "button", "btn", "ok", "apply", "update", "details", "preferences", "customize", "customise",
    "apply-button", "continue", "submit-button", "save-button",
}


def simple_id(selector):
    if not isinstance(selector, str):
        return None
    m = SIMPLE_ID.match(selector.strip())
    return m.group(1).lower() if m else None


def safe(identifier):
    return (
        len(identifier) >= 5
        and identifier not in GENERIC
        and not ACCEPT_LIKE.search(identifier)
        and not CLOSE_LIKE.search(identifier)
        and not TOGGLE_LIKE.search(identifier)
    )


def autoconsent_optin_ids(path):
    """Identifiants cliqués dans les étapes d'ACCEPTATION (optIn) : jamais importés."""
    ids = set()

    def walk(steps):
        for st in steps or []:
            if not isinstance(st, dict):
                continue
            for key in ("click", "waitForThenClick"):
                ident = simple_id(st.get(key))
                if ident:
                    ids.add(ident)
            for key in ("then", "else", "any"):
                walk(st.get(key))

    for f in (Path(path) / "rules" / "autoconsent").glob("*.json"):
        try:
            walk(json.loads(f.read_text(encoding="utf-8")).get("optIn"))
        except (ValueError, OSError):
            continue
    return ids


def consent_o_matic_accept_ids(path):
    """Identifiants cliqués dans les actions « trueAction » (consentement donné) : jamais importés."""
    ids = set()

    def collect(node, accepting):
        if isinstance(node, dict):
            target = node.get("target")
            if accepting and node.get("type") == "click" and isinstance(target, dict):
                ident = simple_id(target.get("selector"))
                if ident:
                    ids.add(ident)
            for key, value in node.items():
                collect(value, accepting or key == "trueAction")
        elif isinstance(node, list):
            for value in node:
                collect(value, accepting)

    for f in (Path(path) / "rules").glob("*.json"):
        try:
            collect(json.loads(f.read_text(encoding="utf-8")), False)
        except (ValueError, OSError):
            continue
    return ids


def autoconsent_ids(path):
    found = {}

    def walk(steps):
        for st in steps or []:
            if not isinstance(st, dict):
                continue
            for key in ("click", "waitForThenClick"):
                ident = simple_id(st.get(key))
                if ident:
                    yield ident
            for key in ("then", "else", "any"):
                yield from walk(st.get(key))

    for f in sorted((Path(path) / "rules" / "autoconsent").glob("*.json")):
        try:
            rule = json.loads(f.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            continue
        for ident in walk(rule.get("optOut")):
            if not safe(ident):
                continue
            if REFUSE_LIKE.search(ident):
                kind = "refuse"
            elif CONFIRM_LIKE.search(ident):
                kind = "confirm"
            elif OPTIONS_LIKE.search(ident):
                kind = "options"
            else:
                continue
            found.setdefault(ident, set()).add(kind)
    return found


def consent_o_matic_ids(path):
    found = {}
    role = {"OPEN_OPTIONS": "options", "SAVE_CONSENT": "confirm"}
    # Le nom de l'identifiant doit lui-même confirmer le rôle (« manage », « save »…) : un
    # identifiant muet (« cookie-all », « iubfooterbtn ») peut désigner un bouton d'acceptation.
    confirms = {"options": OPTIONS_LIKE, "confirm": CONFIRM_LIKE}

    def clicks(node):
        if isinstance(node, dict):
            target = node.get("target")
            if node.get("type") == "click" and isinstance(target, dict):
                ident = simple_id(target.get("selector"))
                if ident and not target.get("textFilter"):
                    yield ident
            for value in node.values():
                yield from clicks(value)
        elif isinstance(node, list):
            for value in node:
                yield from clicks(value)

    for f in sorted((Path(path) / "rules").glob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            continue
        for name, rule in data.items():
            if name.startswith("$") or not isinstance(rule, dict):
                continue
            for method in rule.get("methods", []):
                kind = role.get(method.get("name"))
                if not kind:
                    continue
                for ident in clicks(method.get("action")):
                    if safe(ident) and confirms[kind].search(ident) and not REFUSE_LIKE.search(ident):
                        found.setdefault(ident, set()).add(kind)
    return found


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    rules = json.loads(RULES.read_text(encoding="utf-8"))
    manual = {k: list(v) for k, v in rules.get("manualButtonIds", rules.get("buttonIds", {})).items()}
    manual_ids = {i for ids in manual.values() for i in ids}

    merged = {}
    for source in (autoconsent_ids(sys.argv[1]), consent_o_matic_ids(sys.argv[2])):
        for ident, kinds in source.items():
            merged.setdefault(ident, set()).update(kinds)

    optin = autoconsent_optin_ids(sys.argv[1]) | consent_o_matic_accept_ids(sys.argv[2])
    imported = {"refuse": [], "options": [], "confirm": []}
    dropped = 0
    for ident in sorted(merged):
        kinds = merged[ident]
        if ident in manual_ids:
            continue
        if ident in optin or ident in EXCLUDED:
            dropped += 1  # utilisé ailleurs pour accepter, ou rôle douteux
            continue
        if len(kinds) != 1:
            dropped += 1  # rôle ambigu (ex. le même bouton ouvre et enregistre)
            continue
        imported[next(iter(kinds))].append(ident)

    final = {}
    for kind in ("refuse", "globalReject", "options", "confirm"):
        ids = list(manual.get(kind, [])) + imported.get(kind, [])
        if ids:
            final[kind] = ids
    rules["manualButtonIds"] = manual
    rules["buttonIds"] = final
    counts = {k: len(v) for k, v in imported.items()}
    rules["_buttonIds"] = (
        "Identifiants techniques de boutons (minuscules, comparés au dernier segment de l'identifiant de vue). "
        "« manualButtonIds » : liste vérifiée à la main (Didomi, OneTrust, Cookiebot, Axeptio). "
        f"« buttonIds » = manuels + importés automatiquement par scripts/cmp-library/import_rules.py : "
        f"{counts['refuse']} refus, {counts['options']} ouvertures de préférences, {counts['confirm']} validations, "
        f"{dropped} écartés (rôle ambigu ou identifiant aussi utilisé pour accepter). Sources : DuckDuckGo Autoconsent (MPL-2.0) et Consent-O-Matic (MIT), "
        "voir docs/cmp-library/IMPORT_REGLES.md. Jamais d'acceptation : un texte d'acceptation l'emporte toujours."
    )
    RULES.write_text(json.dumps(rules, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"importés : {counts}, écartés : {dropped}")


if __name__ == "__main__":
    main()
