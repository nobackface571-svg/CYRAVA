🇬🇧 **English** | 🇫🇷 [Français](protocole-test-telephone.fr.md)

# Real-phone test protocol

This protocol is for testing CYRAVA on a real phone, with real cookie pop-ups. It separates what can be **observed** from what **cannot be verified**. Every step is done from the phone.

## 0. What these tests can prove, and what they cannot

| What can be observed | What cannot be verified from the phone |
|---|---|
| CYRAVA recognises the pop-up (History: platform, confidence). | That the site **saved** the rejection on its server. |
| What it tapped: navigation buttons, switches. | That cookies **already set** were deleted: rejecting does not erase existing cookies. |
| Whether each switch was **re-read as off** after the tap ("turned off and verified"). | The state of a box whose state the page does not publish ("unreadable state, not touched"). |
| That no acceptance was tapped (none of your choices switched to "accepted"). | How another version of Chrome or Android behaves. |

A tap is never proof. Only the "turned off and verified" line in the history means a state change was re-read.

## 1. Preparation (once)

1. Install the latest version: testers, from the **Releases** tab of the GitHub repository; maintainer, the test APK from the latest **CodeMagic** build (**Artifacts** section, `app-release.apk` file). Note the version (file name or the app's Settings). For installation and permissions (Play Protect, restricted settings, accessibility), follow the [tester guide](FICHE-TESTEURS.md).
2. **Apps** tab:
   - **only** tick Chrome (and possibly one or two apps with a known cookie pop-up);
   - untick sensitive apps (banking, passwords, authentication, government, messaging) and games with anti-cheat.
3. **Settings › Rejection profile › Strict.** In the "Observation" profile, CYRAVA never taps: nothing can be tested.
4. **Home › Don't optimise CYRAVA**, then on Samsung: *Battery › Background usage limits › Never sleeping apps*.
5. **Settings › Automatic diagnostics: on** during the tests only. Turn it off at the end.
6. **History › Clear**, to start from an empty history.

## 2. For each site tested

In Chrome, **in incognito mode**: a site that has already received your choice no longer shows its pop-up.

1. Open the page and wait 10 seconds without touching the screen.
2. Take a **screenshot before** the action, if the pop-up had time to appear, and one **after**.
3. Open CYRAVA › **History** and take a screenshot of the site's row.
4. If the result is not "switches verified off": Settings › Diagnostic centre › **Share** the latest file.
5. **Manual cross-check**: reopen the page, open the "Cookies" or "Manage my preferences" link at the bottom of the page, and take a screenshot of the state of the boxes. This is the only way, from the phone, to see the state the site kept.

**After a failure, wait 10 minutes before trying the same page again:** CYRAVA does not retry the same pop-up before then.

## 3. Representative sites to cover

Choose at least one site per row. These are examples to check: sites change provider without notice.

| Provider | What is tested | Example observed on 17/11 |
|---|---|---|
| OneTrust | "Reject all" on the first level, switches per category | recettes.lidl.fr |
| Didomi | "Continue without accepting", options, switches | to be found in the history (11 Didomi events) |
| Axeptio | Animated pop-up, scrolling | to be found in the history (2 Axeptio events) |
| Google (Funding Choices / UMP) | Options and "legitimate interest" | news sites using Google |
| Pop-up with no possible rejection | No tap expected | any "pay or accept" site |

## 4. Result sheet (one row per site)

| Field | Where to read it |
|---|---|
| Site, date, time | — |
| APK version | name of the installed file |
| CYRAVA result | History, first line |
| Switches: detected / initially on / already off / turned off and verified / left on / unreadable | History, report line |
| State observed manually (step 2.5) | screenshot |
| CYRAVA ↔ manual check match | yes / no / impossible to say |
| Diagnostic file shared | yes / no |

A test is **successful** only if: the result is "switches verified off" (or "rejection tapped, unverifiable" for a simple "Reject all"), and the manual check shows the boxes turned off. Any other combination must be reported as is, without interpretation. Copy each sheet into [`essais-reels.md`](essais-reels.md) (results log, in French).

## 5. What not to conclude

- "The pop-up disappeared", so it is rejected: wrong, closing does not prove it was saved.
- "CYRAVA performed N actions", so N boxes were turned off: wrong. "Navigation taps" count buttons and scrolls, not boxes.
- "Cookies were deleted": CYRAVA deletes no cookies, it only rejects new consents.
