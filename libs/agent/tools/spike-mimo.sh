#!/bin/bash
# M0 spike（MiMo/小米）：真调 chat/completions，锁定确切 baseUrl、SSE 流式格式、tool_calls 与 reasoning_content 契约
# 用法：MIMO_API_KEY=你的Key MIMO_BASE_URL=https://… ./tools/spike-mimo.sh
#   - BASE_URL 以开放平台（mimo.mi.com）控制台文档为准，形如 https://xxx/v1（脚本自动补 /chat/completions）
#   - 可选 MIMO_MODEL=mimo-v2.6-flash 跳过模型探测；NO_PROXY=1 走直连
# 注意：响应若含敏感字段，回填 spec 前先脱敏
set -euo pipefail

KEY="${MIMO_API_KEY:?请先 export MIMO_API_KEY=你的Key}"
BASE="${MIMO_BASE_URL:?请 export MIMO_BASE_URL=开放平台文档给的 baseUrl（含 /v1）}"
CURL="curl -sS -m 40"
[ "${NO_PROXY:-0}" = "1" ] && CURL="$CURL --noproxy *"

pretty() { python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))' 2>/dev/null || cat; }

echo "== 0. 探测可用模型 =="
MODELS=(${MIMO_MODEL:-mimo-v2.6-flash mimo-v2.6-pro mimo-v2.5-pro})
MODEL=""
for m in "${MODELS[@]}"; do
  echo "-- 尝试 $m"
  RESP=$($CURL "$BASE/chat/completions" \
    -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
    -d "{\"model\":\"$m\",\"messages\":[{\"role\":\"user\",\"content\":\"只回复两个字：你好\"}],\"max_tokens\":16}")
  if echo "$RESP" | grep -q '"content"'; then
    MODEL="$m"; echo "✓ $m 可用"; echo "$RESP" | pretty | head -20; break
  else
    echo "✗ $m 不可用："; echo "$RESP" | head -5
  fi
done
[ -n "$MODEL" ] || { echo "所有候选模型都不可用，请到控制台核对模型名/baseUrl 后重试"; exit 1; }

echo
echo "== 1. SSE 流式（前 30 行原始 event-stream，重点看 reasoning_content 增量）=="
$CURL -N "$BASE/chat/completions" \
  -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"stream\":true,\"max_tokens\":64,\"messages\":[{\"role\":\"user\",\"content\":\"9.11 和 9.9 谁大？简答\"}]}" \
  | head -30

echo
echo "== 2. 工具调用（非流式）：看 tool_calls 结构与 finish_reason =="
TOOL_RESP=$($CURL "$BASE/chat/completions" \
  -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
  -d "{
    \"model\":\"$MODEL\",
    \"messages\":[{\"role\":\"user\",\"content\":\"今天第一食堂有什么菜？请调用工具查询\"}],
    \"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"get_canteen_menus\",
      \"description\":\"查询指定食堂今日菜单\",
      \"parameters\":{\"type\":\"object\",\"properties\":{\"canteen\":{\"type\":\"string\",\"description\":\"食堂名\"}},\"required\":[\"canteen\"]}}}]
  }")
echo "$TOOL_RESP" | pretty

echo
echo "== 3. 工具结果回喂：role=tool + tool_call_id 是否被接受 =="
CALL_ID=$(echo "$TOOL_RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["choices"][0]["message"]["tool_calls"][0]["id"])' 2>/dev/null || echo "")
FN_ARGS=$(echo "$TOOL_RESP" | python3 -c 'import json,sys; print(json.load(sys.stdin)["choices"][0]["message"]["tool_calls"][0]["function"]["arguments"])' 2>/dev/null || echo "{}")
if [ -n "$CALL_ID" ]; then
  $CURL "$BASE/chat/completions" \
    -H "Authorization: Bearer $KEY" -H "Content-Type: application/json" \
    -d "{
      \"model\":\"$MODEL\",
      \"messages\":[
        {\"role\":\"user\",\"content\":\"今天第一食堂有什么菜？请调用工具查询\"},
        {\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"$CALL_ID\",\"type\":\"function\",\"function\":{\"name\":\"get_canteen_menus\",\"arguments\":$FN_ARGS}}]},
        {\"role\":\"tool\",\"tool_call_id\":\"$CALL_ID\",\"content\":\"红烧肉、清炒时蔬、番茄炒蛋\"}
      ],
      \"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"get_canteen_menus\",
        \"description\":\"查询指定食堂今日菜单\",
        \"parameters\":{\"type\":\"object\",\"properties\":{\"canteen\":{\"type\":\"string\",\"description\":\"食堂名\"}},\"required\":[\"canteen\"]}}}]
    }" | pretty | head -30
else
  echo "（上一步未产生 tool_calls，跳过回喂测试）"
fi
