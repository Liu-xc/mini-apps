#!/usr/bin/env python3
"""validate.py — mini-apps 数据包校验器（与 app 导入预检同规则，it-024/it-012）。

用法：
    python3 validate.py <package.zip> --app wardrobe   # 或 eats

退出码：0 = PASS（可有 WARN）；1 = FAIL。仅用 python3 标准库。
"""
import argparse
import json
import sys
import uuid as uuid_mod
import zipfile

WAREHOUSE_CATEGORIES = {"TOP", "OUTERWEAR", "BOTTOM", "DRESS", "SHOES", "BAG", "HAT", "ACCESSORY"}
KINDS = {"RESTAURANT", "TAKEOUT", "HOME"}
CATEGORIES = {"EAT", "DRINK", "PLAY"}

WARDROBE_COLLECTIONS = ["persons", "items", "outfits", "notes", "wearLogs", "wishItems", "wishOutfits"]
EATS_COLLECTIONS = ["places", "visits"]

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
JPG_MAGIC = b"\xff\xd8\xff"
WEBP_MAGIC = b"RIFF"  # + 8..12 == b"WEBP"


class Report:
    def __init__(self):
        self.errors = []
        self.warns = []

    def fail(self, msg):
        self.errors.append(msg)

    def warn(self, msg):
        self.warns.append(msg)

    @property
    def ok(self):
        return not self.errors


def is_uuid(r, where, value):
    if not isinstance(value, str):
        r.fail(f"{where}: id 不是字符串: {value!r}")
        return False
    try:
        parsed = uuid_mod.UUID(value)
    except ValueError:
        r.fail(f"{where}: id 不是合法 UUID: {value}")
        return False
    if str(parsed) != value:
        r.warn(f"{where}: id 非规范小写十六进制: {value}")
    return True


def image_kind(data):
    if data.startswith(PNG_MAGIC):
        return "png"
    if data.startswith(JPG_MAGIC):
        return "jpg"
    if data.startswith(WEBP_MAGIC) and data[8:12] == b"WEBP":
        return "webp"
    return None


def check_tags(r, where, tags):
    if not isinstance(tags, list) or not all(isinstance(t, str) for t in tags):
        r.fail(f"{where}: tags 应为字符串数组")
        return
    if len(tags) > 10:
        r.fail(f"{where}: tags 超过 10 个（{len(tags)}）")
    if len(set(tags)) != len(tags):
        r.fail(f"{where}: tags 有重复")


def check_images(r, zip_names, image_bytes, referenced):
    for name in sorted(referenced):
        if not isinstance(name, str) or not name:
            r.fail(f"图片引用名为空字符串")
            continue
        if name not in zip_names:
            r.fail(f"缺失图片: {name}")
            continue
        data = image_bytes[name]
        if not data:
            r.fail(f"图片内容为空: {name}")
        elif image_kind(data) is None:
            r.fail(f"图片魔数不是 webp/jpg/png: {name}")
    unreferenced = zip_names - referenced
    for name in sorted(unreferenced):
        r.warn(f"包内图片未被引用（不会导入）: {name}")


def check_manifest(r, manifest, app, actual_counts):
    if manifest.get("packageFormat") != 1:
        r.fail(f"manifest.packageFormat 必须为 1，实际 {manifest.get('packageFormat')!r}")
    if manifest.get("app") != app:
        r.fail(f"manifest.app 必须为 {app!r}，实际 {manifest.get('app')!r}")
    if manifest.get("schemaVersion") != 1:
        r.fail(f"manifest.schemaVersion 应为 1，实际 {manifest.get('schemaVersion')!r}")
    if not isinstance(manifest.get("exportedAt"), int):
        r.fail("manifest.exportedAt 应为整数（epoch 毫秒）")
    if not isinstance(manifest.get("generator"), str) or not manifest.get("generator"):
        r.fail("manifest.generator 应为非空字符串")
    counts = manifest.get("counts")
    if isinstance(counts, dict):
        for key, value in counts.items():
            actual = actual_counts.get(key)
            if actual is not None and value != actual:
                r.warn(f"manifest.counts.{key}={value} 与实际 {actual} 不一致")


