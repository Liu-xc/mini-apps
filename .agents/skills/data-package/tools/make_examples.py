#!/usr/bin/env python3
"""make_examples.py — 重新生成 examples/ 下的两个最小合法样例包（schema 变更后跑它刷新）。"""
import base64
import json
import os
import zipfile

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # skill 根目录
EXAMPLES = os.path.join(HERE, "examples")

# 1x1 像素图（两种格式，验证放宽项：非 webp 亦合法）
PNG_1PX = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
)
JPG_1PX = base64.b64decode(
    "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
    "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA"
    "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q=="
)

P1 = "11111111-1111-4111-8111-111111111111"
I1 = "22222222-2222-4222-8222-222222222221"
I2 = "22222222-2222-4222-8222-222222222222"
O1 = "33333333-3333-4333-8333-333333333331"
W1 = "44444444-4444-4444-8444-444444444441"
N1 = "55555555-5555-4555-8555-555555555551"
WI1 = "66666666-6666-4666-8666-666666666661"
WO1 = "77777777-7777-4777-8777-777777777771"
PL1 = "88888888-8888-4888-8888-888888888881"
PL2 = "88888888-8888-4888-8888-888888888882"
V1 = "99999999-9999-4999-8999-999999999991"
V2 = "99999999-9999-4999-8999-999999999992"

WARDROBE_DATA = {
    "schemaVersion": 1,
    "persons": [{"id": P1, "name": "示例角色", "emoji": "🙂", "refImageFile": None, "createdAt": 1758900000000}],
    "items": [
        {"id": I1, "personId": P1, "category": "TOP", "name": "白色牛津纺衬衫",
         "color": "白色", "desc": "宽松棉质、纽扣领", "imageFile": "shirt.png",
         "tags": ["通勤", "简约"], "createdAt": 1758900000000, "updatedAt": 1758900000000},
        {"id": I2, "personId": P1, "category": "BOTTOM", "name": "藏蓝直筒牛仔裤",
         "color": "藏蓝", "desc": "中腰微弹", "imageFile": "pants.png",
         "tags": ["休闲"], "createdAt": 1758900000000, "updatedAt": 1758900000000},
    ],
    "outfits": [
        {"id": O1, "personId": P1, "itemIds": [I1, I2], "tags": ["通勤", "早秋"],
         "effectImages": [], "createdAt": 1758900000000, "updatedAt": 1758900000000},
    ],
    "notes": [{"id": N1, "parentType": "ITEM", "parentId": I1, "text": "洗后微缩水", "createdAt": 1758900000000}],
    "wearLogs": [{"id": W1, "personId": P1, "outfitId": O1, "at": 1758900000000, "createdAt": 1758900000000}],
    "wishItems": [
        {"id": WI1, "personId": P1, "category": "BAG", "name": "棕色植鞣革托特包",
         "color": "棕色", "desc": "大容量可装电脑", "price": 899.0,
         "url": "https://example.com/bag", "imageFile": None, "tags": ["通勤"],
         "purchasedAt": None, "purchasedItemId": None,
         "createdAt": 1758900000000, "updatedAt": 1758900000000},
    ],
    "wishOutfits": [
        {"id": WO1, "personId": P1, "itemIds": [I1], "wishItemIds": [WI1],
         "tags": ["通勤"], "previewImages": [], "createdAt": 1758900000000, "updatedAt": 1758900000000},
    ],
}

EATS_DATA = {
    "schemaVersion": 1,
    "places": [
        {"id": PL1, "name": "巷子深火锅", "kind": "RESTAURANT", "cuisine": "火锅",
         "location": {"lat": 31.22, "lng": 121.44}, "address": "示例路 12 号",
         "rating": 4, "tags": ["辣", "重油"], "photos": ["door.jpg"],
         "links": [{"url": "https://h5.dianping.com/example", "label": "双人套餐"}],
         "notes": "排队严重，错峰去", "category": "EAT",
         "wishlistedAt": None, "planAt": None,
         "createdAt": 1758900000000, "updatedAt": 1758900000000},
        {"id": PL2, "name": "番茄牛腩面", "kind": "HOME", "cuisine": "面食",
         "location": None, "address": "", "rating": None, "tags": ["家常菜"],
         "photos": [], "links": [], "notes": "自己做，牛腩炖一小时",
         "category": "EAT", "wishlistedAt": None, "planAt": None,
         "createdAt": 1758900000000, "updatedAt": 1758900000000},
    ],
    "visits": [
        {"id": V1, "placeId": PL1, "at": 1758903600000, "rating": 5, "cost": 128.0,
         "text": "毛肚绝了", "photos": [], "createdAt": 1758903600000},
        {"id": V2, "placeId": PL2, "at": 1758986400000, "rating": 4, "cost": 22.0,
         "text": "家常但费时", "photos": [], "createdAt": 1758986400000},
    ],
}


def counts(data, collections):
    return {c: len(data[c]) for c in collections}


def write_zip(path, app, data, images):
    os.makedirs(EXAMPLES, exist_ok=True)
    manifest = {
        "packageFormat": 1,
        "app": app,
        "schemaVersion": 1,
        "exportedAt": 1758950000000,
        "generator": "agent: sample",
        "counts": counts(data, list(data.keys())[1:]),
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        z.writestr(f"{app}.json", json.dumps(data, ensure_ascii=False, indent=2))
        for name, blob in images.items():
            z.writestr(f"images/{name}", blob)
    print(f"wrote {path}")


if __name__ == "__main__":
    write_zip(os.path.join(EXAMPLES, "wardrobe-sample.zip"), "wardrobe",
              WARDROBE_DATA, {"shirt.png": PNG_1PX, "pants.png": PNG_1PX})
    write_zip(os.path.join(EXAMPLES, "eats-sample.zip"), "eats",
              EATS_DATA, {"door.jpg": JPG_1PX})
