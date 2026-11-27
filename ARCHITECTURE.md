# Architecture

RefusBot est découpé en trois modules Gradle. La règle de base : **toute décision est prise dans du Kotlin pur, testable sans Android** ; la partie Android se contente de photographier l'écran, d'exécuter les décisions et d'afficher les résultats.

```
core/   Kotlin pur (JVM) : règles, garde-fou, analyse d'écran, moteur de décision, validation des
        règles importées, laboratoire, export de diagnostic. Aucune dépendance Android.
app/    Appli Android : service d'accessibilité, interface Compose, stockage local, tuiles.
demo/   Appli de démonstration (fausses fenêtres de cookies dans une WebView). Non livrée :
        elle sert uniquement au test sur émulateur.
```

## Le trajet d'une fenêtre de cookies

```
Android (événement d'une appli cochée)
   │
   ▼
RefusBotAccessibilityService        app/service
   │  regroupe les événements (300 ms), une seule session à la fois
   ▼
NodeSnapshotter.capture()           photographie l'arbre d'accessibilité → SnapshotNode (immuable)
   │                                 (texte des champs de saisie et de mot de passe écarté ici)
   ▼
RefusalEngine.next(screen, profil)  core : machine à états
   │   ├─ ScreenAnalyzer.analyze()  indices, boutons, interrupteurs, plateforme, confiance, empreinte
   │   └─ SafetyGuard               chaque cible de clic est vérifiée
   ▼
Step.Idle | Step.Wait | Step.Act(ops) | Step.Done(outcome)
   │
   ▼
ActionExecutor.execute(ops)         re-vérifie le garde-fou, puis clic / dépliage / défilement
   │
   └─ boucle jusqu'à Step.Done → historique, notification, diagnostic éventuel
```

Le moteur ne voit jamais les objets Android : il reçoit un instantané (`UiNode`) et renvoie des opérations (`Op.Click`, `Op.Expand`, `Op.Scroll`, `Op.Swipe`) désignées par la position du nœud dans l'instantané. C'est ce qui permet de le tester avec de simples arbres construits à la main, et de le faire tourner dans le laboratoire.

## Module `core`

