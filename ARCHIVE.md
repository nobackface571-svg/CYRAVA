# Archives historiques — RefusBot

## Source active

Le développement se fait uniquement à partir de `main`.

Le 18/11/2026, à la demande du propriétaire, tout le travail a été réuni dans `main` (avance rapide
jusqu'au commit `3da5a5a`, celui des APK testés par CodeMagic n°15 : 200 tests au vert, lint sans
anomalie). Toutes les anciennes branches étaient déjà entièrement contenues dans `main` ; seule
`claude/test-fusion-2026-11-18` (essai de fusion) ne l'est pas, mais son contenu utile l'est.

## Anciennes branches (supprimées le 18/11/2026)

Le propriétaire a supprimé ces branches obsolètes ; seule `main` existe. Leur dernier commit
est noté ci-dessous : il reste dans l'historique de `main`, donc une branche peut être recréée à
tout moment à partir de ce numéro.

| Ancienne branche | Dernier commit |
| --- | --- |
| `claude/corrections-2026-11-18` | `3da5a5a4b0` |
| `claude/diagnostic-webview-api36` | `4e13331f07` |
| `claude/diagnostic-webview-state-api35` | `4ab1d5a27e` |
| `claude/fix-webview-toggle-state` | `5002c15851` |
| `claude/refusbot-android-app-o10b1v` | `c23ce089dd` |
| `claude/test-fusion-2026-11-18` | `3f2e30e93d` |
| `consolidation/claude-base-2026-11-18` | `9813a986af` |
| `consolidation/integrated-claude-gpt-2026-11-18` | `118c6b32df` |
| `copilot/clauderefusbot-android-app-o10b1v` | `72625505b5` |
| `dependabot/github_actions/actions/checkout-7.0.1` | `0f5137a6fe` |
| `dependabot/github_actions/actions/setup-java-6.2.0` | `b2adde781c` |
| `dependabot/github_actions/gradle/actions/setup-gradle-6.4.1` | `522293771c` |
| `diagnostic/webview-state-api35` | `efe5ec90c1` |
| `fix/verified-toggle-state-audit` | `7835764674` |
| `gpt/phase1-radio` | `4c4cf8296f` |
| `gpt/security-hardening-20261116` | `12df5f92ca` |
| `refusbot-ultra-clean` | `3f4621940c` |

Les tags (`v1.0.0`…) et les releases ne sont pas touchés.

## Règles

- Ne jamais développer à partir d'une ancienne branche ; toute reprise exige une demande explicite
  du propriétaire.
- Nouvelle branche de travail : `claude/<sujet>` ou `gpt/<sujet>`, partie de `main` à jour, et
  supprimée une fois fusionnée.
- En cas de doute, utiliser uniquement `main` et signaler le doute au propriétaire.

La protection GitHub de `main` n'est volontairement pas activée pendant la phase actuelle de
corrections ; elle sera réévaluée avant une publication publique.
