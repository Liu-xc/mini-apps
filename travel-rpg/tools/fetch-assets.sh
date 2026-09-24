#!/usr/bin/env bash
# 重下/更新 travel-rpg 运行时素材（全部 CC0，可再分发；ADR-002）
# 用法：bash tools/fetch-assets.sh   （在 travel-rpg/ 下执行；需 python3、unzip、sips）
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p public/assets/quaternius public/assets/nature public/textures
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# 1) 角色：KayKit Adventurers 骑士（CC0，76 段动画；jsDelivr 直链）
curl -fsSL --retry 3 -o public/assets/Knight.glb \
  "https://cdn.jsdelivr.net/gh/KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0@main/addons/kaykit_character_pack_adventures/Characters/gltf/Knight.glb"

# 2) PolyHaven PBR 地面三件套（2K，CC0）+ 纯天空 HDRI（2K，真云背景/IBL 同源）
BASE_PH="https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/leafy_grass/leafy_grass"
curl -fsSL --retry 3 -o public/textures/leafy_grass_diff_2k.jpg    "${BASE_PH}_diff_2k.jpg"
curl -fsSL --retry 3 -o public/textures/leafy_grass_nor_gl_2k.jpg  "${BASE_PH}_nor_gl_2k.jpg"
curl -fsSL --retry 3 -o public/textures/leafy_grass_rough_2k.jpg   "${BASE_PH}_rough_2k.jpg"
curl -fsSL --retry 3 -o public/textures/puresky_2k.hdr \
  "https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/kloofendal_48d_partly_cloudy_puresky_2k.hdr"
curl -fsSL --retry 3 -o public/textures/dawn_puresky_2k.hdr \
  "https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/qwantani_dawn_puresky_2k.hdr"
curl -fsSL --retry 3 -o public/textures/sunset_puresky_2k.hdr \
  "https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/belfast_sunset_puresky_2k.hdr"

# 3) three.js 官方 Lensflare 纹理（MIT）
curl -fsSL --retry 3 -o public/textures/lensflare0.png \
  "https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/textures/lensflare/lensflare0.png"
curl -fsSL --retry 3 -o public/textures/lensflare3.png \
  "https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/textures/lensflare/lensflare3.png"

# 4) Quaternius Stylized Nature MegaKit（CC0，itch 5.0 满分包）
#    itch 无静态直链 → 四步 API：csrf → download_url → upload_id → 60s 签名 S3 直链
#    （签名 URL 只签 GET，勿用 HEAD 测；macOS grep 无 -P，用 python3 解析）
ITCH="https://quaternius.itch.io/stylized-nature-megakit"
CSRF=$(curl -sL --max-time 30 "$ITCH/purchase" | python3 -c "import sys,re; m=re.search(r'csrf_token\" value=\"([^\"]+)\"', sys.stdin.read()); print(m.group(1) if m else '')")
TOKURL=$(curl -s --max-time 30 -X POST "$ITCH/download_url" -H "X-Requested-With: XMLHttpRequest" \
  --data-urlencode "csrf_token=$CSRF" | python3 -c "import sys,json; print(json.load(sys.stdin)['url'])")
UP=$(curl -sL --max-time 30 "$TOKURL" | python3 -c "import sys,re; ms=re.findall(r'data-upload_id=\"(\d+)\"', sys.stdin.read()); print(ms[0] if ms else '')")
FURL=$(curl -s --max-time 30 -X POST "$ITCH/file/$UP" -H "X-Requested-With: XMLHttpRequest" \
  --data-urlencode "csrf_token=$CSRF" | python3 -c "import sys,json; print(json.load(sys.stdin)['url'])")
curl -sL --max-time 600 -o "$TMP/megakit.zip" "$FURL"
unzip -q "$TMP/megakit.zip" -d "$TMP/megakit"
SRC="$TMP/megakit/glTF"

# 5) Quaternius 全量 68 款 + 贴图平铺入库（it-003 资产沉淀），贴图压 1024
mkdir -p public/assets/quaternius
cp "$TMP/megakit/glTF/"*.gltf "$TMP/megakit/glTF/"*.bin "$TMP/megakit/glTF/"*.png public/assets/quaternius/
for f in public/assets/quaternius/*.png; do sips -Z 1024 "$f" >/dev/null 2>&1; done

# 6) Kenney Nature Kit → 只取12 款营地道具（植被已由 Quaternius 接管，ADR-002）
curl -fsSL --retry 3 -o "$TMP/kenney.zip" \
  "https://kenney.nl/media/pages/assets/nature-kit/37ac38a37b-1677698939/kenney_nature-kit.zip"
unzip -q "$TMP/kenney.zip" -d "$TMP/kenney"
KSRC="$TMP/kenney/Models/GLTF format"
for m in campfire_logs campfire_stones tent_smallOpen tent_detailedClosed \
         log_stack log stump_round sign fence_simple fence_simpleLow fence_bend canoe; do
  cp "$KSRC/$m.glb" "public/assets/nature/$m.glb"
done

echo "assets ok: quaternius $(ls public/assets/quaternius | wc -l | tr -d ' ') 文件 / nature $(ls public/assets/nature | wc -l | tr -d ' ') 道具 / textures $(ls public/textures | wc -l | tr -d ' ')"

# 7) Poly Haven 草原模型精选（API 权威清单，预算 18MB，it-003）
python3 - <<'PYEOF'
import json, subprocess, os
CANDS = ["grass_medium_01", "grass_bermuda_01", "boulder_01", "rock_07",
         "fern_02", "shrub_01", "dandelion_01", "tree_stump_01", "moss_01"]
DST_ROOT, BUDGET = "public/assets/polyhaven", 18 * 1024 * 1024
def curl(url, out):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    return subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "90",
                           "-o", out, url], capture_output=True).returncode == 0
total = 0
for mid in CANDS:
    api = subprocess.run(["curl", "-fsSL", "--max-time", "25",
                          f"https://api.polyhaven.com/files/{mid}"], capture_output=True, text=True)
    if api.returncode: continue
    try: d = json.loads(api.stdout)["gltf"]["1k"]["gltf"]
    except Exception: continue
    files, need = {"gltf": d["url"]}, d["size"]
    for name, meta in d.get("include", {}).items():
        files[name] = meta["url"]; need += meta["size"]
    if total + need > BUDGET: continue
    dstdir = f"{DST_ROOT}/{mid}"
    ok = curl(files.pop("gltf"), f"{dstdir}/{mid}_1k.gltf")
    for rel, url in files.items() if ok else []:
        ok = curl(url, f"{dstdir}/{rel}")
    if ok: total += need
    else: subprocess.run(["rm", "-rf", dstdir])
print(f"polyhaven: {total//1024}KB")
PYEOF

# 8) 重新生成资产目录
python3 tools/gen_catalog.py
echo "assets ok: quaternius $(ls public/assets/quaternius | wc -l | tr -d ' ') / nature $(ls public/assets/nature | wc -l | tr -d ' ') / polyhaven $(find public/assets/polyhaven -name '*.gltf' | wc -l | tr -d ' ') 模型 / textures $(ls public/textures | wc -l | tr -d ' ')"