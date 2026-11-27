🇬🇧 [English](SECURITY.md) | 🇫🇷 **Français**

# Sécurité et confidentialité

CYRAVA utilise un service d'accessibilité : c'est une permission puissante. Ce document décrit ce que l'appli s'interdit, comment ces interdictions sont garanties, et ce qui reste à votre charge.

## Ce que l'appli ne fait jamais

| Règle | Comment elle est garantie |
|---|---|
| **Aucune communication réseau** | L'APK n'a pas la permission `INTERNET`. La compilation échoue si elle apparaît (`build.yml`, étape « Vérifier la présence des APK compilés » ; `codemagic.yaml`, étape « Vérifier qu'aucun APK de l'application principale n'a la permission INTERNET »), et le test sur émulateur le revérifie sur l'APK installé. Aucune bibliothèque réseau, publicitaire ou de statistiques n'est incluse. |
| **Jamais d'acceptation** | Chaque clic passe par le garde-fou (`core/.../SafetyGuard.kt`), deux fois : à la décision, puis juste avant l'action. Un élément dont le texte contient une forme d'acceptation, d'achat ou d'abonnement est bloqué. Les seules exceptions sont les formules de refus livrées avec l'appli (« Continuer sans accepter »…), comparées mot pour mot, et, depuis le 18/11/2026 avec l'accord du propriétaire, « Autoriser la sélection » (formules exactes fixées dans le code, `SelectionConfirm.kt`) uniquement quand tous les interrupteurs ont été vus, sont d'état connu et refusés. |
| **Pas d'action sur un écran qui a changé** | Avant chaque clic, dépliage ou appui, la cible réelle est relue (`TargetCheck`) : appli, libellé, position, fenêtre au premier plan. Au moindre écart, rien n'est fait ; aucun appui n'est envoyé vers une ancienne coordonnée. Vérifié par des tests du moteur, pas encore par un test sur émulateur qui change l'écran au dernier moment. |
| **Pas de succès sans preuve** | La fermeture de la fenêtre ne prouve pas que l'appli a enregistré le refus : elle donne « refus cliqué, résultat non vérifiable ». Un seul interrupteur d'état inconnu donne « refus partiel ». |
| **Rien dans le doute** | Sans bouton de refus reconnu, ou si un bouton est ambigu, CYRAVA ne clique pas et inscrit « Refus impossible ». |
| **Pas de lecture hors des applis choisies** | Android ne transmet au service que les événements des applis cochées (liste mise à jour à chaque changement) ; le service revérifie le nom de l'appli avant toute analyse. En pause, il ne reçoit rien. |
| **Pas de lecture des données d'accessibilité marquées sensibles** | Sur Android 14+, un nœud signalé par l'application source comme sensible, ainsi que son sous-arbre, sont exclus de la capture, de l'analyse, des diagnostics et des actions. Les champs de saisie et mots de passe restent protégés sur toutes les versions prises en charge. |
| **Pas de mots de passe ni de saisie** | Le texte, la description et les éléments internes des champs de saisie et de mot de passe sont écartés dès la capture (`SnapshotNode`), avant toute analyse et tout enregistrement. |
| **Pas de texte d'écran dans l'historique** | L'historique ne contient que le résultat, des compteurs, la plateforme reconnue et un motif générique. Le texte de l'écran n'existe que dans les fichiers de diagnostic. |
| **Pas d'image de l'écran** | CYRAVA ne prend aucune capture d'image. Le « diagnostic » enregistre la structure de la fenêtre (boutons, textes), sur le téléphone. |
| **Pas de réactivation forcée** | Si vous désactivez le service ou refusez une permission, l'appli vous en informe mais ne tente jamais de la rétablir elle-même. |
| **Pas de chiffres inventés** | Les statistiques et la compatibilité sont calculées uniquement à partir de l'historique enregistré sur le téléphone. |

## Interpréter les étapes d'un essai

L'historique distingue désormais les étapes suivantes :

| Champ | Ce qu'il prouve | Ce qu'il ne prouve pas |
|---|---|---|
| Observation enregistrée | Une entrée locale a été ajoutée au journal. | Qu'une bannière a été reconnue. |
| Bannière détectée | Les critères de consentement (indices et bouton connu) ont été satisfaits. | Que tous les contrôles ont été compris. |
| Contrôles reconnus | Des boutons, candidats interrupteurs ou sections ont été identifiés dans l'arbre accessible. | Que leur état est connu ou qu'ils sont actionnables. |
| Actions tentées | Le moteur a remis des opérations au service Android. | Qu'Android les a acceptées. |
| Actions exécutées | Android a accepté l'opération ou le geste. | Que la CMP a enregistré le choix. |
| État vérifié | Les contrôles cochables observés sont désactivés après traitement. | Que les cookies sont absents ou supprimés. |

Un candidat interrupteur sans isCheckable reste INCONNU, même si sa description affiche « Off ». Il n'est jamais actionné et, en l'absence d'un contrôle de refus explicite, la validation des préférences est bloquée. Une capture tronquée ne permet jamais de traiter les options ou d'affirmer un refus complet.

Depuis l'onglet Applications, le bouton de test peut ouvrir une application surveillée (par exemple Chrome) ; CYRAVA ne navigue pas vers une URL. L'utilisateur ouvre le site et la bannière lui-même. Le service ne travaille que sur les paquets cochés et uniquement lorsque le service est actif. En profil Observation, aucune action n'est produite.

Les noms OneTrust et Didomi proviennent de marqueurs techniques viewId ou de formulations explicitement reconnues. Si ces indices ne sont pas exposés par l'application source, la plateforme peut rester inconnue ou générique. Le nom du paquet ne permet jamais de déduire le domaine visité.

## Fichiers de règles importés

Un fichier de règles ne contient que des listes de mots et des nombres : rien n'y est exécuté. Avant d'être utilisé, il passe par `core/.../RulesValidator.kt` :

1. **Les sections de sécurité viennent toujours de l'appli.** Le garde-fou livré est conservé (un fichier peut seulement y ajouter des formes interdites). Toute la gestion des interrupteurs — quelles vues sont des interrupteurs, quels états signifient « activé » ou « désactivé », quels libellés ne pas toucher — reste celle de l'appli. Un fichier ne peut donc ni faire passer un interrupteur activé pour désactivé, ni empêcher de le désactiver.
2. **Preuve positive pour chaque formule ajoutée.** Une formule cliquable absente des règles livrées doit contenir un mot-clé reconnu de sa catégorie (refus, options, validation, fournisseurs, retour), aucune forme interdite et aucun mot ressemblant à une acceptation (« activer », « continuer », « ja »…). Aucune nouvelle exception au garde-fou n'est admise.
3. **Bornes.** Taille du fichier, profondeur d'imbrication, nombre de formules, limites d'actions et de délais.

Au moindre doute le fichier est refusé et rien ne change. La version précédente reste disponible pour un retour arrière. Onze tests (`ImportSafetyTest`) fabriquent des fichiers piégés et vérifient qu'ils sont rejetés.

Limite assumée : ce contrôle repose sur des listes de mots dans 7 langues. Il est volontairement strict (il refuse aussi certaines formules légitimes), mais il ne remplace pas la prudence : **n'importez que des fichiers de source sûre.**

## Autorisations demandées

| Autorisation | Usage |
|---|---|
| Service d'accessibilité | Lire la fenêtre des applis cochées et appuyer sur les boutons de refus. |
| Notifications (facultatif) | Vous prévenir d'un refus, d'un échec ou de l'arrêt du service. |
| Ignorer l'optimisation de batterie (facultatif) | Éviter que le système n'arrête le service. |
| Visibilité des applis lançables | Afficher la liste de l'onglet Applications (pas d'accès à la liste complète des applis installées). |

