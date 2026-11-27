🇬🇧 [English](IMPORT_REGLES.md) | 🇫🇷 **Français**

# Import des règles de projets libres (20/11/2026)

CYRAVA ne lit que l'écran (service d'accessibilité) : il ne peut pas exécuter de JavaScript ni de
sélecteurs CSS comme une extension de navigateur. La seule information qu'il partage avec ces
extensions est l'**identifiant HTML** d'un bouton, que Chrome transmet comme identifiant de vue.

`scripts/cmp-library/import_rules.py` extrait donc des règles de deux projets libres les boutons
désignés par un identifiant simple, et les ajoute à `app/src/main/assets/rules.json` > `buttonIds`.

| Source | Licence | Version lue | Ce qui est repris |
| --- | --- | --- | --- |
| [DuckDuckGo Autoconsent](https://github.com/duckduckgo/autoconsent) | MPL-2.0 | `d6cf4b9` | clics des étapes de refus (`optOut`) |
| [Consent-O-Matic](https://github.com/cavi-au/Consent-O-Matic) (université d'Aarhus) | MIT | `8ca8500` | clics « ouvrir les préférences » et « enregistrer » |

Seuls des **identifiants** (des noms techniques) sont repris, avec leur rôle : aucun code. La
provenance est rappelée dans `rules.json` (`_buttonIds`).

## Résultat
- 1 245 règles Autoconsent lues : 133 désignent des boutons par identifiant ; le reste dépend du
  code de la page et reste hors de portée de CYRAVA.
- Importés : 50 refus, 42 ouvertures de préférences, 44 validations (en plus des identifiants
  vérifiés à la main : Didomi, OneTrust, Cookiebot, Axeptio).

## Filtres de sécurité
1. Écarté : tout identifiant qui évoque une acceptation (accept, agree, allow…), sauf « disagree ».
2. Écarté : tout identifiant **utilisé ailleurs pour accepter** (étapes `optIn` d'Autoconsent,
   actions `trueAction` de Consent-O-Matic). Ex. tarteaucitron : `tarteaucitronPersonalize2` =
   accepter, `tarteaucitronPersonalize` = ouvrir les préférences ; la comparaison est exacte.
3. Écarté : cases à cocher et interrupteurs (checkbox, toggle, switch…), fermer / masquer,
   identifiants trop génériques, identifiant au rôle double.
4. Pour Consent-O-Matic, le nom de l'identifiant doit lui-même confirmer son rôle (« manage »,
   « save »…).
5. Dans l'appli : un texte affiché d'acceptation l'emporte toujours sur l'identifiant ; le
   garde-fou s'applique au clic ; seuls les boutons d'une fenêtre de consentement reconnue sont
   lus ; un fichier de règles importé ne peut ni ajouter ni retirer d'identifiants.

Tests : `core/src/test/kotlin/fr/refusbot/core/ButtonIdsSafetyTest.kt`.

## Mettre à jour
```
git clone --depth 1 https://github.com/duckduckgo/autoconsent /tmp/autoconsent
git clone --depth 1 https://github.com/cavi-au/Consent-O-Matic /tmp/com
python3 scripts/cmp-library/import_rules.py /tmp/autoconsent /tmp/com
```
Puis relancer les tests du moteur.
