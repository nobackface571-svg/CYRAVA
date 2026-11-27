# Checklist de validation sur Samsung réel

Cette checklist est à remplir avec le téléphone physique de l'utilisateur. Les résultats d'émulateur
ne remplacent pas ces essais. Ne pas activer de tests automatiques sur des applications non choisies.

## Préparation

- [ ] Noter modèle Samsung, version Android/One UI, version CYRAVA et version Chrome.
- [ ] Installer uniquement l'APK de test dont le commit et le SHA-256 sont connus.
- [ ] Vérifier les permissions et activer le service d'accessibilité volontairement.
- [ ] Sélectionner explicitement une application/site de test.
- [ ] Activer le profil Observation au premier passage.
- [ ] Préparer une page de consentement de test connue et noter son URL.
- [ ] Pour UMP physique, récupérer le `TEST-DEVICE-HASHED-ID` dans les logs et le fournir via
  `UMP_DEMO_TEST_DEVICE_HASHED_ID`, jamais dans le dépôt.

## Vérifications de sécurité

- [ ] Un bouton « Tout accepter », « Accepter » ou équivalent n'est jamais actionné.
- [ ] Un libellé contenant une négation ou une phrase explicative n'est pas pris pour un bouton Refuser.
- [ ] Un contrôle dont l'état est inconnu n'est jamais actionné.
- [ ] Un état « Off » ambigu n'est pas considéré comme une preuve OFF.
- [ ] Après action, l'état est relu ; l'acceptation Android seule n'est pas comptée comme réussite.
- [ ] Une fenêtre qui disparaît après le clic n'est pas comptée comme un refus vérifié.
- [ ] Les champs de saisie et les sous-arbres sensibles ne sont ni lus ni exportés.

## Navigation et fenêtres

- [ ] Bannière au premier plan dans Chrome.
- [ ] Passage de la bannière vers les préférences.
- [ ] Sections repliées puis ouvertes.
- [ ] Liste de fournisseurs longue avec défilement.
- [ ] Retour en haut/bas et événements de défilement.
- [ ] Fenêtre système, dialogue ou clavier superposé : aucune action hors de la fenêtre cible.
- [ ] Rotation ou changement de taille de fenêtre si le modèle le permet.
- [ ] Passage d'une application à une autre : aucun paquet non surveillé n'est inspecté.

## UMP réel

- [ ] Le message RGPD a été publié dans AdMob pour l'ID d'application utilisé.
- [ ] Le formulaire UMP s'affiche réellement sur le téléphone.
- [ ] Le diagnostic identifie les contrôles exposés par Android.
- [ ] Chaque état de préférence reste inconnu si Android ne publie pas une valeur fiable.
- [ ] Les actions, si elles sont autorisées, sont relues et vérifiées individuellement.
- [ ] Le formulaire d'options de confidentialité est disponible si le SDK le déclare requis.
- [ ] Le résultat final ne s'appuie pas uniquement sur `consentStatus`, `canRequestAds` ou la fermeture.

## Historique, diagnostics et stabilité

- [ ] Historique : distinguer vérifié, partiel, non vérifiable, impossible, interrompu et observé.
- [ ] Les nombres d'actions tentées/acceptées/bloquées correspondent aux journaux.
- [ ] CSV et JSON correspondent à l'historique visible.
- [ ] Les diagnostics indiquent si la capture est tronquée.
- [ ] Aucune donnée saisie ou sensible dans les diagnostics exportés.
- [ ] Historique conservé après fermeture/redémarrage du processus.
- [ ] Arrêt d'urgence : aucune action supplémentaire après activation.
- [ ] Service arrêté par Android : l'utilisateur est averti clairement.
- [ ] Vérifier consommation batterie/chaleur et absence de boucle d'événements après défilement.

## Résultat de session

- Date/heure :
- Modèle / Android / One UI :
- Chrome :
- Version APK / commit :
- Application et URL testées :
- Formulaire UMP AdMob utilisé :
- Captures / diagnostics :
- Réussites vérifiées :
- Résultats partiels / inconnus :
- Échecs reproductibles :
- Aucun consentement accepté : oui/non
- Notes :
