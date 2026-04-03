const { spawn } = require('child_process');
const http = require('http');

async function run() {
  console.log("Starting test server...");
  const server = spawn('node', ['index.js'], {
    env: { ...process.env, ADMIN_API_KEY: 'test_key', PORT: '8080' },
    cwd: process.cwd()
  });

  await new Promise(resolve => setTimeout(resolve, 1000));

  console.log("Testing without API key...");
  const req1 = http.get('http://localhost:8080/api/peers', (res) => {
    console.log("Status without key:", res.statusCode);
  });

  console.log("Testing with valid API key...");
  const req2 = http.request('http://localhost:8080/api/peers', {
    headers: { 'X-API-Key': 'test_key' }
  }, (res) => {
    console.log("Status with key:", res.statusCode);
  });
  req2.end();

  setTimeout(() => server.kill(), 1000);
}
run();
