#!/usr/bin/env python3
"""扫描 public/assets 与 public/textures 生成 catalog.json（it-003 AC-2）。
fetch-assets.sh 重下素材后重跑本脚本。"""
import json, os, re, glob

ROOT = os.path.join(os.path.dirname(__file__), "..", "public")
OUT = os.path.join(ROOT, "assets", "catalog.json")

SRC = {
    "quaternius": dict(source="Quaternius", page="https://quaternius.itch.io/stylized-nature-megakit", license="CC0"),
    "kenney": dict(source="Kenney", page="https://kenney.nl/assets/nature-kit", license="CC0"),
    "kaykit": dict(source="KayKit", page="https://kaylousberg.itch.io/kaykit-adventurers", license="CC0"),
    "polyhaven": dict(source="Poly Haven", page="https://polyhaven.com/models", license="CC0"),
}
HDR = dict(source="Poly Haven", page="https://polyhaven.com/hdri", license="CC0")
LF = dict(source="three.js", page="https://threejs.org", license="MIT")

def category(name: str) -> str:
    n = name.lower()
    if re.search(r"tree|pine|twisted", n): return "tree"
    if re.search(r"rock|pebble|boulder|stone", n): return "rock"
    if re.search(r"flower|dandelion|petal", n): return "flower"
    if re.search(r"mushroom", n): return "mushroom"
    if re.search(r"grass|clover|fern|plant|bush|shrub|moss", n): return "foliage"
    if re.search(r"rockpath|path", n): return "prop"
    if re.search(r"knight", n): return "character"
    return "prop"

def main():
    entries = []

    # Quaternius：gltf + bin 同名成对
    for f in sorted(glob.glob(f"{ROOT}/assets/quaternius/*.gltf")):
        name = os.path.basename(f)[:-5]
        entries.append(dict(id=f"quaternius:{name}", name=name,
                            category=category(name), file=f"assets/quaternius/{name}.gltf",
                            **SRC["quaternius"], tags=[]))
    # Kenney：仅营地道具
    for f in sorted(glob.glob(f"{ROOT}/assets/nature/*.glb")):
        name = os.path.basename(f)[:-4]
        entries.append(dict(id=f"kenney:{name}", name=name, category="prop",
                            file=f"assets/nature/{name}.glb", **SRC["kenney"], tags=["camp"]))
    # Poly Haven 模型
    for d in sorted(glob.glob(f"{ROOT}/assets/polyhaven/*")):
        mid = os.path.basename(d)
        gltf = f"{d}/{mid}_1k.gltf"
        if os.path.exists(gltf):
            entries.append(dict(id=f"polyhaven:{mid}", name=mid,
                                category=category(mid),
                                file=f"assets/polyhaven/{mid}/{mid}_1k.gltf",
                                **SRC["polyhaven"], tags=["photoreal"]))
    # 角色
    if os.path.exists(f"{ROOT}/assets/Knight.glb"):
        entries.append(dict(id="kaykit:Knight", name="Knight", category="character",
                            file="assets/Knight.glb", **SRC["kaykit"], tags=["animated"]))
    # 贴图与 HDRI
    for f in sorted(glob.glob(f"{ROOT}/textures/*.jpg")):
        name = os.path.basename(f)[:-4]
        entries.append(dict(id=f"texture:{name}", name=name, category="texture",
                            file=f"textures/{f.split('/')[-1]}", **HDR, tags=["pbr"]))
    for f in sorted(glob.glob(f"{ROOT}/textures/*.hdr")):
        name = os.path.basename(f)[:-4]
        entries.append(dict(id=f"hdri:{name}", name=name, category="hdri",
                            file=f"textures/{os.path.basename(f)}", **HDR,
                            tags=["sunset"] if "sunset" in name else (["dawn"] if "dawn" in name else ["day"])))
    for n in ("lensflare0", "lensflare3"):
        if os.path.exists(f"{ROOT}/textures/{n}.png"):
            entries.append(dict(id=f"three:{n}", name=n, category="prop",
                                file=f"textures/{n}.png", **LF, tags=["lensflare"]))

    with open(OUT, "w") as fp:
        json.dump(dict(version=1, count=len(entries), entries=entries), fp,
                  ensure_ascii=False, indent=1)
    cats = {}
    for e in entries:
        cats[e["category"]] = cats.get(e["category"], 0) + 1
    print(f"catalog: {len(entries)} entries -> {cats}")

if __name__ == "__main__":
    main()
