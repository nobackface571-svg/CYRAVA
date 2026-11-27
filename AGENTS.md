# AGENTS.md — consignes de travail sur le projet

Ce fichier est la référence partagée ; `CLAUDE.md` y renvoie. Lire aussi `ARCHIVE.md` avant toute intervention Git.
Le projet : **CYRAVA** (identifiant de l'appli `fr.cyrava` ; paquets du code Kotlin `fr.refusbot`), appli Android qui refuse les fenêtres de cookies dans les applis choisies.

## Règles non négociables

1. **Jamais d'acceptation.** Aucun clic sur une acceptation, un achat, un abonnement. État inconnu,
   cible modifiée ou doute = **aucune action**. Seule exception (propriétaire, 18/11/2026) :
   « Autoriser la sélection », formule exacte fixée dans le code, quand tous les interrupteurs ont
   été vus et sont refusés (`core/.../SelectionConfirm.kt`).
2. **Jamais de succès sans preuve.** La fermeture d'une fenêtre ne prouve rien (`UNVERIFIED`) ; un
   interrupteur d'état inconnu n'est jamais compté comme désactivé (`PARTIAL` si une partie a été vérifiée, sinon `IMPOSSIBLE` ou `HANDED_OVER`). Seul `OPTIONS_SUCCESS` repose sur une observation.
3. **Aucun réseau** : pas de permission `INTERNET`, pas de télémétrie, pas de SDK publicitaire.
4. **Vie privée** : seules les applis cochées sont lues ; rien n'est lu dans un champ de saisie ou de
   mot de passe (texte, description, enfants) ; le texte d'écran ne va que dans les diagnostics.
5. **Publication** : une Release exige une clé personnelle. `build.yml` et `e2e.yml` n'ont ni secret
   ni droit d'écriture ; ne jamais y remettre l'un ou l'autre. Actions épinglées à un SHA.
7. **Dépenses GitHub Actions** : aucun job GitHub Actions ne doit démarrer sans accord explicite du propriétaire pour cette exécution. Chaque workflow contient l'entrée booléenne `authorize_paid_usage`, obligatoire et à `false` par défaut ; chaque job doit être conditionné à `inputs.authorize_paid_usage == true`. Ne jamais cocher/activer cette autorisation à la place du propriétaire. Une demande générale de compilation, de test ou de correction ne vaut pas autorisation de dépense. Seule une demande explicite d'utiliser le budget payant pour l'exécution concernée l'autorise. Garder le budget Actions à 5 $ avec arrêt automatique et alertes. Cette protection concerne les workflows de ce dépôt ; le budget GitHub est global au compte.

6. **Honnêteté** : ne jamais écrire qu'un test a eu lieu sur un vrai téléphone s'il n'est pas consigné dans
   `docs/essais-reels.md` (essais du propriétaire) ; dire clairement ce qui n'a pas été exécuté. Pas de statistique inventée.

## Façon de travailler

- L'utilisateur travaille **uniquement depuis un téléphone**, n'est pas développeur : répondre en
  **français simple**, ne rien exiger qui demande un ordinateur.
- Lire le code et les tests avant de modifier. Un problème corrigé = un test de régression.
- Source de vérité active : `main` (toutes les branches y ont été réunies le 18/11/2026, voir `ARCHIVE.md`). Ne pas reprendre ni fusionner d'anciennes branches sans demande explicite.
- Les anciennes branches sont obsolètes (liste et derniers commits dans `ARCHIVE.md`) : aucun développement, cherry-pick ou réactivation sans demande explicite. Ne pas supprimer les tags de version ni les releases.
- Ne pas supprimer de tests. Ne pas élargir le périmètre. Pas de PR ni de fusion sans demande.
- Messages de commit en français (`core :`, `app :`, `ci :`, `docs :`, `test(e2e) :`).

## Qui fait quoi

Depuis le 20/11/2026, le propriétaire travaille **uniquement avec Claude** (Claude Code) : plus de GPT,
Codex ni Copilot sur ce projet.

| Rôle | Qui | Ce qu'il fait |
|---|---|---|
| Auteur des modifications | Claude (Claude Code) | Code, tests, documentation, sur `main`. |
| Relecture sécurité | Agent `.claude/agents/relecteur-securite.md` | Relit chaque modification du moteur, du service ou des workflows ; ne modifie rien. |
| Vérifications | Agent `.claude/agents/verificateur.md` | Lance les tests et rapporte fidèlement ce qui passe, échoue ou n'a pas été exécuté. |

Chaque constat s'écrit ainsi : **fichier:ligne — scénario concret — correction proposée — test**.
Un constat n'est « corrigé » que lorsqu'un test de régression existe et que les vérifications passent.
En cas de doute, la règle la plus prudente (ne pas cliquer, ne pas annoncer de succès) l'emporte, et
l'utilisateur tranche.

## Structure

- `core/` : moteur en Kotlin pur (règles, garde-fou `SafetyGuard`, `ScreenAnalyzer`, `RefusalEngine`,
  `RulesValidator`, `TargetCheck`, laboratoire). Tous les tests JUnit sont ici.
- `app/` : appli Android (service d'accessibilité, Compose). `demo/` : appli de test pour l'émulateur.
- Voir `ARCHITECTURE.md`, `SECURITY.md`, `CONTRIBUTING.md`.

## Vérifications

```sh
bash scripts/installer-sdk-android.sh      # une fois (il faut l'accès à dl.google.com)
./gradlew test :app:lintRelease :app:assembleRelease
bash scripts/verifier-workflows.sh         # séparation des droits, actions épinglées
bash scripts/verifier-signature.sh         # publication refusée sans clé personnelle
```

Compilation, tests et lint : CodeMagic (`codemagic.yaml`), **lancé à la main** par le propriétaire. GitHub Actions reste manuel et chaque job exige `authorize_paid_usage: true` (false par défaut) après accord explicite du propriétaire ; sinon aucun runner ne démarre. Numéro de version : `cyrava.version` dans `gradle.properties`, seul endroit à changer. Le test sur émulateur (`.github/scripts/e2e.sh`) ne tourne que sur GitHub. Si un texte de l'interface change, mettre ce script à jour.
