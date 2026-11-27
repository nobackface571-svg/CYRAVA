# CYRAVA — Fiche testeurs

Cette fiche résume, étape par étape, comment installer CYRAVA, l'autoriser et l'essayer. Elle tient en quelques minutes de lecture. Pour tous les détails, voir le [README](../README.md).

> **À savoir avant de commencer**
> - CYRAVA fonctionne sur **Android 8 ou plus récent**. Les étapes « paramètres restreints » ne concernent que **Android 13 et plus**.
> - CYRAVA **n'a pas accès à Internet** et ne lit **que** les applis que vous cochez.
> - Les noms des menus peuvent varier légèrement selon la marque du téléphone (Samsung, Xiaomi, Pixel…).

---

## Étape 0 — Désactiver Google Play Protect (important)

CYRAVA n'est pas sur le Play Store et utilise le service d'accessibilité : **Play Protect risque fort de bloquer l'installation**, ou de la signaler comme dangereuse. Désactivez-le **avant** d'installer :

1. Ouvrez l'appli **Google Play Store**.
2. Touchez votre **photo de profil** (en haut à droite) › **Play Protect**.
3. Touchez la **roue dentée ⚙️** (en haut à droite).
4. Désactivez **« Analyser les applis avec Play Protect »**, puis confirmez.

> Une fois CYRAVA installé et activé (étape 3), vous pouvez réactiver Play Protect au même endroit. S'il affiche ensuite un avertissement sur CYRAVA, choisissez de **conserver** l'appli.

✅ **Contrôle** : la page Play Protect indique que l'analyse est désactivée.

---

## Étape 1 — Télécharger et installer l'APK

1. Depuis le navigateur du téléphone, ouvrez l'onglet **Releases** de ce dépôt GitHub.
2. Téléchargez le fichier **.apk** de la dernière version.
   > N'installez CYRAVA **que** depuis cette page : un APK trouvé ailleurs peut avoir été modifié.
3. Touchez la notification de téléchargement (ou **Mes fichiers › Téléchargements**), puis le fichier `.apk`.
4. Si Android affiche *« … n'est pas autorisé à installer des applis inconnues de cette source »* :
   touchez **Paramètres**, activez **Autoriser depuis cette source**, puis revenez en arrière.
5. Touchez **Installer**.
   - **Play Protect** affiche quand même un avertissement ? → **Plus de détails › Installer quand même**. S'il bloque, revoyez l'étape 0.
   - **Samsung récent**, installation bloquée ? → **Paramètres › Sécurité et confidentialité › Blocage automatique** : désactivez-le le temps de l'installation, puis réactivez-le.

✅ **Contrôle** : l'icône CYRAVA apparaît dans vos applis.

---

## Étape 2 — Autoriser les « paramètres restreints » (Android 13 et plus)

Pour une appli installée hors Play Store, Android **grise** le service d'accessibilité tant que vous ne l'avez pas autorisé. C'est une protection d'Android, pas un bug.

1. Ouvrez CYRAVA. Sur l'**Accueil**, touchez **Ouvrir les réglages d'accessibilité**.
2. Allez dans **Applis installées › CYRAVA – Digital Privacy**.
3. L'interrupteur est grisé ou un message **« Paramètre restreint »** s'affiche : touchez **OK**.
   > ⚠️ Cette tentative est **obligatoire** : le menu de l'étape 5 n'apparaît qu'après elle.
4. Revenez dans CYRAVA et touchez **Ouvrir Infos sur l'appli**
   (ou : **Paramètres › Applications › CYRAVA**).
5. Touchez **⋮** (trois points, en haut à droite) › **Autoriser les paramètres restreints**.
6. Confirmez avec votre code, schéma ou empreinte.

✅ **Contrôle** : l'interrupteur de CYRAVA dans Accessibilité n'est plus grisé.

