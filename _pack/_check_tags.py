import json,pathlib
ok=True
for p in pathlib.Path("src/main/resources/data/guzhenren/tags/item").glob("*.json"):
    try:
        d=json.loads(p.read_text(encoding="utf-8"))
        print(f"OK {p.name}: {len(d.get('values',[]))} values")
    except Exception as e:
        ok=False; print(f"BAD {p.name}: {e}")
raise SystemExit(0 if ok else 1)
