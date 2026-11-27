#!/usr/bin/env python3
"""Validate the additive CMP catalogue with Python standard library only."""
import json, sys
from pathlib import Path
from urllib.parse import urlparse
ROOT=Path(__file__).resolve().parents[2]
CATALOG=ROOT/"app/src/main/assets/cmp-catalog.json"
def main():
 data=json.loads(CATALOG.read_text(encoding="utf-8"))
 policy=data["policy"]
 assert policy["providerIdentificationAloneCanAuthorizeAction"] is False
 assert policy["unknownState"]=="ABSTAIN"
 assert policy["allUnverifiedMarkersDisabled"] is True
 ids=[p["id"] for p in data["providers"]]
 assert len(ids)==len(set(ids)), "duplicate provider id"
 for p in data["providers"]:
  assert p["name"].strip() and p["docs"]
  assert p.get("productType") and p.get("deploymentScope") and p.get("validationStatus")
  for url in p["docs"]:
   u=urlparse(url); assert u.scheme=="https" and u.netloc, (p["id"],url)
  for value,kind,evidence,matchable in p["markers"]:
   assert value.strip()
   assert kind in {"dom-id-or-view-id","android-or-dom-id","dom-id-or-script"}
   assert matchable is False, (p["id"],value,"unverified marker must stay disabled")
 for section in ("ecosystemComponents","privateImplementations"):
  seen=set()
  for item in data.get(section,[]):
   assert item["id"] not in seen, (section,item["id"])
   seen.add(item["id"])
   u=urlparse(item["source"]); assert u.scheme=="https" and u.netloc
 print(f"OK: {len(ids)} CMP/provider records; {len(data.get('ecosystemComponents',[]))} ecosystem components; {len(data.get('privateImplementations',[]))} private implementations; all markers disabled")
if __name__=="__main__":
 try: main()
 except Exception as e: print("INVALID:",e,file=sys.stderr); raise SystemExit(1)