| Fichier | Rôle |
|---|---|
| `TextNormalizer` | Minuscules, sans accents ni ponctuation : toutes les comparaisons de texte passent par là. |
| `MiniJson` | Lecteur / écrivain JSON minimal, sans dépendance, avec profondeur d'imbrication limitée. |
| `Rules`, `RulesParser` | Les règles (formules par catégorie, garde-fou, interrupteurs, limites, plateformes), lues depuis `rules.json`. |
| `RulesValidator` | Contrôle d'un fichier de règles importé : sections de sécurité imposées par l'appli, preuve positive pour chaque formule ajoutée, bornes. |
| `SafetyGuard` | Garde-fou central : bloque tout texte contenant une forme d'acceptation, sauf formule de refus exacte. |
| `UiNode`, `SnapshotNode` | Vue abstraite d'un élément d'écran ; l'instantané immuable utilisé par l'analyse. |
| `ScreenAnalyzer` | Reconnaît une fenêtre de consentement : indices, boutons classés par type, interrupteurs et leur état, sections repliées, plateforme, niveau de confiance, empreinte structurelle (sans texte). |
| `RefusalEngine` | Machine à états COMMUNE à toutes les plateformes : refus direct, sinon réglages → refus global → finalités visibles, puis celles hors écran déjà présentes dans l’arbre (action d’accessibilité sur le nœud, jamais un appui aux coordonnées) → bouton de validation amené à l’écran → défilement → partenaires (refus global seul) → validation (défilement jusqu'en bas si elle n'est pas visible). Jamais de dépliage (`NavigationFilter`), jamais deux clics sur le même élément. Limites (40 actions — les défilements n'en font pas partie —, 90 s au plus fixées par `rules.json`, arrêt après 12 s sans aucun changement à l’écran, 600 interrupteurs, 50 défilements par page), délais de grâce, profils, interruption. |
| `Compatibility` | Bilan d'une appli à partir de ses derniers résultats. |
| `DiagnosticExport` | Sérialise un instantané et son analyse en JSON pour le centre de diagnostic. |
| `lab/Laboratory` | Fausses applis simulées et exécution du vrai moteur dessus. |

### Profils et résultats

- `Policy` : `STRICT`, `PRUDENT` (n'agit que si la confiance est « certaine »), `OBSERVATION` (n'agit jamais). Le profil est demandé par le moteur au moment où une fenêtre est reconnue, ce qui permet à l'appli de le faire dépendre de l'appli surveillée et de l'empreinte de la fenêtre.
- `OutcomeStatus` : `OPTIONS_SUCCESS` (interrupteurs relus désactivés avant la validation : seule preuve observable), `UNVERIFIED` (refus cliqué, fenêtre fermée : la fermeture ne prouve pas l'enregistrement), `PARTIAL` (au moins un interrupteur resté activé, bloqué ou d'état inconnu), `IMPOSSIBLE`, `INTERRUPTED`, `OBSERVED`. `DIRECT_SUCCESS` n'est conservé que pour relire l'ancien historique.

## Module `app`

| Dossier | Contenu |
|---|---|
| `service/` | `RefusBotAccessibilityService` (cycle de vie, filtre des applis, sessions, arrêt d'urgence), `NodeSnapshotter` (arbre Android → `SnapshotNode`), `ActionExecutor` (opérations → actions d'accessibilité), `Notifier`. |
| `data/` | `SettingsRepository` (DataStore), `LogRepository` (historique, exports), `RulesRepository` (règles livrées ou importées, retour arrière), `DiagnosticsRepository`, `AppsRepository`, `ShareHelper` (partage par FileProvider). |
| `ui/` | Cinq onglets (`HomeScreen`, `AppsScreen`, `HistoryScreen`, `CompatibilityScreen`, `SettingsScreen`) et les sous-pages des réglages (`DiagnosticScreen`, `LabScreen`, `PrivacyScreen`). |
| `tile/` | Tuiles de réglages rapides : pause / reprise, capture de diagnostic. |
| `util/` | `SystemStatus` : états et raccourcis vers les réglages du système. |

Points importants :

- **Filtre des applis** : la liste des paquets surveillés est donnée à Android (`serviceInfo.packageNames`) ; en pause ou sans appli cochée, un nom de paquet inexistant est utilisé, donc aucun événement n'arrive.
- **Un seul fil de travail** pour le moteur (`Dispatchers.Default.limitedParallelism(1)`) : pas d'accès concurrent à son état.
- **Aucune exception ne doit tuer le service** : un service d'accessibilité qui plante plusieurs fois est désactivé par Android.
- **Stockage** : tout est dans l'espace privé de l'appli (`filesDir`), les exports temporaires dans `cacheDir/exports`.

## Vérifications automatiques

- `./gradlew test` : tests JUnit du module `core` (moteur, garde-fou, règles, scénarios de plateformes, laboratoire, fichiers de règles piégés).
- `./gradlew :app:lintRelease :app:assembleRelease` : analyse lint (bloquante) et APK final.
- `codemagic.yaml` : compilation lancée **à la main** sur CodeMagic (tests + lint + APK, absence de la permission `INTERNET`, signature) ; numéro de version : `cyrava.version` dans `gradle.properties`.
- `.github/workflows/build.yml` : ancienne compilation GitHub, plus utilisée depuis le 21/11 (quota GitHub Actions épuisé).
- `.github/workflows/e2e.yml` + `.github/scripts/e2e.sh` : sur un émulateur Android 15, installe l'APK final (lancement, laboratoire), puis l'APK de test avec l'appli de démonstration : activation du service, sélection de l'appli, capture de diagnostic, fausse fenêtre Google UMP, bandeau, fenêtre sans refus, historique, arrêt d'urgence.
