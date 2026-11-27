import json, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
DATA=json.loads((ROOT/"app/src/main/assets/cmp-catalog.json").read_text(encoding="utf-8"))
class CatalogSafetyTests(unittest.TestCase):
 def test_unique_provider_ids(self):
  ids=[p["id"] for p in DATA["providers"]]; self.assertEqual(len(ids),len(set(ids)))
 def test_catalog_never_authorizes_actions(self):
  self.assertFalse(DATA["policy"]["providerIdentificationAloneCanAuthorizeAction"])
 def test_every_marker_is_disabled_until_observed(self):
  for p in DATA["providers"]:
   for value,kind,evidence,matchable in p["markers"]:
    self.assertFalse(matchable,p["id"]+":"+value)
 def test_every_provider_has_classification_and_sources(self):
  for p in DATA["providers"]:
   self.assertTrue(p["productType"],p["id"])
   self.assertTrue(p["deploymentScope"],p["id"])
   self.assertTrue(p["docs"],p["id"])
 def test_private_implementations_not_counted_as_vendors(self):
  for p in DATA.get("privateImplementations",[]):
   self.assertEqual(p["type"],"publisher-private-implementation")
 def test_components_not_miscounted_as_full_cmp(self):
  for p in DATA.get("ecosystemComponents",[]):
   self.assertTrue(p["notNecessarilyStandaloneCmp"])
if __name__=="__main__": unittest.main()
