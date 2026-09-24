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

# 5) 选型36 款 + 依赖贴图入库，贴图统一压 1024（预算 ~18MB）
python3 - "$SRC" "public/assets/quaternius" <<'EOF'
import json, shutil, sys
SRC, DST = sys.argv[1], sys.argv[2]
models = """CommonTree_1 CommonTree_2 CommonTree_3 CommonTree_4 CommonTree_5
Pine_1 Pine_2 Pine_3 TwistedTree_2 TwistedTree_4 DeadTree_2
Bush_Common Bush_Common_Flowers Plant_1_Big Plant_7_Big Fern_1
Grass_Common_Short Grass_Common_Tall Grass_Wispy_Short Grass_Wispy_Tall
Flower_3_Group Flower_4_Group Flower_3_Single Flower_4_Single
Mushroom_Common Mushroom_Laetiporus
Rock_Medium_1 Rock_Medium_2 Rock_Medium_3
Pebble_Square_1 Pebble_Square_2 Pebble_Square_3 Pebble_Round_1 Pebble_Round_2
Clover_1 Clover_2""".split()
imgs = set()
for m in models:
    shutil.copy(f"{SRC}/{m}.gltf", DST)
    shutil.copy(f"{SRC}/{m}.bin", DST)
    for im in json.load(open(f"{SRC}/{m}.gltf")).get("images", []):
        imgs.add(im["uri"])
for u in imgs:
    shutil.copy(f"{SRC}/{u}", DST)
print(f"quaternius: {len(models)} models, {len(imgs)} textures")
EOF
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