## Données enregistrées sur le téléphone

Réglages, historique (500 entrées au maximum), fichiers de diagnostic (30 au maximum), règles importées. Tout est dans l'espace privé de l'appli, supprimable depuis **Réglages › Données enregistrées** ou en désinstallant l'appli. Les exports (historique, réglages, diagnostics) ne quittent le téléphone que si vous les partagez vous-même.

## Signature de l'APK et publication

- **APK de test** (CodeMagic) : signés avec une clé de test rangée dans CodeMagic (`cyrava_test`), jamais dans le dépôt ; ailleurs (compilation locale, émulateur), avec la clé « debug » générée par la machine. Ils n'authentifient pas l'auteur ; leur version porte toujours un suffixe (`1.1.0-dev.42`).
- **Release** : Gradle refuse de préparer une version `x.y.z` sans clé personnelle complète, et refuse la clé de test (`scripts/verifier-signature.sh` le vérifie à chaque compilation). Une clé n'est jamais générée pendant la compilation, jamais remplacée silencieusement.
- **Séparation des droits** : `build.yml` et `e2e.yml` ne référencent aucun secret et ont `contents: read`. `publication.yml` est limité au lancement manuel par le propriétaire, sur `main`, avec l'environnement `publication` et le droit de créer une Release. `developpement.yml` est manuel, réservé au propriétaire et limité exclusivement à `main`, avec l'environnement `development` ; il ne publie rien. Les secrets de développement doivent être définis uniquement dans l'environnement `development`, jamais comme secrets du dépôt/de l'organisation. L'environnement `development` doit être limité à `main`. Les secrets de publication restent uniquement dans l'environnement `publication`, lui aussi limité à `main` et protégé par une approbation obligatoire. `scripts/verifier-workflows.sh` contrôle le code des workflows, pas les réglages réels de GitHub.
- Les actions GitHub sont épinglées à un SHA complet.
- La version 1.0.0, antérieure à ces règles, est signée avec la clé de test.

