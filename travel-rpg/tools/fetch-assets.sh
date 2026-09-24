#!/usr/bin/env bash
# 重下/更新 travel-rpg 运行时素材（全部 CC0，可再分发；ADR-002）
# 用法：bash tools/fetch-assets.sh   （在 travel-rpg/ 下执行）
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p public/assets/nature public/textures

# 1) 角色：KayKit Adventurers 骑士（CC0，76 段动画 Idle/Walking_A~C/Running_A~B/Jump_*）
curl -fsSL --retry 3 -o public/assets/Knight.glb \
  "https://cdn.jsdelivr.net/gh/KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0@main/addons/kaykit_character_pack_adventures/Characters/gltf/Knight.glb"

# 2) 高清地面贴图：Poly Haven leafy_grass 2K diffuse（CC0）
curl -fsSL --retry 3 -o public/textures/leafy_grass_diff_2k.jpg \
  "https://dl.polyhaven.org/file/ph-assets/Textures/jpg/2k/leafy_grass/leafy_grass_diff_2k.jpg"

# 3) HDRI 环境光：Poly Haven spruit_sunrise 2K（CC0，日出草原，配黄金时刻布光）
curl -fsSL --retry 3 -o public/textures/spruit_sunrise_2k.hdr \
  "https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/2k/spruit_sunrise_2k.hdr"

# 4) 植被与道具：Kenney Nature Kit（CC0，329 GLB）→ 抽取 52 款子集
TMP_ZIP="$(mktemp -d)/nature.zip"
curl -fsSL --retry 3 -o "$TMP_ZIP" \
  "https://kenney.nl/media/pages/assets/nature-kit/37ac38a37b-1677698939/kenney_nature-kit.zip"
TMP_DIR="$(mktemp -d)"
unzip -q "$TMP_ZIP" -d "$TMP_DIR"
SRC="$TMP_DIR/Models/GLTF format"
for m in \
  tree_oak_fall tree_default_fall tree_simple_fall tree_fat_fall tree_tall_fall \
  tree_blocks_fall tree_pineRoundC tree_pineRoundE tree_blocks tree_default \
  tree_oak tree_pineTallA tree_thin_fall tree_small_fall \
  rock_largeA rock_largeB rock_largeC rock_largeD rock_smallA rock_smallC \
  rock_smallFlatA rock_tallA rock_tallB \
  plant_bush plant_bushLarge plant_bushDetailed plant_bushSmall \
  grass grass_large grass_leafs grass_leafsLarge plant_flatShort plant_flatTall \
  flower_yellowA flower_yellowB flower_yellowC flower_redA flower_redB \
  flower_purpleA flower_purpleB \
  campfire_logs campfire_stones tent_smallOpen tent_detailedClosed \
  log_stack log stump_round sign fence_simple fence_simpleLow fence_bend canoe
do
  cp "$SRC/$m.glb" "public/assets/nature/$m.glb"
done
rm -rf "$TMP_DIR" "$TMP_ZIP"
echo "assets ok: $(ls public/assets/nature | wc -l | tr -d ' ') 模型 + 2K 贴图/HDRI"
