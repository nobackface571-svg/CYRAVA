🇬🇧 [English](#changelog-english) | 🇫🇷 [Français](#journal-des-versions)

# Changelog (English)

## 1.1.0 beta 4

*Tag `v1.1.0-beta.4`, 27/11/2026.*

- **History in the app's language (27/11)**: the engine's explanations (e.g. "only an accept/save button is offered") and "generic TCF" are now shown in English when the app is in English. The saved history and the JSON/CSV exports stay in French.
- **Simpler history cards (27/11)**: result, advice and "state verified" first; the technical counters (items recognised, gestures tried…) appear when you tap the card.
- **What to do after a failure (27/11)**: a short tip under each failed or partial result (no reject option, paywall, finish by hand, share a diagnostic…).
- **Battery (27/11)**: Settings › Battery shows how long CYRAVA worked since the service started (screen reads with no cookie window, cookie windows handled). Durations only, not a battery percentage; nothing is saved.
- **Important fix — pages that talk about cookies (27/11)**: with no cookie pop-up on screen, CYRAVA mistook pages *about* cookies (the LinuxFr post, the XDA thread and the GitHub README presenting CYRAVA) for consent pop-ups, and tapped a menu link, scrolled and tapped "More options". In a browser, when the matched area covers almost the whole page, CYRAVA now only starts if it sees a real reject or accept button, or a known platform's identifier (a menu link, a form checkbox, a lone "Save" button or a sentence fragment is no longer enough). After a rejection, the article behind is no longer taken for the pop-up. Checked by automated tests, including the three pages rebuilt from the phone diagnostics (without publishing their content); not yet tried on a phone.

## 1.1.0 beta 3

*Tag `v1.1.0-beta.3`, 27/11/2026. English summary of the changes since beta 2; the full detailed log, in French, follows below (sections dated 22/11 to 27/11). Earlier versions are only documented in French.*

All releases are published in the repository's **Releases** tab, with the APK.

**Unchanged rule: CYRAVA never taps an accept, purchase or subscription button, in any profile.** Unless stated otherwise, the fixes below were checked by the engine's automated tests on simulated pop-ups; only the tries recorded in [`docs/essais-reels.md`](docs/essais-reels.md) were made on a real phone.

### Reliability: never "verified" without proof
- **Independent consent verification** (`ConsentVerificationEngine`): before calling a rejection "verified", CYRAVA re-evaluates all the accessibility evidence. A switch in an unknown state is never counted as off; an incomplete capture or exploration can never give "verified". Its verdict is now mandatory: a session with a truncated capture gives "partial rejection" at best.
- **Never saves without having seen everything**, whatever the "Don't confirm when in doubt" setting: list not read to the bottom, category or partners page not opened → the site's "Reject all" if there is one (result never "verified"), otherwise no tap.
- **Diagnostics name each control that was not verified.**
- The "TCF verification" module was removed (27/11): it was useless in the app, as CYRAVA cannot read other apps' TCF data. The re-check of accessibility evidence stays in place.

### Long lists, faster
- **Very long lists are scrolled to the bottom** (continuous scrolling, screen after screen), with no more 50-scroll limit; CYRAVA stops when nothing new appears.
- **Analysis 6 times faster**, wait before starting capped at 1.5 s, lighter scroll measurement, and **faster screen reading** during long lists (only the cookie pop-up is re-read, with a full re-read at regular intervals and always before closing the pop-up).
- **Lists entirely present in the capture** (e.g. Galaxy Defense, 367 switches): switches still on are turned off without scrolling, then re-read, with two safeguards against lists loaded progressively.
- **Duration of each step measured** in the history export and diagnostics.

### Consent platforms and browsers
- **OneTrust "with tabs"**: each category is opened in turn, turned off, then the selection is confirmed.
- **Didomi**: "Reject / Accept" radio buttons recognised (Firefox); "Save" and "Reject all" hidden under Opera's or Firefox's bottom bar are triggered through accessibility (never a blind tap); a greyed-out "Save" is never touched.
- **"Save" buttons found more reliably** (Commanders Act, AppConsent, buttons below the list); a confirmation button is no longer mistaken for an options button.
- **Consentmanager in Firefox**: falls back to the first screen's "Reject all" instead of failing.
- **Pop-up recognised after scrolling** by its platform identifier, and a slow preferences page is no longer taken for a closed pop-up.
- **Preferences first**: the site's "Reject all" now comes only after the choices made one by one (BFM case), and can be used again when opening the settings makes the pop-up disappear in an unknown app.
- **"No | Yes" buttons on the same row** (France Télévisions) recognised: each "No" is tapped, never "Yes"; the result is never "verified" (state not published).
- **AliExpress**: the "cookie settings" link in the text no longer hides the real buttons next to it.

### App and project
- **"Share all as one .zip file"** button in the diagnostic centre (all diagnostics + history at once).
- **Language switch** without a black screen or app restart.
- **Correct version number** shown in the app (`1.1.0-beta.3`; test APKs show `-ci.<n>`); CodeMagic builds only when started by hand.
- **Privacy**: captured diagnostic exports were removed from the repository, along with the tests that depended on them.

---

# Journal des versions

Toutes les versions sont publiées dans l'onglet **Releases** du dépôt, avec l'APK.

## 1.1.0 bêta 4 (27/11/2026)

### Historique dans la langue de l'appli, conseils et batterie (27/11)
- **Motifs traduits** : en anglais, les explications du moteur (« seul un bouton de validation est proposé… ») et « TCF générique » s'affichent désormais en anglais. L'historique enregistré et les exports JSON/CSV restent en français.
- **Cartes d'historique simplifiées** : résultat, conseil et « état vérifié » d'abord ; les compteurs techniques (éléments reconnus, gestes tentés…) apparaissent en touchant la carte.
- **Que faire après un échec** : un court conseil sous chaque résultat raté ou partiel (pas de refus proposé, mur payant, terminer à la main, partager un diagnostic…).
- **Batterie** : Réglages › Batterie indique le temps de travail de CYRAVA depuis le démarrage du service (lectures d'écran sans fenêtre de cookies, fenêtres traitées). Ce sont des durées, pas un pourcentage de batterie ; rien n'est enregistré.
- Vérifié par les tests automatiques du moteur ; compilation de l'appli à faire sur CodeMagic ; pas encore essayé sur téléphone.

### Correction importante : les pages qui parlent de cookies (27/11)
- **Problème** (essais du 27/11 dans Chrome, profil Strict) : aucune fenêtre de cookies n'était affichée, mais CYRAVA a pris des **pages qui parlent de cookies** pour des fenêtres de consentement : le journal LinuxFr et le fil XDA présentant CYRAVA, et le README GitHub. Sur LinuxFr, il a cliqué sur le lien « Modifier mes préférences » du menu et repéré la case « licence CC BY-SA » du formulaire comme un interrupteur à décocher ; sur XDA, il a fait défiler la page et cliqué « More options » pendant 14 s. Le même problème pouvait se produire sur toute page qui parle de cookies (politique de confidentialité, article sur le RGPD…).
- **Correction** : dans un navigateur, une vraie fenêtre de cookies est un bloc à part. Quand la zone retenue couvre presque toute la page, c'est la page elle-même : CYRAVA n'y démarre plus que s'il voit un vrai bouton de refus ou d'acceptation, ou l'identifiant d'une plateforme connue. Un lien de menu, une case de formulaire, un bouton « Enregistrer » seul, un bouton d'éditeur ou un bout de phrase (« … and accept. ») ne suffisent plus.
- **Et pendant une session** : après un refus, l'article qui réapparaît derrière n'est plus pris pour la fenêtre (CYRAVA continuait à cliquer dessus) ; et sur une plateforme inconnue, des « interrupteurs » trouvés dans la page entière ne prolongent plus la session.
- Vérifié par des tests automatiques, dont les trois pages reconstituées d'après les diagnostics du téléphone (sans en publier le contenu) : aucune action. Pas encore essayé sur téléphone.

## 1.1.0 bêta 3 (27/11/2026)

Étiquette `v1.1.0-beta.3`. Les bêtas 1 et 2 (21/11) n'ont pas eu de section à part : leurs changements (20–21/11) figurent aussi plus bas dans cette section.

- **27/11 — corrections après les essais du 27/11 sur téléphone** (fenêtres reconstituées dans `Essais27NovTest`, simulées ; diagnostics non rejoués dans cette session) :
  - **Préférences (BFM)** : le « Tout refuser » du site, qui fermait la fenêtre sans rien laisser vérifier, passe désormais APRÈS les choix un par un (interrupteurs, « Bloquer », « Refuser » de chaque ligne), puis « Enregistrer ». Il ne sert plus qu'en dernier recours. « Tout désactiver » reste utilisé d'abord.
  - **Firefox (RTBF)** : « Enregistrer » ou « Tout refuser » restés sous la barre de Firefox, dans une fenêtre fixe, sont actionnés par leur nœud (jamais d'appui aux coordonnées) après deux essais sans effet (« faire apparaître » et défilement) pour les amener à l'écran. Avant : 24 s de défilements pour rien.
  - **AliExpress** : le lien « paramètres de cookies » du texte ne cache plus les vrais boutons « Refuser les cookies » / « Personnaliser » à côté.
  - **France Télévisions** : les choix en boutons « Non | Oui » sur une même ligne sont reconnus ; chaque « Non » est cliqué, jamais « Oui » ; résultat jamais « vérifié » (état non publié). Jamais si la question de la ligne porte sur un refus (« Désactiver… ? »), ni si « Non » et « Oui » forment un seul élément, ni pour fermer un premier écran. Même règle « jamais vérifié » pour les lignes « Refuser | Accepter ».
  - **« Vérifié » sans preuve (Opera, 12 h 00)** : le verdict du vérificateur indépendant est maintenant obligatoire ; une session dont une capture était tronquée donne au mieux « refus partiel ».
  - **Diagnostic** : bouton « Tout partager en un fichier .zip » (tous les diagnostics + l'historique, en une fois).

- **27/11 — module « vérification TCF » retiré** : il ne servait à rien dans l'appli (CYRAVA ne peut pas lire les données TCF des autres applis). La relecture des preuves d'accessibilité avant un refus « vérifié » reste en place, inchangée. Les entrées des pull requests n° 10 et n° 12 ci-dessous décrivent l'état avant ce retrait.

- **23/11 au soir → 26/11 — vérification indépendante du consentement, TCF strict, diagnostic nominatif** (pull requests n° 2 à n° 14 de ce dépôt) :
  - **Budget CI** (23/11, PR n° 2 à n° 4) : autorisation explicite exigée avant toute dépense GitHub Actions ; chemin du catalogue CMP corrigé dans les tests ; vérificateur de workflows corrigé et testé sur des configurations valides.
  - **Confidentialité** (24/11, PR n° 5 et n° 7) : exports de diagnostic capturés retirés du dépôt ; les tests de relecture qui en dépendaient ont été retirés, les régressions indépendantes sont conservées.
  - **Interface** (24/11, PR n° 9) : changement de langue sans écran noir ni redémarrage de l'activité.
  - **Vérification indépendante du consentement** (25/11, PR n° 10) : nouveau module `ConsentVerificationEngine` — réévaluation stricte des preuves d'accessibilité avant tout refus « vérifié » (un interrupteur d'état inconnu n'est jamais compté désactivé ; capture ou exploration incomplète = pas de vérification) et vérification TCF sur instantané normalisé (consentements ET intérêts légitimes, par finalité et par fournisseur). Une donnée absente donne « INCOMPLETE », jamais « vérifié ». Limite assumée : CYRAVA ne lit pas les données TCF des autres applis ; un adaptateur de confiance reste à écrire.
  - **Refus vérifiable** (26/11, PR n° 11) : diagnostic nominatif des contrôles non vérifiés (le libellé lisible de chaque interrupteur est mémorisé et joint au diagnostic automatique) ; « préférences d'abord » étendu hors navigateur pour les plateformes inconnues.
  - **Couverture TCF stricte** (26/11, PR n° 12) : chaque fournisseur divulgué doit avoir un état explicite dans les deux cartes (consentement et intérêt légitime) et chaque finalité attendue aussi ; toute couverture manquante ou partielle bloque le résultat « vérifié ».
  - **Refus direct après perte des préférences** (26/11, PR n° 13) : quand l'ouverture des réglages fait perdre la fenêtre sur une appli inconnue, le « Tout refuser » du site redevient possible.
  - **Tests** (PR n° 6, n° 8, n° 14) : rejet des branches legacy, défilement (Outcome nullable), scénario « préférences d'abord » — la mémoire de l'appli et le délai anti-répétition doivent survivre à la recréation du moteur.
  - À faire : relancer la compilation CodeMagic complète (tests, lint, APK) pour valider l'ensemble sur `main`.

- **23/11 — essais de la version ci.11 (22/11 17 h 58, 23/11 14 h 40 – 14 h 44), diagnostics rejoués** (fichiers de diagnostic depuis supprimés pour raisons de confidentialité, 24/11) :
  - **Galaxy Defense (Google UMP), « liste pas lue jusqu'en bas », 116 s** : la liste des 367 interrupteurs était entièrement dans la capture, mais cette fois elle bougeait : CYRAVA la parcourait écran par écran (3,8 s par défilement) et le délai s'épuisait. La règle « liste entièrement présente » (pull request n° 1) s'applique maintenant aussi à une liste qui défile, avec **deux preuves** (relecture sécurité : une liste chargée au fur et à mesure aurait été validée sans avoir été vue en entier) : les lignes sous l'écran sont presque toutes de hauteur nulle (Galaxy Defense : 364 sur 365), et la dernière ligne, amenée à l'écran une fois, ne fait apparaître aucune ligne nouvelle. Sinon, défilement normal. Exception : un interrupteur cliqué hors de l'écran et relu encore activé est amené à l'écran pour son second essai. Rejoué : validation sans défilement, 367 interrupteurs lus désactivés (« vérifié ») ; liste chargée au fur et à mesure simulée : parcourue jusqu'au bout avant toute validation.
  - **Lecture rapide plus lente que la lecture normale** (Galaxy Defense : 6 lectures rapides en 68 s, 11 s chacune, contre 5,8 s pour une lecture complète) : chaque lecture est maintenant chronométrée, et la lecture rapide est abandonnée pour la session dès qu'elle est plus lente qu'une lecture complète. La cause exacte côté Android n'est pas connue : à observer dans les durées de l'historique.
  - **OneTrust dans Opera (14 h 40), « aucun contrôle de préférence accessible reconnu »** : les onglets de catégories étaient rangés dans une enveloppe intermédiaire, que CYRAVA ne regardait pas. Ils sont maintenant trouvés (6 catégories sur le diagnostic) et ouverts un par un. L'enveloppe n'est ouverte que si elle est seule dans la liste, et un refus ou une ouverture d'options n'est jamais pris pour un onglet.
  - **Commanders Act dans Firefox (14 h 43), « 5 interrupteurs impossibles à lire ou à désactiver »** : sur la page des partenaires capturée, les 4 cases sont lisibles et les 3 cochées sont décochables. Les autres venaient de la page des finalités, visitée avant, dont aucun diagnostic n'existe : **pas encore corrigé**.
  - Nouvel outil de test : rejouer un fichier de diagnostic réel dans le moteur (`DiagnosticReplay`, retiré le 24/11 avec les exports de diagnostic).

- **22/11, fin d'après-midi — échecs restants du troisième essai (16 h 42 – 16 h 50)** : les fichiers de diagnostic n'étaient pas joints à cette session ; fenêtres reconstituées d'après les constats de `docs/essais-reels.md`, causes déduites du code. À revérifier sur le téléphone.
  - **Didomi dans Opera (16 h 47, « plus rien ne bougeait depuis 12 s »)** : le pied de la fenêtre (« Refuser tout », « Enregistrer ») est écrasé sous la barre du bas d'Opera (hauteur nulle) ; aucun défilement ne peut le faire apparaître. Ces deux boutons (seulement : refus global d'au moins deux mots ou identifiant, et validation) sont maintenant actionnés par l'action d'accessibilité de leur nœud, jamais par un appui aux coordonnées, avec le garde-fou, la relecture de la cible et toutes les conditions de validation habituelles. Ils comptent aussi pour délimiter la fenêtre. **« Enregistrer » grisé** (une finalité sans choix) : jamais touché ; le « Tout refuser » du site termine (résultat jamais « vérifié »), sinon la fenêtre est laissée à l'utilisateur au lieu d'attendre 12 s.
  - **« La fenêtre s'est fermée sans validation »** (Didomi dans Firefox 16 h 44, Sourcepoint dans Opera 16 h 46) : deux causes possibles, corrigées toutes les deux. (1) Après un défilement, le titre et le texte de la fenêtre sortent de l'écran (Firefox ne présente que ce qui est affiché) et le conteneur de la plateforme dépasse en haut : plus aucun indice, fenêtre crue fermée. Pendant une session dans un navigateur, sans refus ni validation cliqué, la fenêtre est maintenant reconnue à l'identifiant de la plateforme reconnue au départ (et seulement elle). Ce mode n'ouvre jamais de session. (2) Après « Personnaliser », un centre de préférences qui s'ouvre dans un nouveau cadre (Sourcepoint) laisse la page vide un instant : la fermeture était conclue après 0,75 s, désormais après environ 2 s quand aucun refus ni validation n'a été cliqué.
  - **Consentmanager dans Firefox (15 h 11, « aucun contrôle de préférence accessible reconnu »)** : avec « Refus complet », le « Tout refuser » du premier écran était mis en attente puis perdu dans des réglages illisibles (catégories en sous-pages). CYRAVA revient maintenant une fois au premier écran (bouton « Retour ») et y clique « Tout refuser » (« non vérifiable »). Et pour les fenêtres suivantes de cette plateforme, jusqu'au redémarrage du service, le « Tout refuser » n'est plus mis en attente. **Pas encore fait** : parcourir les sous-pages une à une (il faut le fichier de diagnostic de cette fenêtre).
  - **Foot Mercato (Didomi dans Firefox, 16 h 43, « plus rien ne bougeait depuis 12 s »)** : non réglé par la correction de la mesure du défilement, qui ne corrige que celle-ci (son échec aurait été « défilement impossible »). Les corrections ci-dessus (boutons du pied, fenêtre reconnue après défilement) peuvent l'avoir réglé : à revérifier, idéalement avec son diagnostic.
  - **Galaxy Defense (17 h 20, version ci.10, diagnostic reçu) — toujours « défilement impossible »** : la liste « Confirmer nos fournisseurs » ne bouge pas (ni action de défilement, ni balayage), mais la WebView présente **toutes** ses lignes, celles sous l'écran avec une hauteur nulle et leur état (367 interrupteurs ; 81 « intérêt légitime » restés activés). CYRAVA s'arrêtait dès le défilement refusé, sans les décocher. Désormais, si la liste est entièrement dans la capture (capture complète, page web, lignes sous l'écran présentes, état de chaque interrupteur connu), la fin de liste compte comme lue : les interrupteurs activés sont décochés par leur nœud, relus, puis la validation garde toutes ses conditions. Si un interrupteur reste activé ou illisible : pas de validation, comme avant. Liste incomplète dans la capture (Firefox) : échec prudent inchangé. Rejoué sur le diagnostic réel : 80 puis 1 interrupteur décochés au lieu de l'arrêt. Pourquoi la liste ne défile pas : pas trouvé (à observer sur le téléphone).
  - Tests : `Essais22Nov16hTest` (9 tests ; les 6 scénarios échouaient avant la correction ; 362 tests du moteur, lancés ici). L'appli Android (une ligne de l'exécuteur) et le lint : vérifiés par CodeMagic.

- **22/11, 17 h — défilement de nouveau mesuré correctement** (Galaxy Defense, essais de 15 h 05 et 16 h 48) : depuis l'accélération du 22/11 (mesure sur 40 lignes au lieu de 200), seules les premières lignes de la liste étaient mesurées. Une fois défilées hors de l'écran, la WebView leur donne toutes la même position écrasée : aucun mouvement mesuré, chaque défilement était « bloqué » et la fenêtre finissait en « défilement impossible » alors qu'elle fonctionnait avant. Seules les lignes **visibles** sont maintenant mesurées (40 au plus, avec leur rang dans la liste). Partie Android : vérifiée par CodeMagic, pas de test du moteur. Troisième essai du 22/11 consigné dans `docs/essais-reels.md`.

- **22/11 au soir — corrections de l'audit indépendant** (Claude Code, lecture seule, sur `main` après les fusions du 22/11) :
  - **N° 1 — jamais d'enregistrement sans avoir tout vu**, quel que soit le réglage « Ne pas valider dans le doute » : liste pas lue jusqu'en bas, catégorie OneTrust ou page des partenaires non ouverte → le « Tout refuser » du site s'il existe (résultat jamais « vérifié »), sinon aucun clic (« à terminer à la main » avec le réglage, « refus impossible » sans). Avant, le réglage désactivé, « Enregistrer » était cliqué et le site recevait des choix jamais vus, peut-être acceptés (« refus partiel »). Le réglage est activé par défaut : la plupart des installations n'étaient pas concernées.
  - **N° 2 — README** : « Ne pas valider dans le doute » est **activé** par défaut (le README disait l'inverse depuis une version précédente).
  - **N° 3 — README** : l'exception à « jamais deux clics » est écrite (un interrupteur décoché hors de l'écran, relu encore activé une fois visible, a droit à un second et dernier essai) ; un test garantit qu'il n'y a jamais de troisième clic.
  - README : parcours de refus à jour (longues listes, onglets OneTrust, validation seulement si tout a été vu), signature via CodeMagic seulement, et marche à suivre pour publier une version depuis le téléphone.
  - Tests : `Audit22NovTest` (2 échouaient avant la correction ; 352 tests du moteur).

- **22/11 au soir (second essai sur téléphone, 15 h 05 – 15 h 16) — OneTrust en onglets, Didomi en boutons radio, AppConsent, lecture plus rapide** :
  - **Essai consigné** dans `docs/essais-reels.md` : 17 fenêtres (Chrome, Firefox, Opera, Galaxy Defense), 8 diagnostics partagés.
  - **OneTrust « en onglets »** (Opera 15:08, Chrome 15:13) : la liste « Cookie Categories » n'affiche l'interrupteur que de la catégorie sélectionnée ; CYRAVA concluait « aucun contrôle de préférence accessible reconnu ». Chaque catégorie est maintenant ouverte (un onglet n'enregistre rien), décochée, puis la sélection est confirmée. Un interrupteur dessiné en retard n'est pas sauté ; un onglet sous l'écran est amené à l'écran ; si une catégorie n'a pas pu être ouverte, rien n'est validé (« à terminer à la main »).
  - **Didomi en boutons radio, Firefox** (14:13) : chaque radio est seul dans son enveloppe, la paire « Refuser X / Accepter X » n'était jamais formée, d'où « un choix radio coché n'est pas un refus reconnu ». Le groupe est cherché jusqu'à 3 niveaux plus haut ; la règle reste stricte (exactement deux radios, même suite). Pas de diagnostic de cette fenêtre : cause déduite du code, à revérifier.
  - **Didomi, Chrome** (15:13) : choix déjà tous sur « Refuser », pas de liste défilante, boutons du pied sous l'écran : balayages sans effet puis « défilement impossible ». Le bouton « Enregistrer » est maintenant amené à l'écran d'un coup, puis cliqué. Après un défilement impossible, un « Tout refuser » sous l'écran est aussi amené à l'écran (une fois) avant d'être cliqué.
  - **AppConsent** (Opera 14:19, « bouton de validation introuvable ») : sans diagnostic de cette fenêtre, améliorations générales vérifiées sur une fenêtre simulée : une validation sous l'écran compte pour délimiter la fenêtre (elle est amenée à l'écran avant tout clic), jusqu'à 6 niveaux dans le conteneur d'une plateforme connue ; 13 formules de validation ajoutées (« Valider mes préférences », « Enregistrer ma sélection »…) ; marqueur « appconsent » reconnu. **Corrigé au passage** : un bouton de validation n'est plus pris aussi pour un bouton d'options (« Valider mes préférences » contient « préférences » et était cliqué comme une simple ouverture des réglages : « la fenêtre s'est fermée sans validation »).
  - **Lecture de l'écran plus rapide, en mode prudent** : pendant une longue liste (décochage, défilement), seule la fenêtre de cookies est relue (chaîne de ses ancêtres, puis son contenu entier), plus la page derrière. Lecture complète après tout autre clic, au moins une fois toutes les 5 lectures, si la fenêtre a disparu ou n'est plus la fenêtre active, et toujours avant de fermer la fenêtre (validation, refus) ; le moteur n'utilise une lecture rapide que s'il y retrouve exactement la même fenêtre, lue en entier. Nouvelle durée mesurée : `lectureFenetre`.
  - **Non traités cette fois** (diagnostics reçus) : Consentmanager dans Firefox (catégories en sous-pages), Didomi dans Opera « fenêtre fermée sans validation » (deux cas), Galaxy Defense (Google UMP, « défilement impossible »).
  - Tests : `DidomiRadios22NovSoirTest`, `OneTrustOnglets22NovSoirTest`, `AppConsent22NovSoirTest`, `LecturePartielleTest` (348 tests du moteur). Non compilé ici : l'appli Android et le lint sont vérifiés par CodeMagic. Rien de tout cela n'a encore été essayé sur un vrai téléphone.

- **22/11 (après les essais sur téléphone) — plus rapide, boutons « Enregistrer » mieux trouvés** :
  - **Essais sur un vrai téléphone** (Galaxy S23 FE, Android 16, profil Strict) : 20 fenêtres dans Chrome, Firefox, Opera et Galaxy Defense, consignées dans `docs/essais-reels.md`.
  - **Analyse 6 fois plus rapide** : sur une longue liste, l'analyse relisait toute la liste pour chaque interrupteur (Galaxy Defense, 367 cases : 1,35 s par étape mesurée sur le téléphone, 432 ms sur ordinateur → 69 ms). Résultat identique sur les 12 diagnostics des essais.
  - **Attente avant de commencer** plafonnée à 1,5 s (jusqu'à 6 s observées dans Chrome) ; **mesure d'un défilement** sur 40 éléments au lieu de 200 relus plusieurs fois (1 à 1,5 s par défilement observées).
  - **Commanders Act** : « Enregistrer la configuration des cookies » (et son identifiant `save-consent`) reconnu ; avant : « bouton de validation introuvable » ou « défilement impossible ».
  - **Bouton de validation sous la liste des choix** (Didomi dans Opera) : la zone analysée s'étend jusqu'au bouton « Enregistrer… » le plus proche (3 niveaux au plus, jamais toute la page).
  - **Motif d'échec plus juste** : un bouton bloqué sur le premier écran (« S'abonner ») n'est plus donné comme cause d'un échec survenu dans les préférences.
  - Tests : `Rapport22NovTest`, `StabilityGateTest` (332 tests du moteur). Non compilé ici : l'appli Android et le lint sont vérifiés par CodeMagic.

- **22/11 — Défilement continu « façon TikTok / Webtoon », numéro de version, CodeMagic manuel** :
  - **Très longues listes parcourues jusqu'en bas** : plus de plafond de 50 défilements par page (une liste de 600 partenaires en demande ~75). CYRAVA défile écran après écran en décochant à chaque fois, **tant que de nouveaux choix apparaissent** ; s'il défile 15 fois de suite sans rien de nouveau (bandeau animé, compteur), il s'arrête et le résultat n'est jamais « vérifié ». Délai par fenêtre porté à **120 s** (maximum autorisé), l'arrêt après 12 s sans aucun changement à l'écran reste en place.
  - **Chrome, listes de centaines de partenaires** : la mesure du défilement était jugée « illisible » dès que la liste dépassait 200 éléments, et chaque défilement était déclaré « bloqué » (jamais de validation). Elle se fait maintenant sur les premiers éléments ; la fin de liste exige toujours qu'Android ne propose plus de défiler.
  - **Balayage sans élan** : en fin de balayage, le doigt reste posé un instant avant de se lever, pour que la liste s'arrête net au lieu de sauter des lignes jamais lues.
  - **Diagnostic automatique** aussi pour les fenêtres « À terminer à la main » (si l'option est activée).
  - **Numéro de version** : défini à un seul endroit (`cyrava.version` dans `gradle.properties`, ici `1.1.0-beta.3`) et affiché tel quel dans l'appli signée (avant : toujours « 1.1.0-perso ») ; les APK de test affichent `1.1.0-beta.3-ci.<n>`.
  - **CodeMagic** : plus aucun déclenchement automatique ; chaque compilation est lancée à la main.
  - **Durée de chaque étape mesurée** (pour trouver où passe le temps) : attente d'affichage, lecture de l'écran, analyse, décochage, défilement, autres clics, attentes et pauses, avec leur nombre, la durée totale et la taille de la plus grande copie d'écran. Visible dans l'export JSON de l'historique (champ `durees`, en millisecondes), l'export CSV et les fichiers de diagnostic. Uniquement des durées et des nombres, aucun texte d'écran. Les entrées enregistrées avant cette version n'en ont pas.
  - Réglage « Refus complet » : le texte indique qu'une très longue liste peut prendre jusqu'à 2 minutes.
  - Non testé sur un vrai téléphone : tests du moteur uniquement (`Defilement22NovTest`, simulation d'une liste de 600 partenaires).

- **Nouvel identifiant de l'appli : `fr.cyrava`** (au lieu de `fr.refusbot`). Pour Android, c'est une autre appli : **les testeurs de la bêta 1 doivent désinstaller l'ancienne version avant d'installer la nouvelle** (Paramètres › Applications › CYRAVA › Désinstaller). Les réglages et l'historique de la bêta 1 ne sont pas repris ; il faudra réactiver le service d'accessibilité, autoriser les « paramètres restreints » et recocher les applis surveillées. Le code garde ses paquets `fr.refusbot`.
- **Chrome, 21/11 dans la nuit : très longues listes, page prise pour une fenêtre, diagnostic au mauvais moment** (fenêtre Google UMP d'alternativeto.net, près de 480 contrôles : seulement 7 interrupteurs décochés avant la fin du temps) :
  - **Décochage en rafale hors de l'écran** : Chrome présente toute la page, y compris les lignes de la liste qui ne sont pas encore à l'écran, avec leur état. CYRAVA les décoche maintenant **sans défiler**, par lots de 80, avec l'action d'accessibilité de la ligne (jamais un appui aux coordonnées), puis relit leur état à la capture suivante. Même garde-fou, même relecture de la cible (même fenêtre, même libellé) ; seule la visibilité n'est plus exigée pour ces interrupteurs. Si un site ignore ce clic, l'interrupteur a droit à **un** second essai quand le défilement le rend visible.
  - **Bouton de validation amené à l'écran d'un coup** (« afficher à l'écran » d'Android, un défilement, pas un clic) une fois toute la liste décochée, puis un dernier défilement pour constater la fin de la liste avant d'enregistrer. Simulation d'une liste de 300 partenaires : 7 étapes au lieu de dizaines de balayages.
  - **Avancement affiché** pendant une longue liste (notification silencieuse « 120 sur 480 choix traités — laissez faire, CYRAVA travaille »), retirée à la fin. Rien n'est affiché si les notifications sont réglées sur « Aucune ».
  - **Faux positif corrigé** : le lien « Consent-O-Matic » d'une page AlternativeTo était pris pour un bouton d'acceptation « Consent », et la page pour une bannière. Une acceptation d'un seul mot (« Consent », « Accepter ») n'est plus reconnue que seule ou avec des mots neutres (« Accepter et continuer »). Sans effet sur la sécurité : une acceptation n'est jamais cliquée.
  - **Fenêtre terminée à la main** : quand la fenêtre d'une plateforme reconnue disparaît, la page du site derrière (avec des mots comme « cookie » et des boutons « J'aime » cochables) n'est plus prise pour la même fenêtre. Résultat : « la fenêtre s'est fermée sans validation » au lieu de « bouton de validation introuvable », et le **diagnostic montre la fenêtre de cookies**, plus la page.
  - **Plus de clics sur une page ordinaire** : sur la page GitHub d'une pull request (texte parlant de cookies, lien « Manage cookies » en bas de page), CYRAVA avait ouvert le menu « Show options » d'un commentaire puis la fenêtre « Notifications › Customize ». Désormais, des boutons « Options », « Personnaliser » ou « Gérer les cookies » seuls ne suffisent plus pour commencer : il faut un vrai choix visible (refus, acceptation, validation, choix radio), des interrupteurs, ou l'identifiant technique d'une plateforme de cookies connue.
  - Tests : `Rapport21NovNuitTest`.
- **Historique Opera / Firefox du 21/11 au soir : défilement continu, plus rapide, sans délai bloquant** :
  - **Défilement « à la Fylow » sur toutes les plateformes** : quand la page web n'expose pas de liste défilante (cas fréquent dans les navigateurs), CYRAVA balaie maintenant **dans la fenêtre de cookies** (et non plus sur toute la page), **encore et encore tant que la liste bouge**, en décochant à chaque écran, jusqu'à ce qu'un balayage ne change plus rien (bas de la liste). Avant : un seul balayage d'essai, d'où des listes jamais lues en entier.
  - **Défilement jugé « bloqué » moins souvent** : seconde mesure 250 ms après un défilement (les pages web mettent l'écran à jour en retard). Si le défilement est vraiment impossible, le « Tout refuser » de la fenêtre est utilisé au lieu d'un échec ; jamais de validation dans ce cas, et jamais « vérifié ».
  - **Fini les 45 s bloquées** : la session s'arrête dès que **plus rien ne bouge à l'écran pendant 12 s** ; tant que ça avance, elle peut continuer jusqu'à **90 s** (longues listes de partenaires).
  - **Refus complet (intérêt légitime)** gardé sur toutes les plateformes reconnues ; **OneTrust** : le « Tout refuser » de la page des préférences (`ot-pc-refuse-all-handler`) est reconnu et sert de repli quand les interrupteurs sont illisibles (avant : « refus impossible »).
  - **Jamais « vérifié » sur une liste pas lue jusqu'en bas**, y compris sans liste défilante (balayages).
  - **Rythme plus rapide**, garde-fou inchangé : pause entre deux actions 80–200 ms (avant 150–400), regroupement des événements 200 ms (avant 300), attente après un clic 250 ms (avant 300), contrôle d'affichage toutes les 250 ms (avant 400), pause après un défilement 50 ms (avant 100), balayage 280 ms (avant 350).
  - Tests : `Rapport21NovSoirTest`.
- **Corrections de l'audit et du contre-audit du 21/11** (rapport : `docs/audit-2026-11-21.md` si publié) :
  - **Jamais « vérifié » sans avoir lu toute la liste** : si la liste n'a pas été parcourue jusqu'en bas (ou si la page des partenaires n'a pas pu être ouverte faute de temps), la validation est laissée à l'utilisateur (« À terminer à la main ») avec le réglage « Ne pas valider dans le doute » ; sans ce réglage, le résultat est « Refus partiel », jamais « vérifié ».
  - **Jamais d'« Enregistrer » avec un choix connu comme accepté** : un interrupteur vu activé et jamais traité (sorti de l'écran, grisé, fin du temps) bloque la validation dans le doute. Après une validation, un interrupteur resté activé ou une capture incomplète donnent « Refus partiel » (avant : « Refusé avec le bouton du site », voire « vérifié »). Liste trop longue avec « Tous les partenaires › Bloquer » : pas de validation si un « Autoriser » sélectionné ou un interrupteur activé est visible.
  - **Défilement continu** : défiler → lire → décocher sans pause inutile. Un défilement ne compte plus dans les 40 actions (plafond à part : 50 par page, 120 par fenêtre, et le délai de 45 s) ; la fin de liste mesurée par Android est prise en compte tout de suite au lieu d'attendre ≈ 1 s ; plus de pause aléatoire après un défilement. Le défilement reste permis jusqu'à 80 % du délai (avant : 60 %).
  - **Navigateurs** : après un refus, seul le site traité est mis en attente (avant : tout le navigateur pendant 20 s ; après un échec, toute bannière identique d'un autre site pendant 10 min).
  - **Garde-fou** : mots d'achat et de paiement ajoutés (« achat », « commande », « order », « pay », « payment », « buy », « checkout », « panier », « kauf », « bezahl », « compra »…) ; seuls des mots neutres sont tolérés autour d'une formule de validation (« Confirmer la commande » n'est plus un « Confirmer ») ; tout texte dans un autre alphabet (cyrillique, grec, chinois…) bloque le clic ; « Je n'accepte que… » et la double négation ne sont plus pris pour des refus ; un bouton reconnu par son seul identifiant doit avoir un texte lisible.
  - **Validation jamais par appui aux coordonnées**, même en secours.
  - **« Tout cocher »** n'inclut plus les principales banques, applis de paiement et de santé françaises.
  - **Règles changées pendant un refus** : la session en cours est notée « interrompue » dans l'historique au lieu de disparaître.
  - Formulaire de signalement : avertissement bien visible, le fichier de diagnostic joint est public.
  - Documentation : délai de 45 s (et non 30 s) ; « Ne pas valider dans le doute » est **activé** par défaut (la mention ci-dessous « désactivé par défaut » date d'une version précédente).
  - Tests : `Audit21NovTest` (un test par constat).

- **Refonte du parcours de refus (21/11), commun à toutes les plateformes** (Didomi, Sourcepoint, Axeptio, OneTrust, Quantcast, Cookiebot, inconnues). Constat : sur Didomi (boursorama.com, Firefox), CYRAVA dépliait en boucle les lignes « + » (« Catégories de données », « Stockage de l'appareil », noms de partenaires…), la capture devenait incomplète, 29 « Bloquer » étaient exigés et « Voir nos partenaires » / « Enregistrer » n'étaient jamais cliqués ; sur Sourcepoint, « bouton de validation introuvable ».
  - **Plus aucune section dépliée** ; nouvelle liste noire (`NavigationFilter`) : accordéons, « En savoir plus » / « Détails » dans les réglages, pages externes.
  - **Refus global d'abord**, puis seulement les finalités **visibles** ; dans les partenaires, seul le refus global (« Tous les partenaires › Bloquer ») est cliqué, et il suffit à la vérification (les choix un par un ne sont plus exigés).
  - **Bouton de validation caché** : défilement jusqu'en bas de la fenêtre (ou balayage) avant de conclure qu'il manque.
  - **Anti-boucle** : jamais deux clics sur le même élément (fin du second essai « par tap ») ; un clic sans effet fait passer à l'étape suivante.
  - Conséquence : une fenêtre dont les choix sont tous cachés dans des sections repliées, sans refus global, n'est plus traitée (« Refus impossible ») au lieu d'être dépliée.
  - Relecture sécurité : un « Autoriser » vu sélectionné n'est jamais ignoré (même sous « Tous les partenaires › Bloquer » ou hors de l'écran) ; jamais d'enregistrement avec un interrupteur visible encore activé et non traité ; pas de défilement de recherche en fin de délai ; « ‹ Retour » n'est plus pris pour un accordéon ; motif « choix global « Bloquer » vu sélectionné » quand aucun interrupteur n'existe.
  - Tests : un parcours complet par plateforme (premier écran → finalités → partenaires → validation) qui échoue si un accordéon, un partenaire, une page externe ou une acceptation est touché, et des tests « bouton de validation hors écran ».

- **Essais du 20-21/11 (Firefox, Opera)** :
  - Refus complet : sur une fenêtre de plateforme **inconnue**, un refus global (« Tout refuser ») est cliqué tout de suite ; un « Refuser » seul attend toujours les préférences. Ses options pouvaient ouvrir une autre page (Google « Plus d'options », fenêtre de cookies d'Opera) : la fenêtre disparaissait et rien n'était refusé (« Action interrompue »).
  - Nouvelle formule de refus : « Refuser tous les paramètres » (fenêtre de cookies d'Opera).
  - Profil Prudent dans un navigateur : une fenêtre reconnue avec certitude est traitée même si la plateforme n'est pas nommée (chaque site a sa propre fenêtre ; 4 sites seulement observés dans Firefox). Ces fenêtres ne sont plus marquées « interface modifiée » (historique, onglet Applications, notifications). Une fenêtre seulement « probable » (ex. Google « Avant d'accéder à Google ») reste observée en Prudent.

- **Garde-fou plus strict** : « pas » ou « jamais » seuls devant « accepter » ne valent plus négation (« Ne ratez pas : accepter » reste bloqué) ; seuls « ne pas / ne jamais accepter », « n'accepte », « sans accepter »… passent. Une question n'est jamais prise pour un refus (« Pourquoi ne pas accepter ? », « Why not accept all? » bloqués).
- **Messages de capture traduits** (tuile « Capturer l'écran », partage d'un diagnostic). L'écran de secours après un plantage reste volontairement en français.
- **Consignes** : le projet ne travaille plus qu'avec Claude ; consignes Copilot retirées, `AGENTS.md` et `CLAUDE.md` mis à jour (CodeMagic).
- **Ménage** : anciens libellés français inutilisés retirés du code (l'interface passe par les traductions), vieux documents d'audit, de GPT et de recherche supprimés, README mis à jour (nom CYRAVA, anglais, CodeMagic, Samsung Internet). Test émulateur de l'accueil corrigé (le texte « Refuser automatiquement » n'existe plus).

- **Langues (étape 4)** : Réglages, Laboratoire, Centre de diagnostic, barre d'onglets, diagnostic automatique et messages des tuiles traduits en anglais. Restent en français : l'écran de secours après un plantage (volontairement sans traduction), les motifs détaillés du moteur, les étapes simulées du laboratoire et les fichiers exportés.
- **Langues (étape 3)** : Historique, Compatibilité et notifications traduits en anglais (statuts, filtres, détails, dates). Les motifs détaillés venant du moteur et les exports JSON/CSV restent en français. Samsung Internet est aussi signalé dans l'onglet Compatibilité.
- **Langues (étape 2)** : Accueil et onglet Applications traduits en anglais (libellés des résultats, de la compatibilité et des profils compris). Les jours du graphique suivent la langue choisie.
- **Samsung Internet non pris en charge** : il ne transmet pas le contenu des pages aux services d'accessibilité (diagnostics du 20/11 : seuls les boutons du navigateur sont visibles). L'onglet Applications l'indique et « Tout cocher » ne le coche plus ; il reste possible de le cocher soi-même.

- Rangement : onglet Actions en deux familles (« 🧪 Essais — … » et « 📱/🔐/🚀 Application — … »), page `docs/essais-reels.md` pour les seuls essais sur vrai téléphone. Les APK de test s'appellent `RefusBot-release-test.apk`, `RefusBot-debug.apk` et `RefusBot-demo-debug.apk`.
- Tests automatiques du moteur : 267 (87 à la sortie de la 1.1.0). Développement uniquement sur `main`.

- **Profil Prudent par défaut pour une nouvelle installation.** Une installation existante garde son profil (Strict, l'ancien défaut, si aucun n'avait été choisi).

- **Réglage « Ne pas valider dans le doute »** (désactivé par défaut) : si des cases n'ont pas pu être lues ou désactivées, RefusBot ne valide pas et laisse la fenêtre ouverte (nouveau résultat « À terminer à la main »).
- **Notifications plus utiles** : quand des choix n'ont pas pu être vérifiés, la notification dit quoi faire.

- **Interrupteurs : un clic n'est plus compté comme une désactivation.** Seuls les interrupteurs relus désactivés après le clic comptent. Un interrupteur dont le nouvel état n'a pas pu être relu rend le refus « partiel ».
- **Compte rendu détaillé** dans l'historique et les exports : détectés, activés au départ, déjà désactivés, désactivés et vérifiés, restés activés, cliqués sans relecture, état illisible. « actions » devient « clics de navigation ».
- **Expérience WebView** (APK de test seulement) : le service écoute tous les types d'événements, pour vérifier sur l'émulateur si Chrome publie alors l'état des cases.
- **Protocole d'essai sur téléphone** : `docs/protocole-test-telephone.md`.

Corrections issues d'une revue de sécurité.

- **Diagnostic WebView** : les captures recensent les clés `availableExtraData` annoncées par les nœuds interrupteurs (métadonnées uniquement ; aucune donnée lourde demandée et aucun effet sur les décisions d'état).

- **Grandes fenêtres** : une fenêtre trop grande pour être lue en entier n'était plus traitée du tout. RefusBot peut désormais y cliquer un « Tout refuser » visible (une seule fois, résultat « non vérifiable ») ; options et interrupteurs restent interdits sur une fenêtre lue en partie.
- **Phrases ambiguës** : autour d'une formule de refus, seuls des mots neutres sont acceptés. « Pourquoi refuser », « Peut-on refuser », « Refuser coûte 2 € » ne sont plus pris pour un refus.
- **Onglet Applications** : le bilan ne compte que les applis cochées, chacune dans une seule catégorie (dont « observée ou interrompue ») ; libellés « dernier résultat » au lieu de « test réel » ; couleurs adaptées au thème sombre ; le bouton d'ouverture explique le délai de 10 minutes après un échec.
- **Thème** : fonds des cartes accordés à la palette bleue (plus de teinte violette par défaut).
- **Vérifications** : le test sur émulateur tourne aussi sur `main` et sur toute demande de fusion vers `main` (dont les mises à jour automatiques) ; la signature de développement se lance depuis `main`.

- **Sens inversé** : « Ne pas refuser », « Do not reject » ne sont plus pris pour un refus, ni à l'écran ni dans un fichier de règles importé.
- **État des interrupteurs** : la description publiée par l'appli (« activé ») est prise en compte ; un état illisible est « inconnu », jamais supposé désactivé.
- **Plus de succès sans preuve** : un clic de refus suivi de la fermeture de la fenêtre donne « Refus cliqué, résultat non vérifiable ». Le résultat « refus direct réussi » n'existe plus. Un seul interrupteur d'état inconnu donne « Refus partiel ».
- **Écran modifié** : la cible est relue juste avant chaque action ; aucun appui vers une ancienne coordonnée.
- **Champs de saisie** : description et éléments internes jamais lus ni exportés.
- **Publication** : une Release exige une clé personnelle ; les compilations ordinaires n'ont plus ni secret ni droit d'écriture ; actions GitHub épinglées.
- Captures annulées si le service est interrompu ; noms de diagnostics uniques.
- Test sur émulateur : les points non prouvés sont comptés « non vérifiés », plus jamais conformes.

## 1.1.0 — « RefusBot Ultra »

L'appli s'appelle désormais **RefusBot Ultra**. Elle s'installe par-dessus la 1.0.0 : réglages et historique sont conservés.

### Nouveautés

- **Cinq onglets** : Accueil, Applications, Historique, Compatibilité, Réglages.
- **Tableau de bord** (Accueil) : activité du jour, des 7 derniers jours et de l'historique conservé, graphique sur 7 jours, derniers événements. Tous les chiffres viennent de l'historique réel du téléphone.
- **Arrêt d'urgence** : stoppe immédiatement l'action en cours et met la protection en pause.
- **Trois profils** : Strict (par défaut), Prudent (n'agit que sur les fenêtres reconnues avec certitude et s'arrête si l'interface d'une appli change), Observation (n'agit jamais).
- **Mode par appli** : refus automatique ou observation seule.
- **12 plateformes reconnues** (Google UMP, Didomi, OneTrust, Sourcepoint, InMobi Choice, Usercentrics, Sirdata, Axeptio, Cookiebot, TrustArc, Consentmanager, iubenda) et les fenêtres TCF génériques.
- **7 langues** : français, anglais, allemand, espagnol, italien, portugais, néerlandais.
- **Niveau de confiance** de chaque reconnaissance (certaine, probable) affiché dans l'historique.
- **Résultats honnêtes** : refus direct réussi, refus par les préférences réussi, refus partiel, refus impossible, action interrompue, observé. Un refus n'est « réussi » que s'il a été vérifié.
- **Historique enrichi** : plateforme, confiance, nombre d'actions, changement d'interface ; filtres, recherche, export JSON et CSV.
- **Compatibilité** : bilan de chaque appli calculé sur ses 5 dernières fenêtres ; bouton pour valider une nouvelle interface.
- **Notifications réglables** : toutes, échecs uniquement, interruptions uniquement, aucune ; alerte quand le service s'arrête.
- **Centre de diagnostic** : état du système partageable, capture manuelle, fichiers enregistrés.
- **Laboratoire** : six fausses fenêtres simulées pour voir ce que ferait le moteur, sans risque.
- **Fichiers de règles importables**, contrôlés strictement, avec retour à la version précédente ou aux règles d'origine.
- **Sauvegarde et restauration** des réglages ; suppression de toutes les données en un bouton.
- **Thème** : système, clair ou sombre.
- **Page Confidentialité et autorisations**.

### Moteur

- Jusqu'à 600 interrupteurs désactivés par fenêtre (les longues listes de fournisseurs ne consomment plus le quota de 40 actions).
- Dépliage des sections repliées déclarées par l'appli.
- Empreinte de la structure d'une fenêtre (sans aucun texte) pour détecter un changement d'interface.
- Après un échec, attente de 10 minutes avant de réessayer la même fenêtre (plus de notifications en rafale).

### Sécurité

- Un fichier de règles importé ne peut plus affaiblir le garde-fou ni la gestion des interrupteurs : ces sections viennent toujours de l'appli. Chaque formule ajoutée doit ressembler à un vrai bouton de refus, d'options ou de validation.
- Le lecteur de fichiers refuse les fichiers trop gros ou trop imbriqués (un fichier piégé ne peut plus faire planter l'appli).
- Garde-fou étendu aux formes d'acceptation allemandes, espagnoles, italiennes, portugaises et néerlandaises.
- Toujours aucune permission Internet ; la compilation échoue si elle apparaît.

### Corrections

- **Historique effacé après un redémarrage** (présent dans la 1.0.0) : quand le service redémarrait sans que l'appli ait été ouverte — typiquement après un redémarrage du téléphone — la première fenêtre traitée pouvait écraser tout l'historique. Le fichier est maintenant toujours relu avant d'être réécrit, et le test sur émulateur vérifie ce cas.
- Une erreur imprévue dans une tâche de fond ne peut plus arrêter l'appli (et donc le service).

### Vérifications

- 87 tests automatiques du moteur (47 pour la 1.0.0), dont 11 qui essaient de piéger l'appli avec des fichiers de règles malveillants.
- Analyse lint sans erreur.
- Test sur émulateur Android 15 : installation, laboratoire sur l'APK final, activation du service, fausses fenêtres (Google UMP, bandeau, fenêtre sans refus), historique, arrêt d'urgence, historique conservé après un redémarrage du service.
- **Non testé sur un vrai téléphone** ni sur les vraies fenêtres des plateformes : voir les limites du README.

## 1.0.0

Première version : refus automatique dans les applis choisies (refus direct ou options → interrupteurs → validation), garde-fou « jamais accepter », journal, notifications, tuiles « RefusBot » et « Capturer l'écran », mode diagnostic, guide d'installation, compilation et publication automatiques.