Non vérifié et non configurable par ce dépôt : les secrets réellement enregistrés dans GitHub, les règles de branche de l'environnement, l'approbation obligatoire et son impossibilité de contournement. Le propriétaire doit les régler et retirer les éventuels secrets de signature au niveau dépôt/organisation avant toute publication.

## Audit des préférences de consentement — périmètre et limites

CYRAVA peut observer les choix visibles d'une CMP lorsque l'arbre d'accessibilité les publie. Cette observation ne prouve pas que la CMP a enregistré ces préférences.

CYRAVA **ne lit pas** la base de cookies de Chrome, le stockage interne d'un autre navigateur, les cookies HTTP, leurs valeurs ni leur contenu. Android isole les données privées des applications ; le service d'accessibilité ne donne pas accès à ces bases. Le nom du paquet identifie le navigateur, pas le site actuellement visité. Un domaine ne doit jamais être déduit du seul nom de l'application.

Un futur registre pourra distinguer, pour chaque catégorie, `AUTORISÉ`, `REFUSÉ` ou `INCONNU`, avec date, source et confiance. Les sources à étudier sont l'état explicitement exposé par une CMP, un export volontaire fourni par l'utilisateur, ou une page ouverte dans un navigateur contrôlé par l'utilisateur. Les rôles ARIA non cochables, captures tronquées et signaux contradictoires restent `INCONNU`. Ce registre décrira les **préférences déclarées par la CMP**, jamais les cookies réellement présents ni leur contenu.

## Ce qui reste à votre charge

- Ne cochez que les applis utiles : le service peut lire l'écran des applis surveillées.
- Relisez un fichier de diagnostic avant de le partager : il contient le texte de la fenêtre capturée.
- Certains jeux en ligne interdisent les services d'accessibilité (anti-triche) : mettez CYRAVA en pause ou passez ces jeux en « Observation seule ».
- L'appli a été vérifiée sur émulateur, sur des fenêtres simulées et lors d'essais sur un vrai téléphone (Samsung Galaxy S23 FE, Android 16, 22–23/11/2026 — voir `docs/essais-reels.md`) ; chaque site et chaque version de CMP restent différents : surveillez l'historique les premiers jours.

## Signaler un problème de sécurité

Signalez-le **en privé** avec le formulaire de GitHub : <https://github.com/nobackface571-svg/CYRAVA/security/advisories/new>. Ne publiez jamais une faille dans un ticket public, et n'y collez jamais de fichier de diagnostic contenant des informations personnelles.
