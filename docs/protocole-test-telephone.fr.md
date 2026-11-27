🇬🇧 [English](protocole-test-telephone.md) | 🇫🇷 **Français**

# Protocole d'essai sur téléphone réel

Ce protocole sert à tester CYRAVA sur un vrai téléphone, avec de vraies fenêtres de cookies. Il distingue ce qu'on peut **constater** de ce qu'on ne peut **pas vérifier**. Toutes les étapes se font depuis le téléphone.

## 0. Ce que ces essais peuvent prouver, et ce qu'ils ne peuvent pas

| On peut constater | On ne peut pas vérifier depuis le téléphone |
|---|---|
| CYRAVA reconnaît la fenêtre (Historique : plateforme, confiance). | Que le site a **enregistré** le refus côté serveur. |
| Ce qu'il a cliqué : boutons de navigation, interrupteurs. | Que des cookies **déjà déposés** ont été supprimés : refuser n'efface pas les cookies existants. |
| Si chaque interrupteur a été **relu désactivé** après le clic (« désactivé(s) et vérifié(s) »). | L'état d'une case dont la page ne publie pas l'état (« d'état illisible, non touché(s) »). |
| Qu'aucune acceptation n'a été cliquée (aucun choix de votre part n'est passé à « accepté »). | Le comportement d'une autre version de Chrome ou d'Android. |

Un clic n'est jamais une preuve. Seule la ligne « désactivé(s) et vérifié(s) » de l'historique indique un changement d'état relu.

## 1. Préparation (une fois)

1. Installer la dernière version : testeurs, depuis l'onglet **Releases** du dépôt GitHub ; mainteneur, l'APK de test de la dernière compilation **CodeMagic** (section **Artifacts**, fichier `app-release.apk`). Noter la version (nom du fichier ou Réglages de l'appli). Pour l'installation et les autorisations (Play Protect, paramètres restreints, accessibilité), suivre la [fiche testeurs](FICHE-TESTEURS.fr.md).
2. Onglet **Applications** :
   - ne cocher **que** Chrome (et éventuellement une ou deux applis avec fenêtre de cookies connue) ;
   - décocher les applis sensibles (banque, mots de passe, authentification, administration, messagerie) et les jeux avec anti-triche.
3. **Réglages › Profil de refus › Strict.** En profil « Observation », CYRAVA ne clique jamais : rien ne peut être testé.
4. **Accueil › Ne pas optimiser CYRAVA**, puis Samsung : *Batterie › Limites d'utilisation en arrière-plan › Applis jamais en veille*.
5. **Réglages › Diagnostic automatique : activé** pendant les essais seulement. Le désactiver à la fin.
6. **Historique › Effacer**, pour repartir d'un historique vide.

## 2. Pour chaque site testé

Dans Chrome, **en navigation privée** : un site qui a déjà reçu votre choix n'affiche plus sa fenêtre.

1. Ouvrir la page et attendre 10 secondes sans toucher l'écran.
2. Faire une **capture d'écran avant** l'action, si la fenêtre a eu le temps d'apparaître, et une **après**.
3. Ouvrir CYRAVA › **Historique** et faire une capture de la ligne du site.
4. Si le résultat n'est pas « interrupteurs vérifiés désactivés » : Réglages › Centre de diagnostic › **Partager** le dernier fichier.
5. **Contre-vérification manuelle** : rouvrir la page, ouvrir le lien « Cookies » ou « Gérer mes préférences » en bas de page, et faire une capture de l'état des cases. C'est le seul moyen, depuis le téléphone, de voir l'état retenu par le site.

**Après un échec, attendre 10 minutes avant de refaire la même page :** CYRAVA ne réessaie pas la même fenêtre avant ce délai.

## 3. Sites représentatifs à couvrir

Choisir au moins un site par ligne. Ce sont des exemples à vérifier : les sites changent de prestataire sans prévenir.

| Prestataire | Ce qui est testé | Exemple observé le 17/11 |
|---|---|---|
| OneTrust | « Tout refuser » au premier niveau, interrupteurs par catégorie | recettes.lidl.fr |
| Didomi | « Continuer sans accepter », options, interrupteurs | à relever dans l'historique (11 événements Didomi) |
| Axeptio | Fenêtre animée, défilement | à relever dans l'historique (2 événements Axeptio) |
| Google (Funding Choices / UMP) | Options et « intérêt légitime » | sites d'actualité utilisant Google |
| Fenêtre sans refus possible | Aucun clic attendu | tout site « payer ou accepter » |

## 4. Fiche de résultat (une ligne par site)

| Champ | Où le lire |
|---|---|
| Site, date, heure | — |
| Version de l'APK | nom du fichier installé |
| Résultat CYRAVA | Historique, première ligne |
| Interrupteurs : détectés / activés au départ / déjà désactivés / désactivés et vérifiés / restés activés / illisibles | Historique, ligne de compte rendu |
| État constaté manuellement (étape 2.5) | capture |
| Concordance CYRAVA ↔ constat manuel | oui / non / impossible à dire |
| Fichier de diagnostic partagé | oui / non |

Un essai est **réussi** seulement si : le résultat est « interrupteurs vérifiés désactivés » (ou « refus cliqué, non vérifiable » pour un simple « Tout refuser »), et le constat manuel montre les cases désactivées. Toute autre combinaison est à transmettre telle quelle, sans interprétation. Recopier chaque fiche dans [`essais-reels.md`](essais-reels.md).

## 5. Ce qu'il ne faut pas conclure

- « La fenêtre a disparu », donc c'est refusé : faux, la fermeture ne prouve pas l'enregistrement.
- « CYRAVA a fait N actions », donc N cases ont été désactivées : faux. Les « clics de navigation » comptent les boutons et les défilements, pas les cases.
- « Les cookies ont été supprimés » : CYRAVA ne supprime aucun cookie, il refuse seulement les nouveaux consentements.
