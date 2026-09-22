#!/bin/bash
# M0 spike：用真实 Key 抓官方用量接口响应，锁定 limits[] 字段结构与百分比语义
# 用法：GLM_API_KEY=你的Key ./tools/spike-usage.sh [bigmodel|zai]
# 注意：响应里若含敏感字段，回填 fixture 时先脱敏
set -euo pipefail

KEY="${GLM_API_KEY:?请先 export GLM_API_KEY=你的Key}"
TARGET="${1:-bigmodel}"

case "$TARGET" in
  bigmodel) BASE="https://open.bigmodel.cn" ;;
  zai) BASE="https://api.z.ai" ;;
  *) echo "目标只能是 bigmodel 或 zai"; exit 1 ;;
esac

echo "== GET $BASE/api/monitor/usage/quota/limit"
curl -sS -m 20 -H "Authorization: $KEY" "$BASE/api/monitor/usage/quota/limit" \
  | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))'
