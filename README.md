🇬🇧 **English** | 🇫🇷 [Français](README.fr.md)

# CYRAVA

[![Download the latest version](https://img.shields.io/github/v/release/nobackface571-svg/CYRAVA?include_prereleases&label=Download%20the%20latest%20version&style=for-the-badge&color=1f6feb)](https://github.com/nobackface571-svg/CYRAVA/releases)

**CYRAVA automatically rejects cookie / tracker consent pop-ups (GDPR, IAB TCF)** in the Android apps, games and browsers you choose: it taps "Reject all", or opens the options, **turns off every switch (consent AND legitimate interest)** and confirms. **It never taps "Accept".**

- Recognises 38 consent platforms (Google UMP, Didomi, OneTrust, Sourcepoint, InMobi Choice, Usercentrics, Sirdata, Axeptio, Cookiebot, TrustArc, Consentmanager, iubenda, CookieYes, Commanders Act…) and generic "TCF" pop-ups, in 7 languages (French, English, German, Spanish, Italian, Portuguese, Dutch).
- App interface in **English or French** (Settings › Language). The engine's detailed explanations and exported files stay in French.
- No Internet permission: nothing leaves the phone, except a file you share yourself.
- Only reads the apps you tick.
- Reports only what it actually observed: a rejection is called "verified" only if the switches were seen turned off. A simple tap on "Reject all" followed by the pop-up closing is reported as **"rejected with the site's button, unverifiable"**: CYRAVA cannot see what the app saved.
- Everything is done from the phone: installation, settings, updates. No computer needed.
- Free software under the **GPL-3.0** licence: the code is public and anyone can check it (see [License](#license)).
- **Developed with the help of Claude**, Anthropic's AI assistant (code, tests and documentation), under the direction of its author, who also tests it.

## The guarantee: CYRAVA never taps "Accept all"

This is the project's rule no. 1, and it does not depend on any setting:

- **In every profile** (Careful, Strict, Observation), CYRAVA never taps an accept, purchase or subscription button.
- **Every tap goes through a safety guard** ([`SafetyGuard.kt`](core/src/main/kotlin/fr/refusbot/core/SafetyGuard.kt)), checked twice: when the decision is made, then right before the action. Any button or switch whose text contains *accepter, accept, agree, autoriser, allow, consentir, s'abonner, acheter…* is blocked, unless it **exactly** matches a known rejection phrase ("Continue without accepting", "I do not accept"…).
- **When in doubt, CYRAVA does nothing**: the pop-up stays open and you decide.
- **An imported rules file cannot bypass the safety guard**: it is rejected if it contains the slightest accept word ([`RulesValidator.kt`](core/src/main/kotlin/fr/refusbot/core/RulesValidator.kt)).
- **Automated tests check this on every code change** (`SafetyGuardTest`, `SafetyRegressionTest`, `ButtonIdsSafetyTest`…, in [`core/src/test`](core/src/test/kotlin/fr/refusbot/core)).

A single exception, deliberate and fixed in the code: "Allow selection" ("Autoriser la sélection"), only when **every** switch has been seen rejected (the button then saves your rejection).

If you ever see CYRAVA tap an accept button, it is a serious bug: please report it in the **Issues** tab with the diagnostic file (section 8).

> **What has been tested, honestly.** On every build started on CodeMagic, the decision-engine tests, code analysis (lint) and the build are run, and the absence of the Internet permission is checked. The full test on a **simulated phone** (Android emulator, `.github/workflows/e2e.yml`) is no longer run: GitHub Actions is no longer used. **CYRAVA has also been tried on a real phone** (Samsung Galaxy S23 FE, Android 16), in the Strict profile, in Chrome, Firefox, Opera and a game, on 22/11/2026: 20 pop-ups around 2 pm, 17 around 3 pm, then 14 around 4:45 pm. Results and durations are copied into [docs/essais-reels.md](docs/essais-reels.md) (in French), successes and failures alike. See the [limitations](#12-precautions-and-known-limitations).

## Latest updates (26/11/2026)

What changed, and why (details in [CHANGELOG.md](CHANGELOG.md)):

**From the evening of 23/11 to 26/11** — checked by the engine tests, not yet tried on the phone:

- **Independent consent verification**: before calling a rejection "verified", CYRAVA re-evaluates all the evidence itself. A switch in an unknown state is never counted as turned off; an incomplete capture or exploration can no longer give "verified". The diagnostic now names each control that was not verified.
- **Fallback to direct rejection**: when opening the settings makes the pop-up disappear in an unknown app, the site's "Reject all" can be used again; "preferences first" also applies outside browsers.
- **Privacy**: captured diagnostic exports have been removed from the repository, along with the tests that depended on them.
- **Language switch without a black screen** or app restart.

**Late afternoon of 22/11, after a third try on the phone (4:42 – 4:50 pm)** — not yet tried on the phone:

- **Didomi in Opera**: the bottom of the pop-up ("Reject all", "Save") was hidden under Opera's bottom bar, and "Save" stayed greyed out until each purpose had a choice: nothing moved for 12 s. These two buttons are now triggered directly (as a screen reader would, never by a blind tap), with the same checks as a visible button. If "Save" stays greyed out, the site's "Reject all" finishes the job (result "unverifiable").
- **"The window closed without confirmation"** (Didomi in Firefox, Sourcepoint in Opera): after scrolling, the pop-up title went off screen and CYRAVA thought the pop-up was closed; and a settings page slow to appear was taken for a closure after 0.75 s. CYRAVA now recognises the pop-up by its platform's identifier and waits about 2 s.
- **Galaxy Defense** (5:20 pm): the vendor list refuses to scroll, but all its rows can be read at once. CYRAVA now turns off the 81 "legitimate interest" switches left on, checks, then confirms, instead of stopping on "scrolling impossible".
- **Consentmanager in Firefox** (settings split into sub-pages, nothing readable): with "Full rejection", CYRAVA goes back to the first screen and taps its "Reject all" instead of failing; on the next sites using this platform, it taps "Reject all" directly (until the phone restarts). The sub-pages themselves are not yet explored one by one.

**Evening of 22/11, after a second try on the phone (3:05 – 3:16 pm), then an independent audit**:

- **Never saves without having seen everything**, whatever the setting: if part of the list or a category was not seen, CYRAVA uses the site's "Reject all", or taps nothing. Before, with the "Don't confirm when in doubt" setting turned off, it could save choices it had never seen (possibly accepted).

- **OneTrust "with tabs"** (Opera, Chrome): settings are grouped by category and each switch only appears when its category is tapped. CYRAVA saw no settings ("no accessible preference control recognised"). It now opens each category, turns it off, then confirms the selection; if a category could not be opened, confirmation is left to you.
- **Didomi with "Reject / Accept" buttons**: in Firefox, the "Reject" choices were not recognised ("a checked radio choice is not a recognised rejection"). In Chrome, when everything was already on "Reject", the "Save" button stayed below the screen ("scrolling impossible"): it is now brought on screen, then tapped.
- **AppConsent**: confirmation button found more reliably (more phrases recognised, button below the screen brought on screen). No diagnostic of this pop-up was available: to be confirmed by a new try.
- **Faster screen reading**: during a long list, CYRAVA only re-reads the cookie pop-up, not the whole page behind it (up to 1,900 elements re-read at each step in Opera). As a precaution, it re-reads the whole screen after every tap that is not a switch-off or a scroll, at least once every 5 reads, and always before closing the pop-up (confirmation or rejection). New measured duration: "lectureFenetre".

**Earlier on 22/11**:

- **Very long lists are scrolled to the bottom** (continuous scrolling, screen after screen): before, CYRAVA stopped after 50 scrolls, around the 400th row, and the rejection stayed partial. It now stops when nothing new appears, and the result is never "verified" if it has not seen everything.
- **Lists of more than 200 items in Chrome**: every scroll was reported as "blocked", so never any confirmation. Fixed.
- **Faster**: the phone tries showed that reading and analysing the screen took most of the time (up to 78 s for 367 boxes). The analysis is **6 times faster** (same result, checked on the 12 diagnostics from the tries), the wait before starting is capped at 1.5 s (up to 6 s before), and the measurement of each scroll is lighter.
- **"Save" buttons found more reliably**: "Enregistrer la configuration des cookies" (Commanders Act) and buttons placed below the list of choices (Didomi in Opera) were not seen: "confirmation button not found".
- **Duration of each step measured** (screen reading, analysis, switching off, scrolling, waits) in the history export and diagnostics, to find out where the time goes.
- **Version number** shown correctly in the app, and **CodeMagic** now only builds when started by hand.

---


## Independent consent verification (Consent Verification Engine)

The `ConsentVerificationEngine` module re-evaluates the accessibility evidence before classifying a rejection:
- a switch in an unknown state is never considered turned off;
- an optional switch still on prevents verification;
- an incomplete capture or exploration prevents verification;
- a selected "Allow" radio choice prevents verification;
- "necessary / always active" switches are only exempted if the analyser explicitly classified them as such.

## Contents

1. [Install the APK from your phone](#1-install-the-apk-from-your-phone)
2. [Allow "restricted settings" (Android 13+)](#2-allow-restricted-settings-android-13)
3. [Turn on the accessibility service](#3-turn-on-the-accessibility-service)
4. [Stop Samsung from shutting CYRAVA down](#4-stop-samsung-from-shutting-cyrava-down)
5. [Choose the apps to monitor](#5-choose-the-apps-to-monitor)
6. [Everyday use](#6-everyday-use)
7. [The Careful, Strict and Observation profiles](#7-the-three-profiles-and-possible-results)
8. [Diagnostic centre and lab](#8-diagnostic-centre-and-lab)
9. [Backup, export and rules files](#9-backup-export-and-rules-files)
10. [FAQ and troubleshooting](#10-faq-and-troubleshooting)
11. [APK signing and development](#11-apk-signing-and-development)
12. [Precautions and known limitations](#12-precautions-and-known-limitations)
13. [License](#license)

---

## 1. Install the APK from your phone

> **Testers**: the [tester guide](docs/FICHE-TESTEURS.md) sums up every installation and testing step in a few minutes of reading.

**Before installing: turn off Google Play Protect.** CYRAVA is not on the Play Store and uses accessibility: Play Protect is very likely to block the installation. In the **Play Store** app: **profile picture › Play Protect › ⚙️ (gear)**, turn off **"Scan apps with Play Protect"**. Once CYRAVA is installed and turned on, you can turn it back on in the same place; if it shows a warning about CYRAVA, choose to keep the app.

**Testers: official version**: open the **Releases** tab of this GitHub repository from your phone's browser and download the `.apk` file of the latest version. Each Release is signed with the maintainer's personal key. Only install CYRAVA from this page: an APK found elsewhere may have been modified.

**Maintainer: test APK**: open the latest successful **CodeMagic** build ("tests et APK de vérification" workflow), **Artifacts** section, and download `app-release.apk`. It is signed with the test key stored in CodeMagic: it updates over previous test APKs, but not over an official version (different keys).

Installation:

1. Once the APK is downloaded, tap the download notification (or **My Files › Downloads**) then the `.apk` file.
2. If Android shows *"… is not allowed to install unknown apps from this source"*: tap **Settings**, turn on **Allow from this source**, then go back.
3. Tap **Install**.
   - If **Play Protect** still shows a warning: **More details › Install anyway** (the app is not published on the Play Store, this is normal). If it blocks, turn it off as described above.
   - On recent Samsung phones, if installation is blocked by **Auto Blocker**: **Settings › Security and privacy › Auto Blocker**, turn it off during installation, then turn it back on.

> **Updates**: install the new version over the old one. Settings and history are kept as long as the signing key stays the same.

## 2. Allow "restricted settings" (Android 13+)

For an app installed outside the Play Store, Android greys out the accessibility service until you allow it. CYRAVA's **Home** tab shows this guide and shortcut buttons.

1. In CYRAVA, tap **Open accessibility settings** › **Installed apps** › **CYRAVA – Digital Privacy**. If the switch is greyed out or a **"Restricted setting"** message appears, tap **OK**.
2. Go back to CYRAVA and tap **Open app info** (or: **Settings › Apps › CYRAVA**).
3. Tap **⋮** (top right) › **Allow restricted settings**, then confirm with your PIN / fingerprint.

> The "Allow restricted settings" menu only appears **after** a first attempt to turn on the service (step 1).

## 3. Turn on the accessibility service

1. **Settings › Accessibility › Installed apps › CYRAVA – Digital Privacy** (or the **Open accessibility settings** button in the app).
2. Turn on the switch and accept Android's warning (it is generic: CYRAVA only reads the ticked apps and has no Internet access).
3. Back in CYRAVA, the Home screen no longer shows "Protection off".

Once the service is on, **you no longer need to open CYRAVA**: it works in the background.

### After a restart, an update or turning it off

- **Restart**: Android automatically restarts enabled accessibility services; nothing to do. If Home shows the service as stopped, see the next section.
- **Update** (new APK installed over the old one): the service stays on, your settings and history are kept.
- **Turning off the service** (Settings › Accessibility): CYRAVA stops immediately and reads nothing more. Turn it back on in the same place whenever you like; CYRAVA never turns it back on by itself.

## 4. Stop Samsung from shutting CYRAVA down

One UI readily stops background services:

1. In CYRAVA (Home tab, Battery card), tap **Don't optimise CYRAVA** and accept.
2. **Settings › Battery › Background usage limits › Never sleeping apps › +** › tick **CYRAVA**.
3. Optional: **Settings › Apps › CYRAVA › Battery › Unrestricted**.

If Home says the service is switched on but stopped, turn the accessibility service off and on again.

## 5. Choose the apps to monitor

1. **Apps** tab: find the app, game or browser, then turn on its switch.
2. For each ticked app, choose its mode:
   - **Automatic rejection**: CYRAVA acts, according to the profile chosen in Settings;
   - **Observe only**: CYRAVA analyses the pop-up and logs it in the history, but **touches nothing**.
3. Filters: **Monitored only** and **Hide system apps**. **Tick all** applies to the apps shown.
4. **Only** tick apps that show cookie pop-ups: CYRAVA receives **no** events from other apps.

> **Browsers**: use **Chrome** or **Firefox**. **Samsung Internet is not supported**: it does not pass page content to accessibility services. The Apps tab says so and "Tick all" does not tick it.

> ⚠️ **Games with anti-cheat**: some games can detect accessibility services and react (warning, disconnection, even a ban). Only monitor what you need and pause CYRAVA before starting these games.

## 6. Everyday use

The app has five tabs:

| Tab | What it is for |
|---|---|
| **Home** | Protection status (on, paused, to set up…), **Pause / Resume protection** button, **Emergency stop**, activity (successful rejections, chart), latest events, service status, battery, tiles. |
| **Apps** | Choose the monitored apps and their mode, see each one's last result. |
| **History** | Every pop-up handled: app, date, result, recognised platform, confidence level, switch details. Filters, search, export (with the duration of each step, in milliseconds). |
| **Compatibility** | Each app's summary (compatible, partial, to check, not supported…), worked out from its last 5 real pop-ups. |
| **Settings** | Language, profile, notifications, theme, diagnostic centre, lab, rules, backup, data deletion, privacy. |

- **Pause / resume**: Home button, or the **CYRAVA** quick tile. While paused, no pop-up is handled.
- **Emergency stop**: immediately stops the current action and pauses protection. Nothing is deleted.
- **Notifications** (Settings › Notifications): *All*, *Failures only*, *Interruptions only* or *None*.
- **Statistics**: worked out only from your phone's history. No figure is estimated or made up.
- **Sites already handled**: CYRAVA remembers them until the phone restarts.

### Quick tiles

1. Add them once: on Android 13+, with the Home buttons; otherwise pull down the quick settings panel, tap **✎** and drag **CYRAVA** and **Capture screen** into the active area.
2. **CYRAVA**: one tap = pause / resume.
3. **Capture screen**: open the app on its cookie pop-up, pull down the panel and tap the tile; the structure of the pop-up is saved (section 8). It is not an image: CYRAVA never takes a screenshot.

## 7. The three profiles and possible results

### Profiles (Settings › Rejection profile)

| Profile | Behaviour |
|---|---|
| **Careful** (default for a new installation) | Only acts if the pop-up is recognised with **certainty** (identified platform or many clues). Otherwise it observes without acting. A pop-up that looks like none of those already rejected in the app must be approved in the Compatibility tab. |
| **Strict** | Rejects every optional choice it recognises; only confirms with a safe confirmation button. |
| **Observation** | Never acts: analyses pop-ups and logs them in the history. |

Additional setting **"Don't confirm when in doubt"** (**on by default**, leave it on): if some boxes could not be read or turned off, CYRAVA does not save your choices and lets you finish by hand. When off, it may save an incomplete rejection ("Partial rejection").

In every case, **CYRAVA never saves your choices if it has not seen everything** (list not read to the bottom, category or partners page not opened): it then uses the site's "Reject all" if there is one, otherwise it taps nothing.

In **every** profile, CYRAVA never taps an accept button.

### Results (History tab)

| Result | Meaning |
|---|---|
| **Rejected in preferences: switches verified off** | Options opened, switches turned off then re-read as off, choices confirmed. The only result backed by an observation. |
| **Rejected with the site's button (unverifiable)** | A reject button was tapped and the pop-up closed. Closing does not prove that the app saved the rejection. |
| **Partial rejection** | The rejection was confirmed, but not everything could be checked or turned off (a single box whose state is not published is enough). Only happens if "Don't confirm when in doubt" is off, or after a "Reject all" used because not everything could be read. |
| **Finish by hand** | "Don't confirm when in doubt" setting: the pop-up is left open for you to finish. |
| **Incomplete capture** | The screen could not be fully read. No rejection is established. |
| **Rejection impossible** | No safe reject button ("pay or accept" wall, unknown wording…). Nothing was tapped. |
| **Action interrupted** | Pause, emergency stop, or you left the app during processing. |
| **Observed (no action)** | Observation or Careful profile: the pop-up was only analysed. |

### How CYRAVA decides

1. A pop-up is recognised if it contains **at least 2 clues** (cookies, consent, partners, legitimate interest, platform-specific identifiers…) **and at least 1 known button**.
2. **Direct rejection** if available: "Reject all", "Continue without accepting", "Tout refuser", "Alle ablehnen"…
3. Otherwise **opening the settings** ("Manage options", "Customise", "Paramétrer"…). On the purposes screen, **global rejection first** ("Reject all", "Disable all", master switch), **only then** each purpose still accepted (its "Reject" or its switch, with the state re-read). Long lists are scrolled to the bottom. Settings **grouped in tabs by category** (OneTrust): each category is opened in turn (a tab saves nothing).
4. **Partners / vendors**: the link ("See our partners", "Vendors"…) is opened and only the **global rejection** is tapped there ("All partners › Block", "Reject all"); no partner is expanded or set one by one. When this global rejection is selected, the list is considered rejected.
5. **Confirmation** ("Save", "Confirm my choices", "Save & Exit"…), **only if every choice has been seen**. If the button is not visible, CYRAVA brings it on screen or **scrolls to the bottom** of the pop-up before concluding it is missing. A "Save" or "Reject all" stuck under the browser bar (zero height, Opera) is triggered directly through accessibility, never by a blind tap. A "Save" greyed out by the site is never touched: the site's "Reject all" is then used as a fallback.

This path is **the same for every platform** (Didomi, Sourcepoint, Axeptio, OneTrust, Quantcast, Cookiebot, unknown ones…): each platform only brings its own labels and screen landmarks.

**Never tapped**, on any platform: expandable sections ("+", "-", "›" rows, chevrons, "Data categories", "Device storage", "View user info", "Legitimate interest" heading…), "Learn more" / "See more" / "Details" inside the settings, partner names, links to the privacy policy or an external page, and of course any acceptance.

6. **Safety guard** on every tap (checked at decision time then right before the action): any element whose text contains *accepter, accept, agree, autoriser, allow, consentir, s'abonner, acheter…* is blocked, unless it **exactly** matches a known rejection phrase, or it is a rejection worded with a real negation right before the verb ("I do not accept", "Do not allow", "Continue without accepting"). "pas" ("not" in French) on its own does not count as a negation. Only approved exception: "Allow selection" when every switch has been seen rejected. When in doubt, CYRAVA does nothing.
7. **"Accept or pay" walls**: recognised, nothing is tapped ("Rejection impossible").
8. **Anti-loop**: the same element is **never tapped twice**, with a single exception: a switch turned off while it was still below the screen (Chrome), then re-read **still on** once visible, gets a second and last try, which can only turn it off. Never a third tap. If the screen does not change after a tap, CYRAVA moves on to the next step. Number of actions and duration are limited per pop-up; after a failure, the same pop-up is not retried straight away.

## 8. Diagnostic centre and lab

### Diagnostic centre (Settings › Diagnostic centre)

If a pop-up is not recognised or not fully rejected, save its structure so new rules can be added:

1. Make sure the app is **ticked** (only monitored apps can be captured).
2. **Automatic** capture ("Automatic diagnostics" turned on) or **manual**: **Capture in 5 seconds**, or the **Capture screen** tile.
3. Tap **Share** on the file you want. The file is named `diagnostic-<app>-<date>.json`. **Share all as one .zip file** sends every diagnostic and the history at once.
4. The **System status** card sums up the installation without any personal data.

The file **contains the text shown by the app at that moment**, but never the content of input and password fields. It stays on the phone as long as you do not share it.

### Lab (Settings › Lab)

Simulated pop-up scenarios to check the decision engine on the phone, without touching any app. The real Google UMP form is tested separately by the demo app: see [docs/testing/GOOGLE_UMP_E2E.md](docs/testing/GOOGLE_UMP_E2E.md) (in French).

## 9. Backup, export and rules files

- **Settings backup** (Settings › Settings backup): exports / restores profile, notifications, theme and the list of monitored apps (no history, no screen text).
- **History export** (History tab): **JSON** or **CSV** (readable in a spreadsheet).
- **Rules files** (Settings › Detection rules): you can import a newer rules file. It is checked before use: the safety guard and switch handling **always** stay the app's own, no added phrase can contain an accept word, and at the slightest doubt the file is rejected. Nothing it contains is executed. See [docs/cmp-library/IMPORT_REGLES.md](docs/cmp-library/IMPORT_REGLES.md).
- **Delete all data** (Settings › Stored data): erases settings, history, diagnostics and imported rules.

## 10. FAQ and troubleshooting

**Do I need to keep CYRAVA open?** No. Once the service is on, it works in the background, even after a restart.

**Does it drain the battery?** Very little: Android only wakes CYRAVA up when a ticked app changes screen.

**Nothing happens in an app.** Check, in this order:
1. the app is ticked and not set to "Observe only";
2. Home shows "Protection on";
3. the profile is not "Observation"; in the Careful profile, the pop-up must be recognised with certainty;
4. the browser is not Samsung Internet;
5. otherwise, make a capture in the diagnostic centre: this pop-up's wording is probably unknown.

**When uninstalling, I still see `fr.refusbot`.** That is the old beta 1 (identifier `fr.refusbot`), still installed next to the new one (`fr.cyrava`): for Android, they are two different apps. Uninstall the one showing `fr.refusbot`. The source code folders are still named `fr.refusbot`, but this is not visible anywhere on the phone.

**"Partial rejection": is it serious?** The rejection was saved but not everything could be checked. The reason is in the history. To avoid it, leave "Don't confirm when in doubt" on: you will then finish the rare doubtful pop-ups yourself.

**The Compatibility tab says "To check".** The last pop-up looks like none of those already rejected. If it looks normal to you, tap **Approve the new layout**.

## 11. APK signing and development

### Two signatures, never mixed

- **Test APKs** (CodeMagic): signed with a **test key** stored in CodeMagic under the name `cyrava_test` (Teams › Code signing identities › Android keystores). **No key is stored in this public repository.** Without this key (local build, GitHub emulator), the "debug" key generated by Android on the machine is used.
- **APK signed with your key**: CodeMagic workflow **"CYRAVA - APK signé avec ma clé (manuel)"**, which checks the personal key's fingerprint and the absence of the Internet permission. The GitHub workflows (🔐 development, 🚀 official release) still exist but have not been used since 21/11 (no more GitHub Actions credit). Signing secrets are stored only in CodeMagic (or in the intended GitHub environments), never in the code or in a message.

### Publishing a new version for testers (maintainer, from the phone)

1. Check the version number in [`gradle.properties`](gradle.properties) (`cyrava.version`, for example `1.1.0-beta.3`): it is the only place to change, and it must be new.
2. On CodeMagic, run **"CYRAVA - tests et APK de vérification"** on `main`: everything must be green.
3. Then run **"CYRAVA - APK signé avec ma clé (manuel)"** on `main`, and download the APK from **Artifacts**.
4. On GitHub, create the Release: <https://github.com/nobackface571-svg/CYRAVA/releases/new>. Tag `v` + the number (for example `v1.1.0-beta.3`), target `main`, title "CYRAVA 1.1.0 bêta 3", notes taken from the [changelog](CHANGELOG.md), APK attached, **"Set as a pre-release"** box ticked for a beta, then **Publish release**.
5. In the changelog, move the "À venir (non publié)" (upcoming, unreleased) part under the heading of the new version.

> ⚠️ Changing the key requires **uninstalling** CYRAVA **once** (Android refuses an update signed with another key). Keep the original keystore and a backup off the phone.

### For developers

```
core/   Pure Kotlin (no Android dependency): normalisation, rules, safety guard, screen analysis,
        state machine, imported-rules validation, lab, diagnostic export. Tested on the JVM.
app/    Android: accessibility service, Compose / Material 3, DataStore, tiles, history.
        Interface strings: res/values (French) and res/values-en (English).
app/src/main/assets/rules.json        Phrases in 7 languages and platform profiles.
app/src/main/assets/cmp-catalog.json  Platform catalogue (checked by scripts/cmp-library/validate_catalog.py).
demo/   Test app: real Google UMP SDK + generic CMP pop-ups in a WebView.
```

- App identifier: `fr.cyrava` (since beta 2). The Kotlin code packages remain `fr.refusbot` (Gradle `namespace`), which changes nothing for users.
- Tests: `./gradlew test` — full check: `./gradlew test :app:lintRelease :app:assembleRelease`.
- Builds and tests, started by hand: `codemagic.yaml`. Emulator test: `.github/workflows/e2e.yml` and `.github/scripts/e2e.sh`.
- See also [ARCHITECTURE.md](ARCHITECTURE.md) (in French), [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md) and [CHANGELOG.md](CHANGELOG.md).
- Code comments, commit messages and internal notes are in French.

## 12. Precautions and known limitations

**Precautions**
- An accessibility service can read the screen of the apps it monitors: only tick the apps you need, and only install the APK published in this repository's **Releases**.
- Diagnostic files contain screen text: read them and only share them with whoever you choose.
- The history never contains screen text: only the date, app, result, recognised platform, counters and a generic reason.
- Only import a rules file if it comes from a trusted source.

**Limitations**
- **Tests**: automated on simulated pop-ups, and manual tries on **a single** real phone (Samsung Galaxy S23 FE) recorded in [docs/essais-reels.md](docs/essais-reels.md). Every site is different: behaviour is confirmed through use, with the history and the diagnostic centre.
- **Duration**: a simple rejection takes a few seconds; with "Full rejection", a very long list of partners can take up to 2 minutes (maximum time per pop-up).
- **Samsung Internet**: not supported (see section 5).
- **Boxes in a web page whose state is not published**: CYRAVA does not know whether a tap would turn the box on or off; to be safe it **does not touch them** and the result is **Partial rejection**.
- **Interfaces drawn in a game engine** (Unity/Unreal without Android views or WebView): invisible to accessibility, so not handled.
- **Unknown wording or languages**: only the phrases in the rules are recognised. An unknown pop-up is never tapped at random.
- **Very large pop-ups**: only partly read; CYRAVA can at most tap a visible "Reject all", once, and reports the result as "unverifiable".
- **"Pay or accept" walls**: no free rejection option → nothing is tapped.
- One UI **power saving**: may stop the service without warning; follow section 4.
- **Anti-cheat** in some online games: see section 5.

## License

CYRAVA is free software, © 2026 NoBackFace, distributed under the **GNU General Public License v3.0** (full text: [LICENSE](LICENSE)).

In short: you may use, study, modify and redistribute CYRAVA. If you distribute a modified version, you must publish it under the same licence, with its source code. The software comes with no warranty.

The name "CYRAVA" and its logo identify this project: please give a modified version another name.
