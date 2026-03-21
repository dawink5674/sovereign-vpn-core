#!/bin/bash

# Test without ADMIN_API_KEY
echo "Starting server without ADMIN_API_KEY..."
cd control-plane-api
PORT=8081 node index.js &
SERVER_PID=$!
sleep 1

echo "Testing GET /api/peers without ADMIN_API_KEY..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/peers)
if [ "$RESPONSE" -eq 500 ]; then
  echo "✅ Pass: Server returns 500 when ADMIN_API_KEY is not configured"
else
  echo "❌ Fail: Expected 500, got $RESPONSE"
  kill $SERVER_PID
  exit 1
fi

kill $SERVER_PID
wait $SERVER_PID 2>/dev/null

echo "Starting server with ADMIN_API_KEY..."
PORT=8082 ADMIN_API_KEY="test_secret_key" node index.js &
SERVER_PID2=$!
sleep 1

echo "Testing GET /api/health (should be unprotected)..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/api/health)
if [ "$RESPONSE" -eq 200 ]; then
  echo "✅ Pass: /api/health returns 200"
else
  echo "❌ Fail: Expected 200, got $RESPONSE"
  kill $SERVER_PID2
  exit 1
fi

echo "Testing GET /api/peers without X-API-Key..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/api/peers)
if [ "$RESPONSE" -eq 401 ]; then
  echo "✅ Pass: /api/peers without key returns 401"
else
  echo "❌ Fail: Expected 401, got $RESPONSE"
  kill $SERVER_PID2
  exit 1
fi

echo "Testing GET /api/peers with correct X-API-Key..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: test_secret_key" http://localhost:8082/api/peers)
if [ "$RESPONSE" -eq 200 ]; then
  echo "✅ Pass: /api/peers with correct key returns 200"
else
  echo "❌ Fail: Expected 200, got $RESPONSE"
  kill $SERVER_PID2
  exit 1
fi

echo "Testing POST /api/peers with incorrect X-API-Key..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" -H "X-API-Key: wrong_key" -X POST http://localhost:8082/api/peers)
if [ "$RESPONSE" -eq 401 ]; then
  echo "✅ Pass: /api/peers with incorrect key returns 401"
else
  echo "❌ Fail: Expected 401, got $RESPONSE"
  kill $SERVER_PID2
  exit 1
fi

kill $SERVER_PID2
wait $SERVER_PID2 2>/dev/null

echo "All tests passed successfully!"
