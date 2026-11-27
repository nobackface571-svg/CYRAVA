# Test E2E avec le vrai Google UMP SDK

## Objectif

Le scénario `scenario=ump` de l'application `fr.refusbot.demo` utilise le SDK Android officiel
`com.google.android.ump:user-messaging-platform:4.0.0`. Il ne charge aucune page HTML qui imite
Google UMP. Les scénarios `bandeau`, `accepte` et `inspection` restent des fixtures CMP génériques.

## Obtenir le formulaire dans AdMob

1. Se connecter à [Google AdMob](https://admob.google.com/).
2. Dans **Applications**, ajouter/enregistrer une application Android de démonstration. L'application
   peut être distincte de CYRAVA/RefusBot ; utiliser le package de démonstration
   `fr.refusbot.demo` si AdMob demande un nom de package.
3. Ouvrir **Confidentialité et messages** dans la barre latérale.
4. Dans la fiche des messages liés aux réglementations européennes, choisir **Créer** ou **Gérer**,
   puis créer un message pour les utilisateurs de l'EEE/Royaume-Uni/Suisse selon les choix proposés.
5. Configurer les langues et les options de consentement, associer le message à l'application Android
   créée, puis **publier** le message. Un brouillon non publié ne suffit pas.
6. Dans **Applications** → l'application → **Paramètres de l'application**, copier l'**ID d'application
   AdMob** au format `ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`. Ne pas confondre avec un ID de bloc
   publicitaire au format avec `/`.

Le SDK récupère le message publié correspondant à l'ID d'application présent dans le manifeste.
L'identifiant seul ne crée pas le message et ne garantit pas qu'un formulaire soit disponible.

## Configurer le dépôt et le build

Dans GitHub → Settings → Secrets and variables → Actions → Variables, créer :

- `UMP_DEMO_ADMOB_APP_ID` : l'ID d'application AdMob de la démo (variable publique, pas un secret).

Pour un build local, la même valeur peut être fournie via
`UMP_DEMO_ADMOB_APP_ID` ou `-PumpDemoAdMobAppId=...`.

Le build utilise sinon l'ID d'exemple public Google
`ca-app-pub-3940256099942544~3347511713`. Il permet d'avoir une configuration d'exemple, mais
ne garantit pas qu'un message personnalisé soit publié pour cette application. Un résultat
`formAvailable=false` n'est ni un refus ni un succès.

## Tester sur le téléphone Android physique

Google indique que les émulateurs sont automatiquement considérés comme appareils de test depuis
UMP 2.2.0. Pour le téléphone physique :

1. Lancer une fois la démo avec UMP et lire le journal Android filtré sur `GoogleUMP` et les logs
   du SDK UMP.
2. Repérer le message du SDK qui indique le `TEST-DEVICE-HASHED-ID` à enregistrer.
3. Dans GitHub → Settings → Secrets and variables → Actions → Variables, ajouter
   `UMP_DEMO_TEST_DEVICE_HASHED_ID` avec cette valeur. Pour un build local, utiliser la variable
   d'environnement homonyme ou `-PumpDemoTestDeviceHashedId=...`.
4. Relancer la démo. Le code applique la géographie EEE de débogage uniquement dans l'application
   de test.

Les paramètres de géographie forcée ne fonctionnent que sur des appareils reconnus comme appareils
de test. Ne jamais ajouter un identifiant d'appareil réel dans le dépôt.

## Cycle SDK implémenté

À chaque lancement du scénario UMP de cette application de démonstration :

- réinitialisation de l'état UMP, uniquement parce qu'il s'agit d'une fixture E2E ;
- activation de la géographie EEE de débogage ;
- appel à `requestConsentInfoUpdate()` ;
- appel à `loadAndShowConsentFormIfRequired()` après mise à jour ;
- bouton d'options de confidentialité affiché uniquement si le SDK déclare ce point d'entrée requis ;
- journalisation des événements techniques sous le tag Android `GoogleUMP`.

La réinitialisation UMP est strictement limitée à la démo. Elle ne doit pas être copiée dans
l'application CYRAVA/RefusBot de production. La production doit actualiser les informations à chaque
lancement et exposer les options de confidentialité lorsque le SDK les déclare requises.

## Limites de preuve et sécurité

Le statut global UMP (`OBTAINED`, `REQUIRED`, etc.) et `canRequestAds()` ne prouvent pas que les
finalités facultatives ont été refusées. Le SDK ne fournit pas à une application tierce un relevé
public détaillé de chaque interrupteur du formulaire. RefusBot doit distinguer une action tentée,
une action acceptée par Android et un état relu puis vérifié. Un formulaire affiché ou fermé ne doit
jamais être compté à lui seul comme un refus réussi.

Le test E2E enregistre la disponibilité, les erreurs et la fin du cycle UMP. Si le formulaire est
indisponible ou si son état individuel ne peut pas être vérifié, le résultat reste **non vérifié**.
Il ne doit jamais être transformé en succès artificiel.

Sources officielles :
- https://developers.google.com/admob/android/privacy?hl=fr
- https://support.google.com/admob/answer/10107561?hl=fr
- https://developers.google.com/interactive-media-ads/docs/sdks/android/client-side/privacy/release-notes
