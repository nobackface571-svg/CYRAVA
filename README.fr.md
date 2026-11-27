🇬🇧 [English](README.md) | 🇫🇷 **Français**

# CYRAVA

[![Télécharger la dernière version](https://img.shields.io/github/v/release/nobackface571-svg/CYRAVA?include_prereleases&label=T%C3%A9l%C3%A9charger%20la%20derni%C3%A8re%20version&style=for-the-badge&color=1f6feb)](https://github.com/nobackface571-svg/CYRAVA/releases)

**CYRAVA refuse automatiquement les fenêtres de consentement cookies / traceurs (RGPD, IAB TCF)** dans les applis, jeux et navigateurs Android que vous choisissez : il clique sur « Tout refuser », ou ouvre les options, **désactive tous les interrupteurs (consentement ET intérêt légitime)** et valide. **Il ne clique jamais sur « Accepter ».**

- Reconnaît 38 plateformes de consentement (Google UMP, Didomi, OneTrust, Sourcepoint, InMobi Choice, Usercentrics, Sirdata, Axeptio, Cookiebot, TrustArc, Consentmanager, iubenda, CookieYes, Commanders Act…) et les fenêtres « TCF » génériques, en 7 langues (français, anglais, allemand, espagnol, italien, portugais, néerlandais).
- Interface en **français ou en anglais** (Réglages › Langue). Les motifs détaillés du moteur et les fichiers exportés restent en français.
- Aucune permission Internet : rien ne sort du téléphone, sauf un fichier que vous partagez vous-même.
- Ne lit que les applis que vous cochez.
- Dit ce qu'il a réellement constaté : un refus n'est dit « vérifié » que si les interrupteurs ont été vus désactivés. Un simple clic sur « Tout refuser » suivi de la fermeture de la fenêtre est annoncé comme **« refusé avec le bouton du site, non vérifiable »** : CYRAVA ne peut pas voir ce que l'appli a enregistré.
- Tout se fait depuis le téléphone : installation, réglages, mises à jour. Aucun ordinateur n'est nécessaire.
- Logiciel libre sous licence **GPL-3.0** : le code est public et vérifiable par tous (voir [Licence](#licence)).
- **Développée avec l'aide de Claude**, l'assistant IA d'Anthropic (code, tests et documentation), sous la direction et avec les essais de son auteur.

## La garantie : CYRAVA ne clique jamais sur « Tout accepter »

C'est la règle n° 1 du projet, et elle ne dépend d'aucun réglage :

- **Dans tous les profils** (Prudent, Strict, Observation), CYRAVA ne clique jamais sur une acceptation, un achat ou un abonnement.
- **Chaque clic passe par un garde-fou** ([`SafetyGuard.kt`](core/src/main/kotlin/fr/refusbot/core/SafetyGuard.kt)), vérifié deux fois : au moment de la décision, puis juste avant l'action. Tout bouton ou interrupteur dont le texte contient *accepter, accept, agree, autoriser, allow, consentir, s'abonner, acheter…* est bloqué, sauf s'il correspond **exactement** à une formule de refus connue (« Continuer sans accepter », « Je n'accepte pas »…).
- **En cas de doute, CYRAVA ne fait rien** : la fenêtre reste ouverte et vous décidez.
- **Un fichier de règles importé ne peut pas contourner le garde-fou** : il est refusé s'il contient le moindre mot d'acceptation ([`RulesValidator.kt`](core/src/main/kotlin/fr/refusbot/core/RulesValidator.kt)).
- **Des tests automatiques le vérifient à chaque modification du code** (`SafetyGuardTest`, `SafetyRegressionTest`, `ButtonIdsSafetyTest`…, dans [`core/src/test`](core/src/test/kotlin/fr/refusbot/core)).

Une seule exception, volontaire et fixée dans le code : « Autoriser la sélection », uniquement quand **tous** les interrupteurs ont été vus refusés (le bouton enregistre alors votre refus).

Si vous voyez CYRAVA cliquer sur une acceptation, c'est un bug grave : signalez-le dans l'onglet **Issues** avec le fichier de diagnostic (section 8).

> **Ce qui a été testé, honnêtement.** À chaque compilation lancée sur CodeMagic, il lance les tests du moteur de décision, l'analyse de code (lint) et la compilation, et vérifie l'absence de permission Internet. Le test complet sur un **téléphone simulé** (émulateur Android, `.github/workflows/e2e.yml`) n'est plus lancé : GitHub Actions n'est plus utilisé. **CYRAVA a aussi été essayé sur un vrai téléphone** (Samsung Galaxy S23 FE, Android 16), en profil Strict, dans Chrome, Firefox, Opera et un jeu, le 22/11/2026 : 20 fenêtres vers 14 h, 17 vers 15 h, puis 14 vers 16 h 45, résultats et durées recopiés dans [docs/essais-reels.md](docs/essais-reels.md), réussites comme échecs. Voir les [limites](#12-précautions-et-limites-connues).

## Dernières mises à jour (27/11/2026)

Ce qui a changé, et pourquoi (détail dans [CHANGELOG.md](CHANGELOG.md)) :

**27/11 (version 1.1.0 bêta 4)** — vérifiés par les tests du moteur, pas encore essayés sur le téléphone :

- **Les pages qui parlent de cookies ne sont plus prises pour des fenêtres de cookies** : dans un navigateur, CYRAVA ne démarre sur une page entière que s'il voit un vrai bouton de refus ou d'acceptation, ou l'identifiant d'une plateforme connue (avant, il cliquait sur des liens de menu de pages qui *parlent* de cookies).
- **Historique dans la langue de l'appli** : avec l'appli en anglais, les explications du moteur (« seul un bouton de validation est proposé… ») et « TCF générique » s'affichent désormais en anglais. L'historique enregistré et les exports JSON/CSV restent en français.
- **Cartes d'historique simplifiées** : résultat, conseil et « état vérifié » d'abord ; touchez une carte pour voir les compteurs techniques (éléments reconnus dans la fenêtre, gestes tentés…).
- **Que faire après un échec** : un court conseil 💡 sous chaque résultat raté ou partiel (pas de refus proposé, mur payant, terminer à la main, partager un diagnostic…).
- **Batterie** : Réglages › Batterie indique le temps de travail de CYRAVA depuis le démarrage du service.

**Du 23/11 au soir au 26/11** — vérifiés par les tests du moteur, pas encore essayés sur le téléphone :

- **Vérification indépendante du consentement** : avant de dire un refus « vérifié », CYRAVA réévalue lui-même toutes les preuves. Un interrupteur d'état inconnu n'est jamais compté désactivé ; une capture ou une exploration incomplète ne peut plus donner « vérifié ». Le diagnostic nomme maintenant chaque contrôle non vérifié.
- **Repli sur le refus direct** : quand l'ouverture des réglages fait perdre la fenêtre sur une appli inconnue, le « Tout refuser » du site redevient possible ; « préférences d'abord » s'applique aussi hors navigateur.
- **Confidentialité** : les exports de diagnostic capturés ont été retirés du dépôt ; les tests qui en dépendaient aussi.
- **Changement de langue sans écran noir** ni redémarrage de l'appli.

**Fin d'après-midi du 22/11, après un troisième essai sur le téléphone (16 h 42 – 16 h 50)** — pas encore essayé sur le téléphone :

- **Didomi dans Opera** : le bas de la fenêtre (« Refuser tout », « Enregistrer ») était caché sous la barre du bas d'Opera, et « Enregistrer » restait grisé tant que chaque finalité n'avait pas de choix : plus rien ne bougeait pendant 12 s. Ces deux boutons sont maintenant actionnés directement (comme le ferait un lecteur d'écran, jamais par un appui à l'aveugle), avec les mêmes vérifications qu'un bouton visible. Si « Enregistrer » reste grisé, le « Tout refuser » du site termine (résultat « non vérifiable »).
- **« La fenêtre s'est fermée sans validation »** (Didomi dans Firefox, Sourcepoint dans Opera) : après un défilement, le titre de la fenêtre sortait de l'écran et CYRAVA croyait la fenêtre fermée ; et une page de réglages lente à s'afficher était prise pour une fermeture au bout de 0,75 s. CYRAVA reconnaît maintenant la fenêtre à l'identifiant de sa plateforme et attend environ 2 s.
- **Galaxy Defense** (17 h 20) : la liste des fournisseurs refuse de défiler, mais toutes ses lignes sont lisibles d'un coup. CYRAVA décoche maintenant les 81 « intérêt légitime » restés activés, vérifie, puis valide, au lieu de s'arrêter sur « défilement impossible ».
- **Consentmanager dans Firefox** (réglages rangés en sous-pages, rien de lisible) : avec « Refus complet », CYRAVA revient au premier écran et clique son « Tout refuser » au lieu d'échouer ; sur les sites suivants de cette plateforme, il clique directement « Tout refuser » (jusqu'au redémarrage du téléphone). Les sous-pages elles-mêmes ne sont pas encore parcourues une à une.

**Le soir du 22/11, après un second essai sur le téléphone (15 h 05 – 15 h 16), puis un audit indépendant** :

- **Jamais d'enregistrement sans avoir tout vu**, quel que soit le réglage : si une partie de la liste ou une catégorie n'a pas été vue, CYRAVA utilise le « Tout refuser » du site, ou ne clique sur rien. Avant, le réglage « Ne pas valider dans le doute » désactivé, il pouvait enregistrer des choix jamais vus (peut-être acceptés).

- **OneTrust « en onglets »** (Opera, Chrome) : les réglages sont rangés par catégorie et chaque interrupteur n'apparaît qu'en touchant sa catégorie. CYRAVA ne voyait aucun réglage (« aucun contrôle de préférence accessible reconnu »). Il ouvre maintenant chaque catégorie, la décoche, puis confirme la sélection ; si une catégorie n'a pas pu être ouverte, la validation vous est laissée.
- **Didomi en boutons « Refuser / Accepter »** : dans Firefox, les choix « Refuser » n'étaient pas reconnus (« un choix radio coché n'est pas un refus reconnu »). Dans Chrome, quand tout était déjà sur « Refuser », le bouton « Enregistrer » restait sous l'écran (« défilement impossible ») : il est maintenant amené à l'écran, puis cliqué.
- **AppConsent** : bouton de validation mieux trouvé (plus de formules reconnues, bouton sous l'écran amené à l'écran). Aucun diagnostic de cette fenêtre n'était disponible : à confirmer par un nouvel essai.
- **Lecture de l'écran plus rapide** : pendant une longue liste, CYRAVA ne relit plus que la fenêtre de cookies, pas toute la page derrière (jusqu'à 1 900 éléments relus à chaque étape dans Opera). Par prudence, il relit tout l'écran après chaque clic qui n'est pas un décochage ou un défilement, au moins une fois toutes les 5 lectures, et toujours avant de fermer la fenêtre (validation ou refus). Nouvelle durée mesurée : « lectureFenetre ».

**Plus tôt le 22/11** :

- **Les très longues listes sont parcourues jusqu'en bas** (défilement continu, écran après écran) : avant, CYRAVA s'arrêtait après 50 défilements, vers la 400e ligne, et le refus restait partiel. Il s'arrête maintenant quand plus rien de nouveau n'apparaît, et le résultat n'est jamais « vérifié » s'il n'a pas tout vu.
- **Listes de plus de 200 éléments dans Chrome** : chaque défilement était déclaré « bloqué », donc jamais de validation. Corrigé.
- **Plus rapide** : les essais sur téléphone ont montré que la lecture et l'analyse de l'écran prenaient l'essentiel du temps (jusqu'à 78 s pour 367 cases). L'analyse est **6 fois plus rapide** (même résultat, vérifié sur les 12 diagnostics des essais), l'attente avant de commencer est plafonnée à 1,5 s (jusqu'à 6 s avant) et la mesure de chaque défilement est allégée.
- **Boutons « Enregistrer » mieux trouvés** : « Enregistrer la configuration des cookies » (Commanders Act) et les boutons placés sous la liste des choix (Didomi dans Opera) n'étaient pas vus : « bouton de validation introuvable ».
- **Durée de chaque étape mesurée** (lecture de l'écran, analyse, décochage, défilement, attentes) dans l'export de l'historique et les diagnostics, pour savoir où passe le temps.
- **Numéro de version** affiché correctement dans l'appli, et **CodeMagic** ne compile plus que lorsqu'on le lance à la main.

---


## Vérification indépendante du consentement (Consent Verification Engine)

Le module `ConsentVerificationEngine` réévalue les preuves d'accessibilité avant de qualifier un refus :
- un interrupteur d'état inconnu n'est jamais considéré comme désactivé ;
- un interrupteur facultatif encore actif empêche la vérification ;
- une capture ou une exploration incomplète empêche la vérification ;
- un choix radio « Autoriser » sélectionné empêche la vérification ;
- les interrupteurs « nécessaires / toujours actifs » ne sont exemptés que si l'analyseur les a explicitement classés ainsi.

## Sommaire

1. [Installer l'APK depuis le téléphone](#1-installer-lapk-depuis-le-téléphone)
2. [Autoriser les « paramètres restreints » (Android 13+)](#2-autoriser-les--paramètres-restreints--android-13)
3. [Activer le service d'accessibilité](#3-activer-le-service-daccessibilité)
4. [Empêcher Samsung d'arrêter CYRAVA](#4-empêcher-samsung-darrêter-cyrava)
5. [Choisir les applis à surveiller](#5-choisir-les-applis-à-surveiller)
6. [Utilisation au quotidien](#6-utilisation-au-quotidien)
7. [Les profils Prudent, Strict et Observation](#7-les-trois-profils-et-les-résultats-possibles)
8. [Centre de diagnostic et laboratoire](#8-centre-de-diagnostic-et-laboratoire)
9. [Sauvegarde, export et fichiers de règles](#9-sauvegarde-export-et-fichiers-de-règles)
10. [Questions fréquentes et dépannage](#10-questions-fréquentes-et-dépannage)
11. [Signature de l'APK et développement](#11-signature-de-lapk-et-développement)
12. [Précautions et limites connues](#12-précautions-et-limites-connues)
13. [Licence](#licence)

---

## 1. Installer l'APK depuis le téléphone

> **Testeurs** : la [fiche testeurs](docs/FICHE-TESTEURS.fr.md) résume toutes les étapes d'installation et d'essai en quelques minutes de lecture.

**Avant d'installer : désactiver Google Play Protect.** CYRAVA n'est pas sur le Play Store et utilise l'accessibilité : Play Protect risque fort de bloquer l'installation. Dans l'appli **Play Store** : **photo de profil › Play Protect › ⚙️ (roue dentée)**, désactivez **« Analyser les applis avec Play Protect »**. Une fois CYRAVA installé et activé, vous pouvez le réactiver au même endroit ; s'il affiche un avertissement sur CYRAVA, choisissez de conserver l'appli.

**Testeurs : version officielle** : ouvrez l'onglet **Releases** de ce dépôt GitHub depuis le navigateur du téléphone et téléchargez le fichier `.apk` de la dernière version. Chaque Release est signée avec la clé personnelle du mainteneur. N'installez CYRAVA que depuis cette page : un APK trouvé ailleurs peut avoir été modifié.

**Mainteneur : APK de test** : ouvrez la dernière compilation réussie de **CodeMagic** (workflow « tests et APK de vérification »), section **Artifacts**, et téléchargez `app-release.apk`. Il est signé avec la clé de test rangée dans CodeMagic : il se met à jour par-dessus les APK de test précédents, mais pas par-dessus une version officielle (clés différentes).

Installation :

1. Une fois l'APK téléchargé, touchez la notification de téléchargement (ou **Mes fichiers › Téléchargements**) puis le fichier `.apk`.
2. Si Android affiche *« … n'est pas autorisé à installer des applis inconnues de cette source »* : touchez **Paramètres**, activez **Autoriser depuis cette source**, puis revenez en arrière.
3. Touchez **Installer**.
   - Si **Play Protect** affiche quand même un avertissement : **Plus de détails › Installer quand même** (l'appli n'est pas publiée sur le Play Store, c'est normal). S'il bloque, désactivez-le comme indiqué ci-dessus.
   - Sur Samsung récent, si l'installation est bloquée par le **Blocage automatique** (*Auto Blocker*) : **Paramètres › Sécurité et confidentialité › Blocage automatique**, désactivez-le le temps de l'installation, puis réactivez-le.

> **Mises à jour** : installez la nouvelle version par-dessus l'ancienne. Réglages et historique sont conservés tant que la clé de signature reste la même.

## 2. Autoriser les « paramètres restreints » (Android 13+)

Pour une appli installée hors Play Store, Android grise le service d'accessibilité tant que vous ne l'avez pas autorisé. L'onglet **Accueil** de CYRAVA affiche ce guide et des boutons raccourcis.

1. Dans CYRAVA, touchez **Ouvrir les réglages d'accessibilité** › **Applis installées** › **CYRAVA – Digital Privacy**. Si l'interrupteur est grisé ou qu'un message **« Paramètre restreint »** apparaît, touchez **OK**.
2. Revenez dans CYRAVA et touchez **Ouvrir Infos sur l'appli** (ou : **Paramètres › Applications › CYRAVA**).
3. Touchez **⋮** (en haut à droite) › **Autoriser les paramètres restreints**, puis confirmez avec votre code / empreinte.

> Le menu « Autoriser les paramètres restreints » n'apparaît qu'**après** avoir essayé une première fois d'activer le service (étape 1).

## 3. Activer le service d'accessibilité

1. **Paramètres › Accessibilité › Applis installées › CYRAVA – Digital Privacy** (ou bouton **Ouvrir les réglages d'accessibilité** dans l'appli).
2. Activez l'interrupteur et acceptez l'avertissement d'Android (il est générique : CYRAVA ne lit que les applis cochées et n'a pas Internet).
3. De retour dans CYRAVA, l'accueil n'affiche plus « Protection inactive ».

Une fois le service activé, **vous n'avez plus besoin d'ouvrir CYRAVA** : il travaille en arrière-plan.

### Après un redémarrage, une mise à jour ou une désactivation

- **Redémarrage** : Android relance automatiquement les services d'accessibilité activés ; rien à refaire. Si l'accueil affiche « Service arrêté », voir la section suivante.
- **Mise à jour** (nouvel APK par-dessus) : le service reste activé, vos réglages et votre historique sont conservés.
- **Désactiver le service** (Paramètres › Accessibilité) : CYRAVA s'arrête immédiatement et ne lit plus rien. Réactivez-le au même endroit quand vous voulez ; CYRAVA ne le réactive jamais lui-même.

## 4. Empêcher Samsung d'arrêter CYRAVA

One UI arrête volontiers les services en arrière-plan :

1. Dans CYRAVA (onglet Accueil, carte Batterie), touchez **Ne pas optimiser CYRAVA** et acceptez.
2. **Paramètres › Batterie › Limites d'utilisation en arrière-plan › Applis jamais en veille › +** › cochez **CYRAVA**.
3. Facultatif : **Paramètres › Applications › CYRAVA › Batterie › Non restreinte**.

Si l'accueil affiche **« Service arrêté »**, désactivez puis réactivez le service d'accessibilité.

## 5. Choisir les applis à surveiller

1. Onglet **Applications** : cherchez l'appli, le jeu ou le navigateur, puis activez son interrupteur.
2. Pour chaque appli cochée, choisissez son mode :
   - **Refus automatique** : CYRAVA agit, selon le profil choisi dans les Réglages ;
   - **Observation seule** : CYRAVA analyse la fenêtre et l'inscrit dans l'historique, mais **ne touche à rien**.
3. Filtres : **Surveillées uniquement** et **Masquer les applis système**. **Tout cocher / Tout décocher** s'applique aux applis affichées.
4. Ne cochez **que** les applis qui affichent des fenêtres de cookies : CYRAVA ne reçoit **aucun** événement des autres applis.

> **Navigateurs** : utilisez **Chrome** ou **Firefox**. **Samsung Internet n'est pas pris en charge** : il ne transmet pas le contenu des pages aux services d'accessibilité. L'onglet Applications le signale et « Tout cocher » ne le coche pas.

> ⚠️ **Jeux avec anti-triche** : certains jeux peuvent détecter les services d'accessibilité et réagir (avertissement, déconnexion, voire sanction). Ne surveillez que le nécessaire et mettez CYRAVA en pause avant de lancer ces jeux.

## 6. Utilisation au quotidien

L'appli a cinq onglets :

| Onglet | À quoi il sert |
|---|---|
| **Accueil** | État de la protection (active, en pause, à configurer…), bouton **Mettre en pause / Reprendre la protection**, **Arrêt d'urgence**, activité (refus réussis, graphique), derniers événements, état du service, batterie, tuiles. |
| **Applications** | Choisir les applis surveillées et leur mode, voir le dernier résultat de chacune. |
| **Historique** | Chaque fenêtre traitée : appli, date, résultat, plateforme reconnue, niveau de confiance, détail des interrupteurs. Filtres, recherche, export (avec la durée de chaque étape, en millisecondes). |
| **Compatibilité** | Le bilan de chaque appli (compatible, partielle, à vérifier, non prise en charge…), calculé sur ses 5 dernières fenêtres réelles. |
| **Réglages** | Langue, profil, notifications, thème, centre de diagnostic, laboratoire, règles, sauvegarde, suppression des données, confidentialité. |

- **Pause / reprise** : bouton de l'accueil, ou tuile rapide **CYRAVA**. En pause, aucune fenêtre n'est traitée.
- **Arrêt d'urgence** : stoppe immédiatement l'action en cours et met la protection en pause. Rien n'est effacé.
- **Notifications** (Réglages › Notifications) : *Toutes*, *Échecs uniquement*, *Interruptions uniquement* ou *Aucune*.
- **Statistiques** : calculées uniquement à partir de l'historique de votre téléphone. Aucun chiffre n'est estimé ou inventé.
- **Sites déjà traités** : CYRAVA s'en souvient jusqu'au redémarrage du téléphone.

### Les tuiles rapides

1. Ajoutez-les une fois : sur Android 13+, boutons de l'accueil ; sinon déroulez le volet, touchez **✎** et faites glisser **CYRAVA** et **Capturer l'écran** dans la zone active.
2. **CYRAVA** : un appui = pause / reprise.
3. **Capturer l'écran** : ouvrez l'appli sur sa fenêtre de cookies, déroulez le volet et touchez la tuile ; la structure de la fenêtre est enregistrée (section 8). Ce n'est pas une image : CYRAVA ne prend jamais de capture d'image.

## 7. Les trois profils et les résultats possibles

### Profils (Réglages › Profil de refus)

| Profil | Comportement |
|---|---|
| **Prudent** (par défaut pour une nouvelle installation) | N'agit que si la fenêtre est reconnue avec **certitude** (plateforme identifiée ou nombreux indices). Sinon il observe sans agir. Une fenêtre qui ne ressemble à aucune de celles déjà refusées dans l'appli est à valider dans l'onglet Compatibilité. |
| **Strict** | Refuse toutes les options facultatives reconnues ; ne valide qu'avec un bouton de validation sûr. |
| **Observation** | N'agit jamais : analyse et inscrit les fenêtres dans l'historique. |

Réglage complémentaire **« Ne pas valider dans le doute »** (**activé par défaut**, à laisser activé) : si des cases n'ont pas pu être lues ou désactivées, CYRAVA n'enregistre pas vos choix et vous laisse terminer à la main. Désactivé, il peut enregistrer un refus incomplet (« Refus partiel »).

Dans tous les cas, **CYRAVA n'enregistre jamais vos choix s'il n'a pas tout vu** (liste pas lue jusqu'en bas, catégorie ou page des partenaires non ouverte) : il utilise alors le « Tout refuser » du site s'il existe, sinon il ne clique sur rien.

Dans **tous** les profils, CYRAVA ne clique jamais sur une acceptation.

### Résultats (onglet Historique)

| Résultat | Signification |
|---|---|
| **Refus par les préférences : interrupteurs vérifiés désactivés** | Options ouvertes, interrupteurs désactivés puis relus désactivés, choix validés. Seul résultat appuyé par une observation. |
| **Refusé avec le bouton du site (non vérifiable)** | Un bouton de refus a été cliqué et la fenêtre s'est fermée. La fermeture ne prouve pas que l'appli a enregistré le refus. |
| **Refus partiel** | Le refus a été validé, mais tout n'a pas pu être vérifié ou désactivé (une seule case dont l'état n'est pas publié suffit). N'arrive que si « Ne pas valider dans le doute » est désactivé, ou après un « Tout refuser » utilisé faute d'avoir pu tout lire. |
| **À terminer à la main** | Réglage « Ne pas valider dans le doute » : la fenêtre est laissée ouverte pour que vous finissiez. |
| **Capture incomplète** | L'écran n'a pas pu être lu entièrement. Aucun refus n'est établi. |
| **Refus impossible** | Aucun bouton de refus sûr (mur « payer ou accepter », formulation inconnue…). Rien n'a été cliqué. |
| **Action interrompue** | Pause, arrêt d'urgence, ou vous avez quitté l'appli pendant le traitement. |
| **Observé (aucune action)** | Profil Observation ou Prudent : la fenêtre a seulement été analysée. |

Sous un résultat raté ou partiel, une ligne 💡 indique quoi faire. Touchez une carte pour voir ses détails techniques : *éléments reconnus dans la fenêtre* (tous les boutons, liens, onglets et interrupteurs repérés, pas seulement les cases à décocher), *gestes tentés* (clics et défilements) et *interrupteurs désactivés* (les cases réellement décochées).

### Comment CYRAVA décide

1. Une fenêtre est reconnue si elle contient **au moins 2 indices** (cookies, consentement, partenaires, intérêt légitime, identifiants propres à une plateforme…) **et au moins 1 bouton connu**.
2. **Refus direct** s'il existe : « Tout refuser », « Continuer sans accepter », « Reject all », « Alle ablehnen »…
3. Sinon **ouverture des réglages** (« Paramétrer », « Personnaliser », « Gérer mes choix »…). Sur l'écran des finalités, **d'abord le refus global** (« Tout refuser », « Tout désactiver », interrupteur principal), **ensuite seulement** chaque finalité encore acceptée (son « Refuser » ou son interrupteur, avec relecture de l'état). Les longues listes sont parcourues jusqu'en bas. Réglages rangés **en onglets par catégorie** (OneTrust) : chaque catégorie est ouverte à son tour (un onglet n'enregistre rien).
4. **Partenaires / fournisseurs** : le lien (« Voir nos partenaires », « Vendors »…) est ouvert et seul le **refus global** y est cliqué (« Tous les partenaires › Bloquer », « Tout refuser ») ; aucun partenaire n'est déplié ni réglé un par un. Quand ce refus global est sélectionné, la liste est considérée refusée.
5. **Validation** (« Enregistrer », « Confirmer mes choix », « Save & Exit »…), **seulement si tous les choix ont été vus**. Si le bouton n'est pas visible, CYRAVA le fait venir à l'écran ou **défile jusqu'en bas** de la fenêtre avant de conclure qu'il manque. Un « Enregistrer » ou un « Tout refuser » coincé sous la barre du navigateur (hauteur nulle, Opera) est actionné directement par l'accessibilité, jamais par un appui à l'aveugle. Un « Enregistrer » grisé par le site n'est jamais touché : le « Tout refuser » du site sert alors de repli.

Ce parcours est **le même pour toutes les plateformes** (Didomi, Sourcepoint, Axeptio, OneTrust, Quantcast, Cookiebot, inconnues…) : chaque plateforme n'apporte que ses libellés et ses repères d'écran.

**Jamais cliqués**, sur aucune plateforme : les sections dépliables (lignes « + », « - », « › », chevrons, « Catégories de données », « Stockage de l'appareil », « Voir les infos de l'utilisateur », titre « Intérêt légitime »…), « En savoir plus » / « Voir plus » / « Détails » à l'intérieur des réglages, les noms de partenaires, les liens vers la politique de confidentialité ou une page externe, et bien sûr toute acceptation.

6. **Garde-fou** sur chaque clic (vérifié à la décision puis juste avant l'action) : tout élément dont le texte contient *accepter, accept, agree, autoriser, allow, consentir, s'abonner, acheter…* est bloqué, sauf s'il correspond **exactement** à une formule de refus connue, ou s'il s'agit d'un refus formulé avec une vraie négation juste avant le verbe (« Je n'accepte pas », « Ne pas autoriser », « Continuer sans accepter »). « pas » seul ne compte pas comme négation. Seule exception validée : « Autoriser la sélection » quand tous les interrupteurs ont été vus refusés. En cas de doute, CYRAVA ne fait rien.
7. **Murs « accepter ou payer »** : reconnus, rien n'est cliqué (« Refus impossible »).
8. **Anti-boucle** : un même élément n'est **jamais cliqué deux fois**, avec une seule exception : un interrupteur décoché pendant qu'il était encore sous l'écran (Chrome), puis relu **toujours activé** une fois visible, a droit à un second et dernier essai, qui ne peut que le désactiver. Jamais de troisième clic. Si l'écran ne change pas après un clic, CYRAVA passe à l'étape suivante. Nombre d'actions et durée limités par fenêtre ; après un échec, la même fenêtre n'est pas réessayée tout de suite.

## 8. Centre de diagnostic et laboratoire

### Centre de diagnostic (Réglages › Centre de diagnostic)

Si une fenêtre n'est pas reconnue ou pas refusée complètement, enregistrez sa structure pour que de nouvelles règles soient ajoutées :

1. Vérifiez que l'appli est **cochée** (seules les applis surveillées peuvent être capturées).
2. Capture **automatique** (« Diagnostic automatique » activé) ou **manuelle** : **Capturer dans 5 secondes**, ou tuile **Capturer l'écran**.
3. Touchez **Partager** sur le fichier voulu. Le fichier s'appelle `diagnostic-<appli>-<date>.json`. **Tout partager en un fichier .zip** envoie d'un coup tous les diagnostics et l'historique.
4. La carte **État du système** résume l'installation sans aucune donnée personnelle.

Le fichier **contient le texte affiché par l'appli à ce moment-là**, mais jamais le contenu des champs de saisie et de mot de passe. Il reste sur le téléphone tant que vous ne le partagez pas.

### Laboratoire (Réglages › Laboratoire)

Scénarios de fenêtres simulées pour vérifier le moteur de décision sur le téléphone, sans toucher à aucune appli. Le vrai formulaire Google UMP est testé à part par l'appli de démonstration : voir [docs/testing/GOOGLE_UMP_E2E.md](docs/testing/GOOGLE_UMP_E2E.md).

## 9. Sauvegarde, export et fichiers de règles

- **Sauvegarde des réglages** (Réglages › Sauvegarde) : exporte / restaure profil, notifications, thème et liste des applis surveillées (ni historique ni texte d'écran).
- **Export de l'historique** (onglet Historique) : **JSON** ou **CSV** (lisible dans un tableur).
- **Fichiers de règles** (Réglages › Règles de détection) : vous pouvez importer un fichier de règles plus récent. Il est contrôlé avant usage : le garde-fou et la gestion des interrupteurs restent **toujours** ceux de l'appli, aucune formule ajoutée ne peut contenir un mot d'acceptation, et au moindre doute le fichier est refusé. Rien de ce qu'il contient n'est exécuté. Voir [docs/cmp-library/IMPORT_REGLES.md](docs/cmp-library/IMPORT_REGLES.fr.md).
- **Supprimer toutes les données** (Réglages › Données enregistrées) : efface réglages, historique, diagnostics et règles importées.

## 10. Questions fréquentes et dépannage

**Dois-je laisser CYRAVA ouvert ?** Non. Une fois le service activé, il fonctionne en arrière-plan, même après un redémarrage.

**Est-ce que ça use la batterie ?** Android ne réveille CYRAVA que lorsqu'une appli cochée change d'écran : moins vous cochez d'applis, moins il travaille. **Réglages › Batterie** indique le temps de travail de CYRAVA depuis le démarrage du service (lectures d'écran sans fenêtre de cookies, fenêtres traitées). Ce sont des durées, pas un pourcentage de batterie : la consommation réelle se lit dans les réglages Android (Batterie › Utilisation de la batterie › CYRAVA).

**Rien ne se passe dans une appli.** Vérifiez dans l'ordre :
1. l'appli est cochée et n'est pas en « Observation seule » ;
2. l'accueil affiche « Protection active » ;
3. le profil n'est pas « Observation » ; en profil Prudent, la fenêtre doit être reconnue avec certitude ;
4. le navigateur n'est pas Samsung Internet ;
5. sinon, faites une capture dans le centre de diagnostic : la formulation de cette fenêtre est sans doute inconnue.

**En désinstallant, je vois encore `fr.refusbot`.** C'est l'ancienne bêta 1 (identifiant `fr.refusbot`), restée installée à côté de la nouvelle (`fr.cyrava`) : pour Android, ce sont deux applis différentes. Désinstallez celle qui affiche `fr.refusbot`. Les dossiers du code source s'appellent encore `fr.refusbot`, mais cela ne se voit nulle part sur le téléphone.

**« Refus partiel », est-ce grave ?** Le refus a été enregistré mais tout n'a pas pu être vérifié. Le motif est dans l'historique. Pour l'éviter, laissez « Ne pas valider dans le doute » activé : vous finirez alors vous-même les rares fenêtres douteuses.

**L'onglet Compatibilité indique « À vérifier ».** La dernière fenêtre ne ressemble à aucune de celles déjà refusées. Si elle vous paraît normale, touchez **Valider la nouvelle interface**.

## 11. Signature de l'APK et développement

### Deux signatures, jamais mélangées

- **APK de test** (CodeMagic) : signés avec une **clé de test** rangée dans CodeMagic sous le nom `cyrava_test` (Teams › Code signing identities › Android keystores). **Aucune clé n'est rangée dans ce dépôt public.** Sans cette clé (compilation locale, émulateur GitHub), la clé « debug » générée par Android sur la machine est utilisée.
- **APK signé avec votre clé** : workflow CodeMagic **« CYRAVA - APK signé avec ma clé (manuel) »**, qui vérifie l'empreinte de la clé personnelle et l'absence de permission Internet. Les workflows GitHub (🔐 développement, 🚀 publication officielle) existent encore mais ne sont plus utilisés depuis le 21/11 (plus de crédit GitHub Actions). Les secrets de signature se rangent uniquement dans CodeMagic (ou dans les environnements GitHub prévus), jamais dans le code ni dans un message.

### Publier une nouvelle version pour les testeurs (mainteneur, depuis le téléphone)

1. Vérifier le numéro de version dans [`gradle.properties`](gradle.properties) (`cyrava.version`, par exemple `1.1.0-beta.3`) : c'est le seul endroit à changer, et il doit être nouveau.
2. Sur CodeMagic, lancer **« CYRAVA - tests et APK de vérification »** sur `main` : tout doit être vert.
3. Lancer ensuite **« CYRAVA - APK signé avec ma clé (manuel) »** sur `main`, puis télécharger l'APK dans **Artifacts**.
4. Sur GitHub, créer la Release : <https://github.com/nobackface571-svg/CYRAVA/releases/new>. Étiquette (tag) `v` + le numéro (par exemple `v1.1.0-beta.3`), cible `main`, titre « CYRAVA 1.1.0 bêta 3 », notes reprises du [journal des versions](CHANGELOG.md), APK ajouté en pièce jointe, case **« Set as a pre-release »** cochée pour une bêta, puis **Publish release**.
5. Déplacer, dans le journal des versions, la partie « À venir (non publié) » sous le titre de la nouvelle version.

> ⚠️ Changer de clé impose de **désinstaller une fois** CYRAVA (Android refuse une mise à jour signée par une autre clé). Conservez le keystore original et une sauvegarde hors du téléphone.

### Pour les développeurs

```
core/   Kotlin pur (aucune dépendance Android) : normalisation, règles, garde-fou, analyse d'écran,
        machine à états, validation des règles importées, laboratoire, export diagnostic. Testé sur la JVM.
app/    Android : service d'accessibilité, Compose / Material 3, DataStore, tuiles, historique.
        Textes de l'interface : res/values (français) et res/values-en (anglais).
app/src/main/assets/rules.json        Formules en 7 langues et profils de plateformes.
app/src/main/assets/cmp-catalog.json  Catalogue des plateformes (contrôlé par scripts/cmp-library/validate_catalog.py).
demo/   Appli de test : vrai SDK Google UMP + fenêtres CMP génériques dans une WebView.
```

- Identifiant de l'appli : `fr.cyrava` (depuis la bêta 2). Les paquets du code Kotlin restent `fr.refusbot` (`namespace` Gradle), ce qui ne change rien pour les utilisateurs.
- Tests : `./gradlew test` — vérification complète : `./gradlew test :app:lintRelease :app:assembleRelease`.
- Compilation et tests, lancés à la main : `codemagic.yaml`. Test sur émulateur : `.github/workflows/e2e.yml` et `.github/scripts/e2e.sh`.
- Voir aussi [ARCHITECTURE.md](ARCHITECTURE.md), [CONTRIBUTING.fr.md](CONTRIBUTING.fr.md), [SECURITY.fr.md](SECURITY.fr.md) et [CHANGELOG.md](CHANGELOG.md).

## 12. Précautions et limites connues

**Précautions**
- Un service d'accessibilité peut lire l'écran des applis qu'il surveille : ne cochez que les applis nécessaires, et n'installez que l'APK publié dans les **Releases** de ce dépôt.
- Les fichiers de diagnostic contiennent du texte d'écran : relisez-les et ne les partagez qu'avec qui vous voulez.
- L'historique ne contient jamais de texte d'écran : seulement la date, l'appli, le résultat, la plateforme reconnue, des compteurs et un motif générique.
- N'importez un fichier de règles que s'il vient d'une source de confiance.

**Limites**
- **Tests** : automatiques sur des fenêtres simulées, et essais manuels sur **un seul** vrai téléphone (Samsung Galaxy S23 FE) notés dans [docs/essais-reels.md](docs/essais-reels.md). Chaque site est différent : le comportement se confirme à l'usage, avec l'historique et le centre de diagnostic.
- **Durée** : un refus simple prend quelques secondes ; avec « Refus complet », une très longue liste de partenaires peut prendre jusqu'à 2 minutes (délai maximum par fenêtre).
- **Samsung Internet** : non pris en charge (voir section 5).
- **Cases dans une page web dont l'état n'est pas publié** : CYRAVA ne sait pas si un appui activerait ou désactiverait la case ; par sécurité il **ne les touche pas** et le résultat est **Refus partiel**.
- **Interfaces dessinées dans un moteur de jeu** (Unity/Unreal sans vues Android ni WebView) : invisibles pour l'accessibilité, donc non traitées.
- **Formulations ou langues inconnues** : seules les formules des règles sont reconnues. Une fenêtre inconnue n'est jamais cliquée au hasard.
- **Fenêtres très grandes** : lues en partie seulement ; CYRAVA peut au plus cliquer un « Tout refuser » visible, une fois, et annonce le résultat « non vérifiable ».
- **Murs « payer ou accepter »** : aucune option de refus gratuite → rien n'est cliqué.
- **Économie d'énergie** de One UI : peut arrêter le service sans prévenir ; suivez la section 4.
- **Anti-triche** de certains jeux en ligne : voir la section 5.

## Licence

CYRAVA est un logiciel libre, © 2026 NoBackFace, distribué sous la **GNU General Public License v3.0** (texte complet : [LICENSE](LICENSE)).

En résumé : vous pouvez utiliser, étudier, modifier et redistribuer CYRAVA. Si vous distribuez une version modifiée, vous devez la publier sous la même licence, avec son code source. Le logiciel est fourni sans aucune garantie.

Le nom « CYRAVA » et son logo désignent ce projet : merci de donner un autre nom à une version modifiée.
