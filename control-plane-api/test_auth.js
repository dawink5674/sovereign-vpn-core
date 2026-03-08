const http = require('http');
const { spawn } = require('child_process');

const PORT = 8081;
const TEST_KEY = 'test-secret-key';

function makeRequest(method, path, headers = {}) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'localhost',
      port: PORT,
      path,
      method,
      headers
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body: data }));
    });

    req.on('error', error => reject(error));
    req.end();
  });
}

function runServer(env) {
  return new Promise((resolve, reject) => {
    const serverProcess = spawn('node', ['index.js'], {
      cwd: __dirname,
      env: { ...process.env, PORT, ...env },
    });

    serverProcess.stdout.on('data', data => {
      if (data.toString().includes('listening')) {
        resolve(serverProcess);
      }
    });

    serverProcess.stderr.on('data', data => {
      console.error(`Server error: ${data}`);
    });

    serverProcess.on('error', error => {
      reject(error);
    });
  });
}

async function runTests() {
  console.log('--- Running Tests ---');

  // Test 1: No ADMIN_API_KEY set
  console.log('\nStarting server without ADMIN_API_KEY...');
  let serverProcess = await runServer({ ADMIN_API_KEY: '' });

  console.log('Test 1: No ADMIN_API_KEY set (expect 500)');
  let res = await makeRequest('GET', '/api/peers');
  console.log(`Status: ${res.status}, Body: ${res.body}`);
  if (res.status !== 500) {
    serverProcess.kill();
    throw new Error(`Test 1 failed. Expected 500, got ${res.status}`);
  }

  // Kill server and restart with ADMIN_API_KEY
  serverProcess.kill();
  await new Promise(resolve => setTimeout(resolve, 500));

  console.log(`\nStarting server with ADMIN_API_KEY=${TEST_KEY}...`);
  serverProcess = await runServer({ ADMIN_API_KEY: TEST_KEY });

  // Test 2: Missing X-API-Key header
  console.log('Test 2: Missing X-API-Key header (expect 401)');
  res = await makeRequest('GET', '/api/peers');
  console.log(`Status: ${res.status}, Body: ${res.body}`);
  if (res.status !== 401) {
    serverProcess.kill();
    throw new Error(`Test 2 failed. Expected 401, got ${res.status}`);
  }

  // Test 3: Incorrect X-API-Key header
  console.log('\nTest 3: Incorrect X-API-Key header (expect 401)');
  res = await makeRequest('GET', '/api/peers', { 'X-API-Key': 'wrong-key' });
  console.log(`Status: ${res.status}, Body: ${res.body}`);
  if (res.status !== 401) {
    serverProcess.kill();
    throw new Error(`Test 3 failed. Expected 401, got ${res.status}`);
  }

  // Test 4: Correct X-API-Key header
  console.log('\nTest 4: Correct X-API-Key header (expect 200)');
  res = await makeRequest('GET', '/api/peers', { 'X-API-Key': TEST_KEY });
  console.log(`Status: ${res.status}, Body: ${res.body}`);
  if (res.status !== 200) {
    serverProcess.kill();
    throw new Error(`Test 4 failed. Expected 200, got ${res.status}`);
  }

  // Test 5: Unprotected route (/api/health)
  console.log('\nTest 5: Unprotected route /api/health (expect 200)');
  res = await makeRequest('GET', '/api/health');
  console.log(`Status: ${res.status}, Body: ${res.body}`);
  if (res.status !== 200) {
    serverProcess.kill();
    throw new Error(`Test 5 failed. Expected 200, got ${res.status}`);
  }

  console.log('\n✅ All tests passed!');
  serverProcess.kill();
  process.exit(0);
}

runTests().catch(err => {
  console.error(err);
  process.exit(1);
});
