#!/usr/bin/env bash
# 演示数据：生成 eats.json 并推入设备（debug 包经 run-as 写应用私有目录）
# 用法：./demo-data.sh [device-serial]
set -euo pipefail

SERIAL="${1:-}"
PKG="com.leo.eats"
DIR="$(cd "$(dirname "$0")" && pwd)"
adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }

NOW=$(date +%s000)
DAY=$((24 * 60 * 60 * 1000))
AGO() { echo $((NOW - $1 * DAY)); }

cat > /tmp/eats-demo.json <<EOF
{
  "schemaVersion": 1,
  "places": [
    {"id":"pl1","name":"巷子深火锅","kind":"RESTAURANT","cuisine":"火锅","location":{"lat":31.2304,"lng":121.4737},"address":"某某路 12 号","rating":4,"tags":["辣","重口味"],"photos":[],"links":[{"url":"https://h5.waimai.meituan.com/awp/h5/block/default/abc","label":"双人套餐"},{"url":"https://m.dianping.com/shoplist/ch10/r111","label":"店铺主页"}],"notes":"排队严重，错峰去","createdAt":$(AGO 60),"updatedAt":$(AGO 3)},
    {"id":"pl2","name":"沙县小吃","kind":"RESTAURANT","cuisine":"快餐","location":{"lat":31.2289,"lng":121.4752},"address":"某南路 88 号","rating":3,"tags":["清淡","便宜"],"photos":[],"links":[],"notes":"蒸饺不错","createdAt":$(AGO 90),"updatedAt":$(AGO 7)},
    {"id":"pl3","name":"鮨 · 日料亭","kind":"RESTAURANT","cuisine":"日料","location":{"lat":31.2331,"lng":121.4701},"address":"某江路 5 号 2F","rating":5,"tags":["日料","清淡"],"photos":[],"links":[{"url":"https://m.dianping.com/shop/987654","label":""}],"notes":"周五有板前位","createdAt":$(AGO 30),"updatedAt":$(AGO 30)},
    {"id":"pl4","name":"猪脚饭","kind":"TAKEOUT","cuisine":"快餐","location":{"lat":31.2296,"lng":121.4768},"address":"","rating":4,"tags":["一人食"],"photos":[],"links":[{"url":"https://i.meituan.com/awp/h5/def/pig","label":"招牌套餐"}],"notes":"外卖 30 分钟必达","createdAt":$(AGO 45),"updatedAt":$(AGO 2)},
    {"id":"pl5","name":"麦当劳","kind":"TAKEOUT","cuisine":"快餐","location":{"lat":31.2318,"lng":121.4699},"address":"某大道 1 号","rating":3,"tags":["一人食","夜宵"],"photos":[],"links":[],"notes":"","createdAt":$(AGO 120),"updatedAt":$(AGO 1)},
    {"id":"pl6","name":"番茄炒蛋","kind":"HOME","cuisine":"家常菜","location":null,"address":"","rating":null,"tags":["清淡"],"photos":[],"links":[],"notes":"10 分钟搞定","createdAt":$(AGO 200),"updatedAt":$(AGO 14)},
    {"id":"pl7","name":"红烧排骨","kind":"HOME","cuisine":"家常菜","location":null,"address":"","rating":4,"tags":[],"photos":[],"links":[],"notes":"","createdAt":$(AGO 150),"updatedAt":$(AGO 21)},
    {"id":"pl8","name":"柳州螺蛳粉","kind":"RESTAURANT","cuisine":"面食","location":{"lat":31.2277,"lng":121.4715},"address":"某城路 66 号","rating":4,"tags":["辣","一人食"],"photos":[],"links":[],"notes":"加双份腐竹","createdAt":$(AGO 40),"updatedAt":$(AGO 10)}
  ],
  "visits": [
    {"id":"v1","placeId":"pl1","at":$(AGO 3),"rating":5,"cost":128.0,"text":"毛肚绝了","photos":[],"createdAt":$(AGO 3)},
    {"id":"v2","placeId":"pl1","at":$(AGO 17),"rating":4,"cost":99.0,"text":"错峰不用排队","photos":[],"createdAt":$(AGO 17)},
    {"id":"v3","placeId":"pl1","at":$(AGO 31),"rating":4,"cost":110.0,"text":"","photos":[],"createdAt":$(AGO 31)},
    {"id":"v4","placeId":"pl5","at":$(AGO 1),"rating":3,"cost":39.0,"text":"1+1 随心配","photos":[],"createdAt":$(AGO 1)},
    {"id":"v5","placeId":"pl5","at":$(AGO 4),"rating":null,"cost":35.0,"text":"","photos":[],"createdAt":$(AGO 4)},
    {"id":"v6","placeId":"pl4","at":$(AGO 2),"rating":4,"cost":26.0,"text":"猪脚软糯","photos":[],"createdAt":$(AGO 2)},
    {"id":"v7","placeId":"pl4","at":$(AGO 9),"rating":4,"cost":26.0,"text":"","photos":[],"createdAt":$(AGO 9)},
    {"id":"v8","placeId":"pl2","at":$(AGO 7),"rating":3,"cost":18.0,"text":"拌面 + 蒸饺","photos":[],"createdAt":$(AGO 7)},
    {"id":"v9","placeId":"pl6","at":$(AGO 14),"rating":null,"cost":null,"text":"凑合一顿","photos":[],"createdAt":$(AGO 14)},
    {"id":"v10","placeId":"pl7","at":$(AGO 21),"rating":4,"cost":null,"text":"电饭煲版也香","photos":[],"createdAt":$(AGO 21)},
    {"id":"v11","placeId":"pl8","at":$(AGO 10),"rating":4,"cost":32.0,"text":"酸笋加倍","photos":[],"createdAt":$(AGO 10)}
  ]
}
EOF

adb push /tmp/eats-demo.json /data/local/tmp/eats-demo.json >/dev/null
adb shell am force-stop "$PKG"
adb shell "run-as $PKG sh -c 'mkdir -p files && cp /data/local/tmp/eats-demo.json files/eats.json'"
adb shell am start -n "$PKG/.MainActivity" >/dev/null
echo "演示数据已写入并重启应用（8 家食堂 / 11 条记录）"
