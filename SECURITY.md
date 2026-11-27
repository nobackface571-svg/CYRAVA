🇬🇧 **English** | 🇫🇷 [Français](SECURITY.fr.md)

# Security and privacy

CYRAVA uses an accessibility service: this is a powerful permission. This document describes what the app forbids itself, how these rules are enforced, and what remains your responsibility.

## What the app never does

| Rule | How it is enforced |
|---|---|
| **No network communication** | The APK does not have the `INTERNET` permission. The build fails if it appears (`build.yml`, step "Vérifier la présence des APK compilés"; `codemagic.yaml`, step "Vérifier qu'aucun APK de l'application principale n'a la permission INTERNET"), and the emulator test checks it again on the installed APK. No network, advertising or analytics library is included. |
| **Never accept** | Every tap goes through the safety guard (`core/.../SafetyGuard.kt`), twice: at decision time, then right before the action. An element whose text contains a form of acceptance, purchase or subscription is blocked. The only exceptions are the rejection phrases shipped with the app ("Continue without accepting"…), compared word for word, and, since 18/11/2026 with the owner's approval, "Allow selection" ("Autoriser la sélection"; exact phrases fixed in the code, `SelectionConfirm.kt`), only when every switch has been seen, is in a known state and is rejected. |
| **No action on a screen that has changed** | Before each tap, expansion or press, the actual target is re-read (`TargetCheck`): app, label, position, foreground window. At the slightest difference, nothing is done; no press is ever sent to an old coordinate. Checked by engine tests, not yet by an emulator test that changes the screen at the last moment. |
| **No success without proof** | The pop-up closing does not prove that the app saved the rejection: it gives "rejection tapped, result unverifiable". A single switch in an unknown state gives "partial rejection". |
| **Nothing when in doubt** | Without a recognised reject button, or if a button is ambiguous, CYRAVA does not tap and logs "Rejection impossible". |
| **No reading outside the chosen apps** | Android only passes events from the ticked apps to the service (list updated on every change); the service re-checks the app name before any analysis. While paused, it receives nothing. |
| **No reading of accessibility data marked sensitive** | On Android 14+, a node flagged as sensitive by the source app, and its subtree, are excluded from capture, analysis, diagnostics and actions. Input and password fields remain protected on all supported versions. |
| **No passwords or input** | The text, description and internal elements of input and password fields are discarded at capture time (`SnapshotNode`), before any analysis or saving. |
| **No screen text in the history** | The history only contains the result, counters, the recognised platform and a generic reason. Screen text only exists in diagnostic files. |
| **No screen image** | CYRAVA never takes a screenshot. The "diagnostic" saves the structure of the pop-up (buttons, texts), on the phone. |
| **No forced re-enabling** | If you turn off the service or refuse a permission, the app tells you but never tries to restore it by itself. |
| **No made-up figures** | Statistics and compatibility are worked out only from the history stored on the phone. |

## Reading the steps of a try

The history distinguishes the following steps:

| Field | What it proves | What it does not prove |
|---|---|---|
| Observation recorded | A local entry was added to the log. | That a banner was recognised. |
| Banner detected | The consent criteria (clues and a known button) were met. | That every control was understood. |
| Controls recognised | Buttons, switch candidates or sections were identified in the accessibility tree. | That their state is known or that they can be acted on. |
| Actions attempted | The engine handed operations to the Android service. | That Android accepted them. |
| Actions performed | Android accepted the operation or gesture. | That the CMP saved the choice. |
| State verified | The observed checkable controls are off after processing. | That cookies are absent or deleted. |

A switch candidate without isCheckable stays UNKNOWN, even if its description says "Off". It is never acted on and, without an explicit rejection control, confirming the preferences is blocked. A truncated capture never allows handling the options or claiming a complete rejection.

From the Apps tab, the test button can open a monitored app (for example Chrome); CYRAVA does not navigate to a URL. The user opens the site and the banner themselves. The service only works on the ticked packages and only when the service is on. In the Observation profile, no action is produced.

The OneTrust and Didomi names come from technical viewId markers or explicitly recognised phrases. If these clues are not exposed by the source app, the platform may stay unknown or generic. The package name never allows the visited domain to be deduced.

## Imported rules files

A rules file only contains word lists and numbers: nothing in it is executed. Before being used, it goes through `core/.../RulesValidator.kt`:

