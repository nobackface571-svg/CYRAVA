🇬🇧 **English** | 🇫🇷 [Français](IMPORT_REGLES.fr.md)

# Importing rules from open-source projects (20/11/2026)

CYRAVA only reads the screen (accessibility service): it cannot run JavaScript or CSS
selectors like a browser extension. The only information it shares with these
extensions is a button's **HTML identifier**, which Chrome passes on as a view identifier.

`scripts/cmp-library/import_rules.py` therefore extracts, from the rules of two open-source
projects, the buttons designated by a simple identifier, and adds them to
`app/src/main/assets/rules.json` > `buttonIds`.

| Source | Licence | Version read | What is taken |
| --- | --- | --- | --- |
| [DuckDuckGo Autoconsent](https://github.com/duckduckgo/autoconsent) | MPL-2.0 | `d6cf4b9` | taps of the rejection steps (`optOut`) |
| [Consent-O-Matic](https://github.com/cavi-au/Consent-O-Matic) (Aarhus University) | MIT | `8ca8500` | "open preferences" and "save" taps |

Only **identifiers** (technical names) are taken, with their role: no code. Their
origin is recorded in `rules.json` (`_buttonIds`).

## Result
- 1,245 Autoconsent rules read: 133 designate buttons by identifier; the rest depend on the
  page's code and remain out of CYRAVA's reach.
- Imported: 50 rejections, 42 preference openings, 44 confirmations (in addition to the
  hand-checked identifiers: Didomi, OneTrust, Cookiebot, Axeptio).

## Safety filters
1. Discarded: any identifier suggesting an acceptance (accept, agree, allow…), except "disagree".
2. Discarded: any identifier **used elsewhere to accept** (Autoconsent `optIn` steps,
   Consent-O-Matic `trueAction` actions). E.g. tarteaucitron: `tarteaucitronPersonalize2` =
   accept, `tarteaucitronPersonalize` = open preferences; the comparison is exact.
3. Discarded: checkboxes and switches (checkbox, toggle, switch…), close / hide,
   identifiers that are too generic, identifiers with a double role.
4. For Consent-O-Matic, the identifier's name must itself confirm its role ("manage",
   "save"…).
5. In the app: a displayed accept text always takes precedence over the identifier; the
   safety guard applies to the tap; only the buttons of a recognised consent pop-up are
   read; an imported rules file can neither add nor remove identifiers.

Tests: `core/src/test/kotlin/fr/refusbot/core/ButtonIdsSafetyTest.kt`.

## Updating
```
git clone --depth 1 https://github.com/duckduckgo/autoconsent /tmp/autoconsent
git clone --depth 1 https://github.com/cavi-au/Consent-O-Matic /tmp/com
python3 scripts/cmp-library/import_rules.py /tmp/autoconsent /tmp/com
```
Then run the engine tests again.
