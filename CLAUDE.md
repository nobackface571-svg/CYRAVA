# CYRAVA (RefusBot) — consignes pour les sessions Claude

Règles du projet et répartition des rôles (Claude uniquement depuis le 20/11/2026) : voir
`AGENTS.md` et `ARCHIVE.md` (les anciennes branches et versions sont des archives en lecture seule). Agents Claude du projet : `.claude/agents/relecteur-securite.md` et `verificateur.md`.

## Conditions de travail de l'utilisateur

- Tout se fait **depuis un téléphone Android** (Samsung S23 FE), sans ordinateur.
  Ne jamais demander une action qui exige un PC. Ne pas déclencher de fenêtre d'approbation
  (modification d'environnement, saisie de secret…) : elles ne se valident pas depuis le téléphone.
- Toujours répondre en **français**, avec des mots simples et sans jargon de développement ;
  les sous-agents aussi.
- Donner des points d'étape courts et répondre aux questions posées avant de continuer le travail.
- Naviguer sur le site GitHub depuis le téléphone est très difficile : pour chaque action à faire
  sur GitHub ou CodeMagic, donner l'adresse (URL) complète et cliquable de la page exacte.

## Règles de sécurité non négociables

- Aucune permission `INTERNET`, aucune télémétrie, aucun SDK publicitaire : rien ne sort du
  téléphone, sauf un fichier que l'utilisateur partage lui-même.
- L'appli ne clique **jamais** sur une acceptation, un achat ou un abonnement. En cas de doute,
  elle ne clique sur rien.
  Seule exception, validée par le propriétaire le 18/11/2026 : « Autoriser la sélection » (formule exacte, fixée dans le code) quand tous les interrupteurs ont été vus et sont refusés (`core/.../SelectionConfirm.kt`).
- Un fichier de règles importé ne doit jamais pouvoir introduire une acceptation
  (`core/.../RulesValidator.kt`), et rien de ce qu'il contient n'est exécuté.
- Seules les applis cochées sont lues ; les champs de saisie et de mot de passe jamais ; le texte
  de l'écran n'est enregistré que dans les fichiers de diagnostic.
- Jamais de statistique inventée, jamais de refus annoncé « vérifié » sans observation : la fermeture
  d'une fenêtre ne prouve rien (statut `UNVERIFIED`), un interrupteur d'état inconnu n'est jamais compté comme désactivé : le résultat est `PARTIAL` si une partie a été
  vérifiée, sinon `IMPOSSIBLE` ou « à terminer à la main » (`HANDED_OVER`) selon le réglage.
- Ne jamais écrire qu'un test a eu lieu sur un vrai téléphone s'il n'est pas consigné dans
  `docs/essais-reels.md` (essais faits par le propriétaire, depuis le 22/11, à partir de l'historique de l'appli).
  Tout ce que Claude vérifie lui-même reste simulé (tests JUnit, diagnostics rejoués).

## Le projet

- `core/` : moteur de décision en Kotlin pur, testé avec JUnit (aucune dépendance Android).
- `app/` : appli Android (Compose, Material 3). `demo/` : appli de test (WebView) pour l'émulateur.
- Vérification complète : `./gradlew test :app:lintRelease :app:assembleRelease`.
  Il faut le SDK Android : lancer une fois `bash scripts/installer-sdk-android.sh` (environ une
  minute ; l'environnement « Refusebot » autorise `dl.google.com`). Sans ce serveur, seul
  `./gradlew :core:test` reste possible avec un projet Gradle séparé, et la CI fait le reste.
- **Protection des dépenses GitHub Actions** : aucun job ne doit démarrer sans l'entrée `authorize_paid_usage` explicitement réglée sur `true` pour cette exécution. Elle est obligatoire et vaut `false` par défaut dans tous les workflows. Ne jamais l'activer à la place du propriétaire ; une demande générale de compilation/test/correction ne vaut pas accord de dépense. Seule une demande explicite d'utiliser le budget payant pour l'exécution concernée autorise son activation. Le budget Actions reste limité à 5 $ avec arrêt automatique et alertes. Le budget est global au compte ; cette garde protège les workflows de ce dépôt.
- CI : CodeMagic (`codemagic.yaml`, tests + lint + APK, **lancé à la main** par le propriétaire) ; GitHub Actions, uniquement sur lancement manuel et après autorisation explicite par exécution (`authorize_paid_usage: true`), sinon tous les jobs sont ignorés. Numéro de version dans `gradle.properties` (`cyrava.version`),
  `.github/workflows/build.yml` et `e2e.yml` (émulateur, script
  `.github/scripts/e2e.sh` à tenir à jour quand un texte de l'interface change).
- Publication : APK signé par le workflow CodeMagic « CYRAVA - APK signé avec ma clé (manuel) », lancé
  à la main par le propriétaire avec sa clé personnelle rangée dans CodeMagic, puis Release créée à la
  main sur GitHub (marche à suivre : README, section 11). Le workflow GitHub « Publication » n'est plus utilisé. Sans clé, seuls des APK de test (« 📱 Application — APK de test (compilation) ») sont produits. Ne jamais
  réintroduire de secret ou de droit d'écriture dans `build.yml` / `e2e.yml`.
- Messages de commit en français. Pas de pull request sans demande explicite.
