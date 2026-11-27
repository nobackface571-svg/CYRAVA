🇬🇧 [English](CONTRIBUTING.md) | 🇫🇷 **Français**

# Contribuer à CYRAVA

Merci de lire d'abord [SECURITY.fr.md](SECURITY.fr.md) : les règles de sécurité passent avant le taux de réussite. Une modification qui permettrait, même rarement, de cliquer sur une acceptation n'est pas acceptable.

## Règles d'or

1. **Jamais d'acceptation.** Toute nouvelle formule cliquable doit passer le garde-fou. En cas de doute, ne pas cliquer.
2. **Aucun réseau.** Pas de permission `INTERNET`, pas de bibliothèque qui en aurait besoin, pas de statistiques d'usage.
3. **Honnêteté des résultats.** Seule une observation prouve un refus (`OPTIONS_SUCCESS` : interrupteurs relus désactivés). Un clic suivi d'une fermeture donne `UNVERIFIED` ; un doute donne `PARTIAL` ou `IMPOSSIBLE`.
4. **Pas de texte d'écran** en dehors des fichiers de diagnostic (ni dans l'historique, ni dans les journaux système).
5. **Chaque décision se teste.** La logique vit dans `core/` et arrive avec ses tests.

## Ajouter une formule ou une plateforme

1. Éditez `app/src/main/assets/rules.json`, dans la bonne liste : `hints` (indices de reconnaissance), `buttons.refuse`, `buttons.options`, `buttons.globalReject`, `buttons.confirm`, `buttons.vendorTabs`, `buttons.back`, `buttons.accept` (jamais cliqué : sert seulement à reconnaître la fenêtre), ou `platforms`.
2. Les textes sont comparés sans accents ni ponctuation, en minuscules : écrivez-les normalement.
3. Une formule de refus qui contient un mot d'acceptation (« Continuer sans accepter ») devient une exception au garde-fou : elle doit être une négation explicite, et un test doit la couvrir.
4. Augmentez `version` dans `rules.json`.
5. Lancez `./gradlew test` : les tests vérifient que chaque acceptation est bloquée, que chaque refus est autorisé, et que les règles livrées restent importables.

Si vous partez d'un fichier de diagnostic, ajoutez de préférence un scénario dans `CmpScenariosTest` qui reproduit la structure de la fenêtre.

## Modifier le moteur

- Écrivez d'abord le test (dans `core/src/test`), avec un arbre construit à la main.
- Les noms de tests contenant des classes locales doivent rester en caractères simples (sans « » ni accents).
- Vérifiez les scénarios du laboratoire (`LabAndValidatorTest`) : ils ne doivent jamais accepter.

## Modifier l'interface

- Textes simples, sans jargon, en français (`res/values`) et en anglais (`res/values-en`).
- Si un texte visible change, mettez à jour `.github/scripts/e2e.sh`, qui retrouve les éléments par leur texte.
- Pas de chiffres ni de statuts qui ne viennent pas de données réelles.

## Vérifier avant d'envoyer

```sh
./gradlew test :app:lintRelease :app:assembleRelease
```

La compilation de l'appli demande le SDK Android (téléchargé depuis `dl.google.com`). Sans lui, `./gradlew :core:test` reste possible. La compilation et les tests complets passent par CodeMagic, lancés à la main par le mainteneur.

## Messages de commit

En français, à l'impératif ou sous forme de titre court avec un préfixe : `core :`, `app :`, `ci :`, `docs :`, `test(e2e) :`. Une phrase de contexte dans le corps si le « pourquoi » n'est pas évident.

## Publier une version

Les versions sont publiées par le propriétaire du dépôt, avec sa clé de signature personnelle : voir le [README, section 11](README.fr.md#11-signature-de-lapk-et-développement). Mettez à jour `CHANGELOG.md` avant. `bash scripts/verifier-workflows.sh` et `bash scripts/verifier-signature.sh` doivent rester verts.
