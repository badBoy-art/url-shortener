#!/usr/bin/env bash
# 简单压测：优先使用 hey，其次 ab，最后退化到 curl 循环
# 用法: ./scripts/bench.sh [目标地址，默认 http://localhost:8080]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
CONCURRENCY="${2:-100}"
REQUESTS="${3:-10000}"

# 先创建一个短链用于重定向压测
CODE=$(curl -s -X POST "$BASE/api/url" \
  -H 'Content-Type: application/json' \
  -d '{"destUrl":"https://www.example.com/benchmark"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["shortCode"])')
echo "压测短码: $CODE"

run_create_bench() {
  if command -v hey >/dev/null 2>&1; then
    hey -n "$REQUESTS" -c "$CONCURRENCY" -m POST -H 'Content-Type: application/json' \
      -d '{"destUrl":"https://www.example.com/benchmark"}' "$BASE/api/url"
  elif command -v ab >/dev/null 2>&1; then
    ab -n "$REQUESTS" -c "$CONCURRENCY" -p /dev/null "$BASE/api/url"
  else
    echo "未找到 hey/ab，跳过创建接口压测（可用 brew install hey）"
  fi
}

run_redirect_bench() {
  if command -v hey >/dev/null 2>&1; then
    hey -n "$REQUESTS" -c "$CONCURRENCY" "$BASE/$CODE"
  elif command -v ab >/dev/null 2>&1; then
    ab -n "$REQUESTS" -c "$CONCURRENCY" "$BASE/$CODE"
  else
    echo "未找到 hey/ab，跳过重定向压测"
  fi
}

echo "=== 创建接口 POST /api/url ==="
run_create_bench
echo "=== 重定向接口 GET /$CODE ==="
run_redirect_bench