1. **Safety sections always come from the app.** The shipped safety guard is kept (a file can only add forbidden forms to it). All switch handling — which views are switches, which states mean "on" or "off", which labels must not be touched — stays the app's own. A file can therefore neither make an "on" switch pass for "off", nor prevent it from being turned off.
2. **Positive proof for every added phrase.** A tappable phrase absent from the shipped rules must contain a recognised keyword for its category (reject, options, confirm, vendors, back), no forbidden form and no word resembling an acceptance ("activer", "continuer", "ja"…). No new exception to the safety guard is allowed.
3. **Limits.** File size, nesting depth, number of phrases, action and time limits.

At the slightest doubt the file is rejected and nothing changes. The previous version remains available to roll back. Eleven tests (`ImportSafetyTest`) build booby-trapped files and check that they are rejected.

Accepted limitation: this check relies on word lists in 7 languages. It is deliberately strict (it also rejects some legitimate phrases), but it does not replace caution: **only import files from a trusted source.**

## Permissions requested

| Permission | Use |
|---|---|
| Accessibility service | Read the pop-up of ticked apps and tap the reject buttons. |
| Notifications (optional) | Tell you about a rejection, a failure or the service stopping. |
| Ignore battery optimisation (optional) | Prevent the system from stopping the service. |
| Visibility of launchable apps | Show the list in the Apps tab (no access to the full list of installed apps). |

## Data stored on the phone

Settings, history (500 entries at most), diagnostic files (30 at most), imported rules. Everything is in the app's private storage, and can be deleted from **Settings › Stored data** or by uninstalling the app. Exports (history, settings, diagnostics) only leave the phone if you share them yourself.

## APK signing and release

- **Test APKs** (CodeMagic): signed with a test key stored in CodeMagic (`cyrava_test`), never in the repository; elsewhere (local build, emulator), with the "debug" key generated by the machine. They do not authenticate the author; their version always has a suffix (`1.1.0-dev.42`).
- **Release**: Gradle refuses to prepare an `x.y.z` version without a complete personal key, and refuses the test key (`scripts/verifier-signature.sh` checks this on every build). A key is never generated during the build, never silently replaced.
- **Separation of rights**: `build.yml` and `e2e.yml` reference no secret and have `contents: read`. `publication.yml` is limited to manual runs by the owner, on `main`, with the `publication` environment and the right to create a Release. `developpement.yml` is manual, reserved for the owner and limited exclusively to `main`, with the `development` environment; it publishes nothing. Development secrets must be defined only in the `development` environment, never as repository/organisation secrets. The `development` environment must be limited to `main`. Release secrets stay only in the `publication` environment, also limited to `main` and protected by a mandatory approval. `scripts/verifier-workflows.sh` checks the workflow code, not GitHub's actual settings.
- GitHub actions are pinned to a full SHA.
- Version 1.0.0, older than these rules, is signed with the test key.

Not verified and not configurable by this repository: the secrets actually stored in GitHub, the environment's branch rules, the mandatory approval and whether it can be bypassed. The owner must set them and remove any signing secrets at repository/organisation level before any release.

## Consent preference audit — scope and limits

CYRAVA can observe the visible choices of a CMP when the accessibility tree publishes them. This observation does not prove that the CMP saved these preferences.

CYRAVA **does not read** Chrome's cookie database, another browser's internal storage, HTTP cookies, their values or their content. Android isolates apps' private data; the accessibility service gives no access to these databases. The package name identifies the browser, not the site currently visited. A domain must never be deduced from the app name alone.

A future register may distinguish, for each category, `ALLOWED`, `REJECTED` or `UNKNOWN`, with date, source and confidence. Sources to study are the state explicitly exposed by a CMP, an export voluntarily provided by the user, or a page opened in a browser controlled by the user. Non-checkable ARIA roles, truncated captures and contradictory signals stay `UNKNOWN`. This register will describe the **preferences declared by the CMP**, never the cookies actually present or their content.

## What remains your responsibility

- Only tick the apps you need: the service can read the screen of monitored apps.
- Read a diagnostic file before sharing it: it contains the text of the captured pop-up.
- Some online games forbid accessibility services (anti-cheat): pause CYRAVA or set these games to "Observe only".
- The app has been checked on an emulator, on simulated pop-ups and during tries on a real phone (Samsung Galaxy S23 FE, Android 16, 22–23/11/2026 — see `docs/essais-reels.md`, in French); every site and every CMP version remain different: keep an eye on the history during the first few days.

## Reporting a security issue

Open an issue (**Issues** tab) in the repository, without pasting any diagnostic file that contains personal information.