def load_root(r, zf, data_file, collection_names):
    try:
        raw = zf.read(data_file)
    except KeyError:
        r.fail(f"缺少数据文件 {data_file}")
        return None
    try:
        root = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        r.fail(f"{data_file} 不是合法 JSON: {e}")
        return None
    if root.get("schemaVersion") != 1:
        r.fail(f"{data_file}.schemaVersion 应为 1，实际 {root.get('schemaVersion')!r}")
    out = {}
    for coll in collection_names:
        value = root.get(coll)
        if value is None:
            r.fail(f"{data_file} 缺集合键 {coll}（即使为空也必须显式写 []）")
            out[coll] = []
        elif not isinstance(value, list):
            r.fail(f"{data_file}.{coll} 应为数组")
            out[coll] = []
        else:
            out[coll] = value
    return out


def each_entity(r, coll_name, entities):
    for i, e in enumerate(entities):
        where = f"{coll_name}[{i}]"
        if not isinstance(e, dict):
            r.fail(f"{where} 应为对象")
            continue
        yield where, e


# ---- wardrobe ----

def validate_wardrobe(r, data, zip_names, image_bytes):
    persons, items, outfits = data["persons"], data["items"], data["outfits"]
    notes, wear_logs = data["notes"], data["wearLogs"]
    wish_items, wish_outfits = data["wishItems"], data["wishOutfits"]

    person_ids, item_ids, outfit_ids = set(), set(), set()
    wish_item_ids = set()
    referenced = set()

    for where, e in each_entity(r, "persons", persons):
        for f in ("id", "name"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        if is_uuid(r, where, e.get("id")):
            person_ids.add(e["id"])
        ref = e.get("refImageFile")
        if ref is not None:
            referenced.add(ref)

    for where, e in each_entity(r, "items", items):
        for f in ("id", "personId", "category", "name", "imageFile"):
            if f not in e or e[f] in (None, ""):
                r.fail(f"{where} 缺必填字段 {f}")
        if e.get("category") not in WAREHOUSE_CATEGORIES:
            r.fail(f"{where}.category 非法: {e.get('category')!r}（应为 8 个英文枚举之一）")
        if is_uuid(r, where, e.get("id")):
            item_ids.add(e["id"])
        if "imageFile" in e:
            referenced.add(e["imageFile"])
        check_tags(r, where, e.get("tags", []))

    for where, e in each_entity(r, "outfits", outfits):
        for f in ("id", "personId"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        if is_uuid(r, where, e.get("id")):
            outfit_ids.add(e["id"])
        for j, img in enumerate(e.get("effectImages", [])):
            if not isinstance(img, dict) or "file" not in img:
                r.fail(f"{where}.effectImages[{j}] 缺 file")
            else:
                referenced.add(img["file"])
        check_tags(r, where, e.get("tags", []))

    for where, e in each_entity(r, "notes", notes):
        for f in ("id", "parentType", "parentId", "text"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        if e.get("parentType") not in ("ITEM", "OUTFIT"):
            r.fail(f"{where}.parentType 应为 ITEM|OUTFIT，实际 {e.get('parentType')!r}")
        is_uuid(r, where, e.get("id"))

    for where, e in each_entity(r, "wearLogs", wear_logs):
        for f in ("id", "personId", "outfitId", "at"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        if not isinstance(e.get("at"), int):
            r.fail(f"{where}.at 应为整数时间戳")
        is_uuid(r, where, e.get("id"))

    for where, e in each_entity(r, "wishItems", wish_items):
        for f in ("id", "personId", "category", "name"):
            if f not in e or e[f] in (None, ""):
                r.fail(f"{where} 缺必填字段 {f}")
        if e.get("category") not in WAREHOUSE_CATEGORIES:
            r.fail(f"{where}.category 非法: {e.get('category')!r}")
        if is_uuid(r, where, e.get("id")):
            wish_item_ids.add(e["id"])
        if e.get("imageFile"):
            referenced.add(e["imageFile"])
        check_tags(r, where, e.get("tags", []))

    for where, e in each_entity(r, "wishOutfits", wish_outfits):
        for f in ("id", "personId"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        if not e.get("wishItemIds"):
            r.fail(f"{where}.wishItemIds 至少一件（心愿穿搭不变量）")
        is_uuid(r, where, e.get("id"))
        for j, img in enumerate(e.get("previewImages", [])):
            if not isinstance(img, dict) or "file" not in img:
                r.fail(f"{where}.previewImages[{j}] 缺 file")
            else:
                referenced.add(img["file"])

    # 引用完整性
    for i, e in enumerate(items):
        if e.get("personId") not in person_ids:
            r.fail(f"items[{i}].personId 悬空: {e.get('personId')}")
    for i, e in enumerate(outfits):
        if e.get("personId") not in person_ids:
            r.fail(f"outfits[{i}].personId 悬空: {e.get('personId')}")
        for j, iid in enumerate(e.get("itemIds", [])):
            if iid not in item_ids:
                r.fail(f"outfits[{i}].itemIds[{j}] 悬空: {iid}")
    for i, e in enumerate(notes):
        pool = item_ids if e.get("parentType") == "ITEM" else outfit_ids
        if e.get("parentId") not in pool:
            r.fail(f"notes[{i}].parentId 悬空: {e.get('parentId')}")
    for i, e in enumerate(wear_logs):
        if e.get("personId") not in person_ids:
            r.fail(f"wearLogs[{i}].personId 悬空: {e.get('personId')}")
        if e.get("outfitId") not in outfit_ids:
            r.fail(f"wearLogs[{i}].outfitId 悬空: {e.get('outfitId')}")
    for i, e in enumerate(wish_items):
        if e.get("personId") not in person_ids:
            r.fail(f"wishItems[{i}].personId 悬空: {e.get('personId')}")
        pid = e.get("purchasedItemId")
        if pid is not None and pid not in item_ids:
            r.fail(f"wishItems[{i}].purchasedItemId 悬空: {pid}")
    for i, e in enumerate(wish_outfits):
        if e.get("personId") not in person_ids:
            r.fail(f"wishOutfits[{i}].personId 悬空: {e.get('personId')}")
        for j, iid in enumerate(e.get("itemIds", [])):
            if iid not in item_ids:
                r.fail(f"wishOutfits[{i}].itemIds[{j}] 悬空: {iid}")
        for j, wid in enumerate(e.get("wishItemIds", [])):
            if wid not in wish_item_ids:
                r.fail(f"wishOutfits[{i}].wishItemIds[{j}] 悬空: {wid}")

    # 集合内 id 重复
    for coll, entities in (("persons", persons), ("items", items), ("outfits", outfits),
                           ("notes", notes), ("wearLogs", wear_logs),
                           ("wishItems", wish_items), ("wishOutfits", wish_outfits)):
        ids = [e.get("id") for e in entities if isinstance(e, dict)]
        if len(set(ids)) != len(ids):
            r.fail(f"{coll} 内有重复 id")

    check_images(r, zip_names, image_bytes, referenced)
    return {c: len(data[c]) for c in WARDROBE_COLLECTIONS}


# ---- eats ----

def validate_eats(r, data, zip_names, image_bytes):
    places, visits = data["places"], data["visits"]
    place_ids = set()
    referenced = set()

    for where, e in each_entity(r, "places", places):
        for f in ("id", "name", "kind"):
            if f not in e or e[f] in (None, ""):
                r.fail(f"{where} 缺必填字段 {f}")
        if e.get("kind") not in KINDS:
            r.fail(f"{where}.kind 非法: {e.get('kind')!r}（RESTAURANT|TAKEOUT|HOME）")
        if e.get("category") not in CATEGORIES:
            r.fail(f"{where}.category 非法: {e.get('category')!r}（EAT|DRINK|PLAY）")
        if e.get("category") == "PLAY" and e.get("kind") == "TAKEOUT":
            r.fail(f"{where}（{e.get('name')}）：PLAY+TAKEOUT 无效组合（玩只有出门/在家）")
        rating = e.get("rating")
        if rating is not None and (not isinstance(rating, int) or not 1 <= rating <= 5):
            r.fail(f"{where}.rating 越界: {rating!r}（应 1–5 或不填）")
        loc = e.get("location")
        if loc is not None and not (isinstance(loc, dict) and isinstance(loc.get("lat"), (int, float))
                                    and isinstance(loc.get("lng"), (int, float))):
            r.fail(f"{where}.location 应为 {{lat, lng}} 数值对象")
        urls = []
        for j, link in enumerate(e.get("links", [])):
            if not isinstance(link, dict) or not str(link.get("url", "")).strip():
                r.fail(f"{where}.links[{j}] 缺非空 url")
            else:
                urls.append(link["url"].strip())
        if len(set(urls)) != len(urls):
            r.fail(f"{where}.links 的 url 有重复")
        if is_uuid(r, where, e.get("id")):
            place_ids.add(e["id"])
        for p in e.get("photos", []):
            referenced.add(p)
        check_tags(r, where, e.get("tags", []))

    for where, e in each_entity(r, "visits", visits):
        for f in ("id", "placeId", "at"):
            if f not in e:
                r.fail(f"{where} 缺必填字段 {f}")
        rating = e.get("rating")
        if rating is not None and (not isinstance(rating, int) or not 1 <= rating <= 5):
            r.fail(f"{where}.rating 越界: {rating!r}")
        if not isinstance(e.get("at"), int):
            r.fail(f"{where}.at 应为整数时间戳")
        is_uuid(r, where, e.get("id"))
        for p in e.get("photos", []):
            referenced.add(p)

    for i, e in enumerate(visits):
        if e.get("placeId") not in place_ids:
            r.fail(f"visits[{i}].placeId 悬空: {e.get('placeId')}")

    for coll, entities in (("places", places), ("visits", visits)):
        ids = [e.get("id") for e in entities if isinstance(e, dict)]
        if len(set(ids)) != len(ids):
            r.fail(f"{coll} 内有重复 id")

    check_images(r, zip_names, image_bytes, referenced)
    return {c: len(data[c]) for c in EATS_COLLECTIONS}


def main():
    parser = argparse.ArgumentParser(description="mini-apps 数据包校验器")
    parser.add_argument("package", help="zip 路径")
    parser.add_argument("--app", required=True, choices=["wardrobe", "eats"])
    args = parser.parse_args()

    r = Report()
    data_file = f"{args.app}.json"
    try:
        zf = zipfile.ZipFile(args.package)
    except (zipfile.BadZipFile, OSError) as e:
        print(f"FAIL 无法打开 zip: {e}")
        sys.exit(1)

    with zf:
        names = set(zf.namelist())
        if "manifest.json" not in names:
            print("FAIL 缺 manifest.json（不是 mini-apps 数据包）")
            sys.exit(1)
        try:
            manifest = json.loads(zf.read("manifest.json").decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as e:
            print(f"FAIL manifest.json 不是合法 JSON: {e}")
            sys.exit(1)

        image_entries = {n[len("images/"):] for n in names
                         if n.startswith("images/") and not n.endswith("/")}
        image_bytes = {n: zf.read(f"images/{n}") for n in image_entries}

        data = load_root(r, zf, data_file,
                         WARDROBE_COLLECTIONS if args.app == "wardrobe" else EATS_COLLECTIONS)
        counts = {}
        if data is not None:
            if args.app == "wardrobe":
                counts = validate_wardrobe(r, data, image_entries, image_bytes)
            else:
                counts = validate_eats(r, data, image_entries, image_bytes)
        check_manifest(r, manifest, args.app, counts)

    for msg in r.errors:
        print(f"FAIL {msg}")
    for msg in r.warns:
        print(f"WARN {msg}")
    if r.ok:
        print(f"== PASS ✓ {args.package}（{len(r.warns)} 警告）==")
        sys.exit(0)
    print(f"== FAIL ✗ {args.package}（{len(r.errors)} 错误 / {len(r.warns)} 警告）==")
    sys.exit(1)


if __name__ == "__main__":
    main()