> **Le menu ⋮ n'affiche pas « Autoriser les paramètres restreints » ?**
> Refaites les étapes 1 à 3 (essayer d'activer le service), puis revenez à l'étape 4.

---

## Étape 3 — Activer le service d'accessibilité

1. **Paramètres › Accessibilité › Applis installées › CYRAVA – Digital Privacy**
   (ou bouton **Ouvrir les réglages d'accessibilité** dans CYRAVA).
2. Activez l'interrupteur.
3. Android affiche un avertissement générique sur le contrôle de l'écran : acceptez-le.
   CYRAVA ne lit que les applis cochées et n'a pas Internet.
4. Revenez dans CYRAVA.

✅ **Contrôle** : l'accueil n'affiche plus **« Protection inactive »**.

À partir de là, **plus besoin d'ouvrir CYRAVA** : il travaille en arrière-plan. Après un redémarrage ou une mise à jour, le service reste activé.

---

## Étape 4 — Empêcher le téléphone d'arrêter CYRAVA

Surtout utile sur **Samsung** (et Xiaomi, Huawei, Oppo…), qui coupent volontiers les services en arrière-plan.

1. Dans CYRAVA, carte **Batterie** de l'accueil : touchez **Ne pas optimiser CYRAVA** et acceptez.
2. Samsung : **Paramètres › Batterie › Limites d'utilisation en arrière-plan › Applis jamais en veille › +** › cochez **CYRAVA**.
3. Facultatif : **Paramètres › Applications › CYRAVA › Batterie › Non restreinte**.

> L'accueil affiche **« Service arrêté »** ? Désactivez puis réactivez le service d'accessibilité (étape 3).

---

## Étape 5 — Choisir les applis et le profil

1. Onglet **Applications** : activez l'interrupteur des applis à surveiller.
   - **Navigateurs conseillés** : **Chrome** ou **Firefox**.
   - **Samsung Internet n'est pas pris en charge** (il ne transmet pas le contenu des pages).
   - Ne cochez **jamais** : banque, gestionnaire de mots de passe, messagerie, jeux avec anti-triche.
2. **Réglages › Profil de refus** : choisissez **Strict** pour les essais.
   > En profil **Observation**, CYRAVA analyse mais ne touche à rien : il n'y a donc rien à tester.

---

## Étape 6 — Faire un essai

1. Dans Chrome, ouvrez un onglet en **navigation privée** (un site qui a déjà reçu votre choix n'affiche plus sa fenêtre).
2. Ouvrez un site qui affiche une fenêtre de cookies (site d'actualité, de recettes, de commerce…).
3. **Ne touchez pas l'écran** pendant une dizaine de secondes.
4. Ouvrez CYRAVA › onglet **Historique** et regardez la ligne du site.

**Après un échec**, attendez **10 minutes** avant de retester la même page : CYRAVA ne réessaie pas la même fenêtre avant ce délai.

> **Bon à savoir**
> - Une fenêtre qui disparaît ne prouve pas que le refus est enregistré. Seule la mention **« désactivé(s) et vérifié(s) »** dans l'historique indique des cases relues désactivées.
> - Pour vérifier vous-même : rouvrez la page, touchez « Cookies » ou « Gérer mes préférences » en bas de page, et regardez l'état des cases.
> - CYRAVA ne supprime pas les cookies déjà déposés : il refuse les nouveaux.

---

## Étape 7 — Donner votre avis

Ouvrez l'onglet **Issues** de ce dépôt GitHub, puis **New issue**, et choisissez :

| Situation | Modèle à choisir |
|---|---|
| CYRAVA a cliqué sur **« Accepter »**, « Autoriser », un achat ou un abonnement | 🚨 **CYRAVA a cliqué sur une acceptation** (bug grave) |
| Fenêtre non reconnue, refus partiel, bouton non trouvé, appli bloquée | 🐞 **Une fenêtre de cookies n'est pas (bien) refusée** |

**À joindre si possible :**

- le **site ou l'appli** concerné, la date et l'heure ;
- le **modèle du téléphone** et la **version d'Android** ;
- la **version de CYRAVA** (nom du fichier `.apk` installé) ;
- une **capture** de la ligne de l'Historique ;
- le **fichier de diagnostic** : **Réglages › Centre de diagnostic › Capturer dans 5 secondes** (fenêtre affichée à l'écran), puis **Partager**.
  > Ce fichier contient le texte affiché à l'écran à ce moment-là, jamais le contenu des champs de saisie ni des mots de passe. Relisez-le avant de le publier.

---

## Désinstaller ou mettre en pause

- **Pause** : **Paramètres › Accessibilité › CYRAVA** › désactivez l'interrupteur. CYRAVA s'arrête immédiatement et ne se réactive jamais tout seul.
- **Mise à jour** : installez le nouvel APK **par-dessus** l'ancien ; réglages et historique sont conservés.
- **Désinstaller** : appui long sur l'icône › **Désinstaller**.

Merci de tester CYRAVA !
