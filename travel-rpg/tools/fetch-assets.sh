#!/usr/bin/env bash
# 重下/更新 travel-rpg 运行时素材（全部 CC0，可再分发；ADR-002）
# 用法：bash tools/fetch-assets.sh   （在 travel-rpg/ 下执行）
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p public/assets/nature public/textures

# 1) 角色：KayKit Adventurers 骑士（CC0，76 段动画 Idle/Walking_A~C/Running_A~B/Jump_*）
curl -fsSL --retry 3 -o public/assets/Knight.glb \
  "https://cdn.jsdelivr.net/gh/KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0@main/addons/kaykit_character_pack_adventures/Characters/gltf/Knight.glb"

# 2) 地面贴图：Poly Haven leafy_grass（CC0，1K diffuse）
curl -fsSL --retry 3 -o public/textures/leafy_grass_diff_1k.jpg \
  "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/leafy_grass/leafy_grass_diff_1k.jpg"

# 3) 植被：Kenney Nature Kit（CC0，329 GLB）→ 只取子集
TMP_ZIP="$(mktemp -d)/nature.zip"
curl -fsSL --retry 3 -o "$TMP_ZIP" \
  "https://kenney.nl/media/pages/assets/nature-kit/37ac38a37b-1677698939/kenney_nature-kit.zip"
TMP_DIR="$(mktemp -d)"
unzip -q "$TMP_ZIP" -d "$TMP_DIR"
SRC="$TMP_DIR/Models/GLTF format"
for m in tree_oak_fall tree_default_fall tree_simple_fall tree_fat_fall tree_tall_fall \
         tree_pineRoundC tree_pineRoundE tree_blocks_fall \
         rock_largeA rock_largeC rock_smallA rock_smallFlatA rock_tallA \
         plant_bush plant_bushLarge grass grass_large \
         flower_yellowA flower_redA flower_purpleA; do
  cp "$SRC/$m.glb" "public/assets/nature/$m.glb"
done
rm -rf "$TMP_DIR" "$TMP_ZIP"
echo "assets ok:"; ls public/assets public/assets/nature public/textures | head -40
