#!/usr/bin/env bash
# 演示数据：生成 wardrobe.json + 17 件"衣物"（thiings.co 服饰素材作品类照片）并推入设备
# 用法：./demo-data.sh [device-serial]
set -euo pipefail

SERIAL="${1:-}"
PKG="com.leo.wardrobe"
DIR="$(cd "$(dirname "$0")" && pwd)"
adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }

TMP=$(mktemp -d)
mkdir -p "$TMP/images"

python3 - "$TMP" <<'PY'
import json, subprocess, sys, time, os
tmp = sys.argv[1]
# (品类, thiings blobId, 名称, 颜色, 描述, 标签)
ITEMS = [
    ("TOP", "ScKZwuDUTUPRm5ljOa5KSKzyJB3BNB", "白T恤", "白色", "纯棉圆领", ["通勤", "简约"]),
    ("TOP", "TyCXC2E7A9NTewf9im9kE67V4Mv7Rl", "球衣", "藏青", "足球训练款", ["运动"]),
    ("TOP", "jNFNutQXA079LwSjmfwJeFsid37vkh", "牛津纺衬衫", "白蓝条纹", "纽扣领", ["通勤", "早秋"]),
    ("OUTERWEAR", "GGEzZp2bzlrsQLZ4zt9vfa6dvCCDP0", "羽绒服", "黑色", "蓬松短款", ["冬", "通勤"]),
    ("OUTERWEAR", "nrcvXJr22VXWX9PBtXNZ5UrplwAfhj", "飞行夹克", "军绿", "Bomber", ["休闲", "早秋"]),
    ("BOTTOM", "DULdzV3uEdmGmIzORCmUox0QGw8W1z", "直筒牛仔裤", "靛蓝", "微弹", ["休闲"]),
    ("BOTTOM", "fo8wyXqFVEaRo4JUQTTR1vEzfBT0OK", "工装裤", "卡其", "多口袋", ["休闲", "运动"]),
    ("DRESS", "sjnsZFCzT6VnSOqmWOg9YE53KR68pi", "连衣裙", "奶油", "无袖红领", ["约会"]),
    ("DRESS", "OndHuVEwtwfTcGKfNBaBNFawZVaKLh", "背带裙", "丹宁", "背带款", ["度假"]),
    ("SHOES", "llBJrXGTGW8fvXFYtSynrQ6nWHbhKo", "小白鞋", "白色", "轻便运动", ["通勤", "运动"]),
    ("SHOES", "rwia1MUv5NLXZz5qQFGKX7nT2gKxwA", "高帮帆布鞋", "黑色", "高帮", ["休闲"]),
    ("BAG", "OAYK6HWr7j9IxNH5mk3vkntjQCJnwc", "手提包", "棕色", "通勤大容量", ["通勤", "简约"]),
    ("BAG", "3JqmFhoX4wlSJ2F8hEg2ljSfxraUPO", "双肩包", "黑色", "轻量", ["运动", "通勤"]),
    ("HAT", "R2D8r79JBRWYLJGB18GgsUjN2s4Dzb", "牛仔帽", "棕色", "宽檐", ["度假"]),
    ("HAT", "xOofjqlFRH8A5h5dmCd1XPd7wNbLi7", "渔夫帽", "米色", "软檐", ["休闲", "度假"]),
    ("ACCESSORY", "5SFKX0yeQoj1PbNM2ObWNi902QoUeO", "太阳镜", "黑框", "UV400", ["度假", "简约"]),
    ("ACCESSORY", "IzpAiEPUhmyIp69cv7LNwmlPKqrltx", "珍珠项链", "白", "短款", ["约会"]),
]
now = int(time.time() * 1000)
day = 86400000
items = []
for idx, (cat, bid, name, color, desc, tags) in enumerate(ITEMS):
    png = f"{tmp}/{bid}.png"
    subprocess.run(["curl", "-s", "-m", "30",
                    f"https://lftz25oez4aqbxpq.public.blob.vercel-storage.com/image-{bid}.png",
                    "-o", png], check=True)
    webp = f"{tmp}/images/demo_{bid}.webp"
    subprocess.run(["cwebp", "-quiet", "-q", "82", png, "-o", webp], check=True)
    items.append({
        "id": f"it{idx+1}", "personId": "p1", "category": cat,
        "name": name, "color": color, "desc": desc,
        "imageFile": f"demo_{bid}.webp", "tags": tags,
        "createdAt": now - (60 - idx) * day, "updatedAt": now - (idx + 1) * day,
    })
outfits = [
    {"id": "o1", "itemIds": ["it1", "it4", "it6", "it10", "it12"], "tags": ["通勤", "早秋"]},
    {"id": "o2", "itemIds": ["it2", "it7", "it11"], "tags": ["运动"]},
    {"id": "o3", "itemIds": ["it8", "it10", "it15"], "tags": ["约会", "度假"]},
    {"id": "o4", "itemIds": ["it3", "it5", "it6", "it10", "it16"], "tags": ["通勤"]},
    {"id": "o5", "itemIds": ["it1", "it7", "it13", "it14"], "tags": ["休闲", "度假"]},
]
for k, o in enumerate(outfits):
    o["personId"] = "p1"
    o["effectImages"] = []
    o["createdAt"] = now - (30 - k * 5) * day
    o["updatedAt"] = o["createdAt"]
data = {
    "schemaVersion": 1,
    "persons": [{"id": "p1", "name": "Leo", "emoji": "👨", "createdAt": now - 60 * day}],
    "items": items, "outfits": outfits, "notes": [],
}
json.dump(data, open(f"{tmp}/wardrobe.json", "w"), ensure_ascii=False, indent=1)
print(f"生成 {len(items)} 件衣物 / {len(outfits)} 套穿搭")
PY

adb push "$TMP/wardrobe.json" /data/local/tmp/wardrobe-demo.json >/dev/null
adb shell "run-as $PKG sh -c 'mkdir -p files/images files/export && cp /data/local/tmp/wardrobe-demo.json files/wardrobe.json'"
for f in "$TMP"/images/*.webp; do
  name=$(basename "$f")
  adb push "$f" /data/local/tmp/wimg >/dev/null
  adb shell "run-as $PKG sh -c 'cp /data/local/tmp/wimg files/images/$name'"
done
adb shell rm -f /data/local/tmp/wimg /data/local/tmp/wardrobe-demo.json
adb shell am force-stop "$PKG"
rm -rf "$TMP"
echo "演示数据已写入（1 角色 / 17 件衣物，thiings 服饰素材）"
