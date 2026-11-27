# Résultats des essais sur téléphone réel

Cette page rassemble **uniquement** des essais faits par une personne sur un vrai téléphone.
Les essais automatiques (émulateur de GitHub, laboratoire de l'appli, tests JUnit) n'y figurent pas :
ils sont visibles dans l'onglet **Actions › 🧪 Essais — émulateur Android**.

Méthode à suivre : [`protocole-test-telephone.fr.md`](protocole-test-telephone.fr.md) (🇬🇧 [English](protocole-test-telephone.md)).
Règle : on recopie ce qui a été constaté, sans interprétation. Si un champ n'est pas connu, écrire « non relevé ».

## Téléphones utilisés

| Téléphone | Android | Navigateur et version |
|---|---|---|
| Samsung Galaxy S23 FE (SM-S711B) | 16 | Chrome, Firefox, Opera (versions non relevées) ; jeu Galaxy Defense |

## Essais

| Date | Version de l'APK | Profil | Site ou appli | Prestataire | Résultat CYRAVA | Interrupteurs (détectés / activés au départ / vérifiés désactivés / illisibles) | Constat manuel | Concordance | Diagnostic partagé | Durée |
|---|---|---|---|---|---|---|---|---|---|---|
| 16/11/2026 | non relevée | Observation | plusieurs sites dans Chrome | OneTrust, Didomi, Axeptio | fenêtres observées, **aucun clic** (profil Observation) | non relevé | non fait | impossible à dire | non | non mesurée |
| 22/11/2026 14:08 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Galaxy Defense | TCF générique | Refus partiel — plus rien ne bougeait à l'écran depuis 12 s | désactivés vérifiés : 86 (autres compteurs non relevés) | non relevé | non relevée | oui | 78.3 s |
| 22/11/2026 14:09 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.2 s |
| 22/11/2026 14:11 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.2 s |
| 22/11/2026 14:12 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | OneTrust | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.0 s |
| 22/11/2026 14:12 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | Commanders Act (TrustCommander) | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.8 s |
| 22/11/2026 14:13 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | Didomi | À terminer à la main — fenêtre laissée ouverte — un choix radio coché n'est pas un refus reconnu ; validation bloquée | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 6.3 s |
| 22/11/2026 14:14 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Firefox | inconnue | Refus impossible — mur payant : pas de refus gratuit sur ce site (accepter ou s'abonner) | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.7 s |
| 22/11/2026 14:14 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | TCF générique | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.6 s |
| 22/11/2026 14:15 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | OneTrust | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 6.4 s |
| 22/11/2026 14:16 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.7 s |
| 22/11/2026 14:16 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.3 s |
| 22/11/2026 14:16 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | OneTrust | Refus impossible — aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 2.6 s |
| 22/11/2026 14:16 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | inconnue | Refus impossible — mur payant : pas de refus gratuit sur ce site (accepter ou s'abonner) | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.5 s |
| 22/11/2026 14:17 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | Didomi | Action interrompue — la fenêtre s'est fermée sans validation | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 10.3 s |
| 22/11/2026 14:18 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | Commanders Act (TrustCommander) | Refus impossible — bouton de validation introuvable, même après défilement jusqu'en bas de la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 37.1 s |
| 22/11/2026 14:18 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | OneTrust | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.0 s |
| 22/11/2026 14:19 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | AppConsent (SFBX) | Refus impossible — bouton de validation introuvable, même après défilement jusqu'en bas de la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 10.7 s |
| 22/11/2026 14:20 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Opera | Didomi | Refus impossible — garde-fou : bouton contenant « abonn » bloqué | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 40.2 s |
| 22/11/2026 14:21 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Chrome | OneTrust | Refus par les préférences : interrupteurs vérifiés désactivés — — | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 5.1 s |
| 22/11/2026 14:22 | 1.1.0-beta.3 | Strict (refus complet, « ne pas valider dans le doute ») | Chrome | Commanders Act (TrustCommander) | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 1 (autres compteurs non relevés) | non relevé | non relevée | oui | 33.4 s |

| 22/11/2026 15:05 | 1.1.0-beta.3-ci.7 | non relevé | Galaxy Defense | TCF générique | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 6 (autres compteurs non relevés) | non relevé | non relevée | oui | 9.2 s |
| 22/11/2026 15:05 | 1.1.0-beta.3-ci.7 | non relevé | Galaxy Defense | Google UMP (Funding Choices) | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 2.2 s |
| 22/11/2026 15:06 | 1.1.0-beta.3-ci.7 | non relevé | Opera | Didomi | Action interrompue — la fenêtre s'est fermée sans validation | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 1.9 s |
| 22/11/2026 15:06 | 1.1.0-beta.3-ci.7 | non relevé | Opera | Didomi | Action interrompue — la fenêtre s'est fermée sans validation | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 6.1 s |
| 22/11/2026 15:07 | 1.1.0-beta.3-ci.7 | non relevé | Opera | Didomi | Refus par les préférences : interrupteurs vérifiés désactivés — choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 14.5 s |
| 22/11/2026 15:07 | 1.1.0-beta.3-ci.7 | non relevé | Opera | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.9 s |
| 22/11/2026 15:08 | 1.1.0-beta.3-ci.7 | non relevé | Opera | Didomi | Refus par les préférences : interrupteurs vérifiés désactivés — choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 19.7 s |
| 22/11/2026 15:08 | 1.1.0-beta.3-ci.7 | non relevé | Opera | OneTrust | Refus impossible — aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 2.5 s |
| 22/11/2026 15:09 | 1.1.0-beta.3-ci.7 | non relevé | Opera | Didomi | Refus par les préférences : interrupteurs vérifiés désactivés — choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 12.5 s |
| 22/11/2026 15:10 | 1.1.0-beta.3-ci.7 | non relevé | Firefox | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 7.3 s |
| 22/11/2026 15:11 | 1.1.0-beta.3-ci.7 | non relevé | Firefox | Consentmanager | Refus impossible — aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 4.8 s |
| 22/11/2026 15:11 | 1.1.0-beta.3-ci.7 | non relevé | Firefox | Axeptio | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.3 s |
| 22/11/2026 15:12 | 1.1.0-beta.3-ci.7 | non relevé | Firefox | OneTrust | Refus par les préférences : interrupteurs vérifiés désactivés | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.8 s |
| 22/11/2026 15:13 | 1.1.0-beta.3-ci.7 | non relevé | Chrome | OneTrust | Refus impossible — aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 2.4 s |
| 22/11/2026 15:13 | 1.1.0-beta.3-ci.7 | non relevé | Chrome | Didomi | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 6.7 s |
| 22/11/2026 15:15 | 1.1.0-beta.3-ci.7 | non relevé | Opera | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 4.1 s |
| 22/11/2026 15:16 | 1.1.0-beta.3-ci.7 | non relevé | Firefox | inconnue | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.3 s |

| 22/11/2026 16:42 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | OneTrust | Refus par les préférences : interrupteurs vérifiés désactivés | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 7.2 s |
| 22/11/2026 16:43 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | OneTrust | Refus par les préférences : interrupteurs vérifiés désactivés | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.4 s |
| 22/11/2026 16:43 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | Didomi | Refus impossible — plus rien ne bougeait à l'écran depuis 12 s | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 25.5 s |
| 22/11/2026 16:44 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | Didomi | Action interrompue — la fenêtre s'est fermée sans validation | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 12.4 s |
| 22/11/2026 16:44 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | OneTrust | Refus par les préférences : interrupteurs vérifiés désactivés | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 7.0 s |
| 22/11/2026 16:44 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | Didomi | Refus par les préférences : interrupteurs vérifiés désactivés — choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 8.6 s |
| 22/11/2026 16:45 | 1.1.0-beta.3-ci.9 | non relevé | Opera | Didomi | Refus par les préférences : interrupteurs vérifiés désactivés — choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.8 s |
| 22/11/2026 16:45 | 1.1.0-beta.3-ci.9 | non relevé | Opera | Sourcepoint | Refus impossible — mur payant : pas de refus gratuit sur ce site (accepter ou s'abonner) | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.5 s |
| 22/11/2026 16:46 | 1.1.0-beta.3-ci.9 | non relevé | Opera | Sourcepoint | Action interrompue — la fenêtre s'est fermée sans validation | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 5.2 s |
| 22/11/2026 16:47 | 1.1.0-beta.3-ci.9 | non relevé | Opera | Didomi | Refus partiel — plus rien ne bougeait à l'écran depuis 12 s | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 18.7 s |
| 22/11/2026 16:47 | 1.1.0-beta.3-ci.9 | non relevé | Opera | TCF générique | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 2.8 s |
| 22/11/2026 16:47 | 1.1.0-beta.3-ci.9 | non relevé | Firefox | TCF générique | Refusé avec le bouton du site (non vérifiable) — bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | non | 3.8 s |
| 22/11/2026 16:48 | 1.1.0-beta.3-ci.9 | non relevé | Galaxy Defense | TCF générique | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 6 (autres compteurs non relevés) | non relevé | non relevée | oui | 8.4 s |
| 22/11/2026 16:50 | 1.1.0-beta.3-ci.9 | non relevé | Galaxy Defense | Google UMP (Funding Choices) | Capture incomplète — refus non établi — défilement impossible : certains interrupteurs peuvent être restés hors de la capture | désactivés vérifiés : 0 (autres compteurs non relevés) | non relevé | non relevée | oui | 6.2 s |

Les essais du 22/11 sont recopiés depuis l'export JSON de l'historique de l'appli (le site visité n'y figure pas,
volontairement). Entre les essais du 16/11 et du 22/11, d'autres essais en profil Strict ont été faits sur ce téléphone
sans être consignés ici.

### Constats du 22/11 (à partir de l'historique et des diagnostics)

- **Refus simples** (bouton « Tout refuser » du site) : 2 à 6 s par fenêtre, sans problème.
- **Lenteur des longues fenêtres** : la lecture de l'écran et l'analyse prenaient l'essentiel du temps (Galaxy Defense,
  367 interrupteurs : 1,4 s de lecture + 1,35 s d'analyse à chaque étape, 78 s au total). Analyse accélérée ensuite
  (voir le journal des versions), à confirmer par un nouvel essai.
- **Échecs** : « bouton de validation introuvable » (Commanders Act, AppConsent, Didomi dans Opera), « défilement
  impossible » (Commanders Act dans Chrome), choix radio non reconnu (Didomi dans Firefox), aucun réglage reconnu
  (OneTrust dans Opera). Les cas Commanders Act et Didomi/Opera sont corrigés dans la version suivante, à revérifier.

### Second essai du 22/11 (15 h 05 – 15 h 16)

Recopié depuis l'export JSON de l'historique (version de l'APK lue dans les diagnostics ; profil non noté dans
l'export). 8 diagnostics partagés.

- **Refus simples** : 2 à 7 s par fenêtre (Firefox, Opera, Axeptio).
- **Lecture de l'écran** : jusqu'à 11,3 s sur 19,7 s (Didomi dans Opera, 22 lectures de 1 286 éléments au plus) ;
  d'où la lecture rapide de la seule fenêtre de cookies (journal des versions).
- **Échecs** : OneTrust en onglets dans Opera et Chrome (« aucun contrôle de préférence accessible reconnu ») ;
  Didomi dans Chrome (« défilement impossible », choix déjà sur « Refuser ») ; Consentmanager dans Firefox ;
  Didomi dans Opera (deux « fenêtre fermée sans validation ») ; Galaxy Defense (« défilement impossible »).
  Les deux premiers sont corrigés dans la version suivante, à revérifier ; les autres restent à traiter.

### Troisième essai du 22/11 (16 h 42 – 16 h 50), version avec les corrections du soir

- **OneTrust** (Firefox, 3 fenêtres) : refus par les préférences, interrupteurs vérifiés désactivés.
- **Didomi** : 2 réussites vérifiées (Firefox, Opera) ; échecs dans Firefox (« plus rien ne bougeait depuis 12 s »,
  « fenêtre fermée sans validation ») et Opera (« refus partiel », bouton « Enregistrer » grisé et pied de fenêtre
  caché sous la barre d'Opera).
- **Galaxy Defense** : « défilement impossible » (déjà le cas à 15 h 05). Cause trouvée : la mesure du défilement ne
  regardait que les 40 premières lignes de la liste, déjà sorties de l'écran (corrigé dans la version suivante).
- **Suite** : les échecs Didomi (Opera, Firefox) et Sourcepoint (Opera) sont traités dans la version suivante
  (voir le journal des versions), sans les fichiers de diagnostic : à revérifier.

### Galaxy Defense, 22/11 (17 h 20 – 18 h 00) et quatrième essai du 23/11 (14 h 39 – 14 h 45), version ci.10 puis ci.11

Les exports JSON de diagnostic associés à ces essais ont depuis été supprimés pour des raisons de confidentialité. Les constats textuels ci-dessous sont conservés ; les scénarios de replay qui dépendaient de ces fichiers ont été retirés des tests automatisés.

- **Galaxy Defense** : 17 h 20 (ci.10) « défilement impossible », 81 « intérêt légitime » restés activés (corrigé
  par la pull request n° 1). 17 h 58 (ci.11) : « à terminer à la main », liste pas lue jusqu'en bas, 116 s dont
  68 s pour 6 lectures de la fenêtre.
- **Navigateurs (ci.11)** : 7 fenêtres sur 10 refusées avec le bouton du site (« non vérifiable »), 2,6 à 4,4 s
  (Opera, Firefox, dont OneTrust dans Firefox). Échecs : OneTrust dans Opera (14 h 40, « aucun contrôle de
  préférence accessible reconnu » : seulement « Confirmer la sélection » et « Tout autoriser ») ; Commanders Act
  dans Firefox (14 h 43, 5 interrupteurs illisibles, fenêtre laissée à l'utilisateur).
- Captures manuelles de Firefox à 14 h 42 et 14 h 43 : prises après la fermeture de la fenêtre (page seule).

## Ce qui reste à faire

- Refaire les mêmes sites avec la version qui suit le 22/11 et comparer les durées.
- Pour chaque échec restant : refaire l'essai sur téléphone et consigner le résultat textuellement, sans exporter de diagnostic.
- Galaxy Defense : lecture de la fenêtre trop lente ; OneTrust dans Opera ; Commanders Act dans Firefox.
