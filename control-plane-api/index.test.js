const request = require('supertest');
const { app, peers } = require('./index.js');
const crypto = require('crypto');

// Standard jest mock to bypass actual WireGuard server SSH execution
jest.mock('ssh2', () => ({
  Client: jest.fn().mockImplementation(() => {
    let connectCallback, readyCallback, errorCallback, execCallback;
    return {
      on: jest.fn((event, cb) => {
        if (event === 'ready') readyCallback = cb;
        if (event === 'error') errorCallback = cb;
      }),
      connect: jest.fn((opts) => {
        if (readyCallback) readyCallback();
      }),
      exec: jest.fn((cmd, cb) => {
        const stream = {
          on: jest.fn((event, dataCb) => {
            if (event === 'data') dataCb('mock output');
            if (event === 'close') dataCb(0);
          }),
          stderr: {
            on: jest.fn(),
          },
          stdin: {
            write: jest.fn(),
            end: jest.fn(),
          }
        };
        cb(null, stream);
      }),
      end: jest.fn(),
    };
  })
}));

describe('Control Plane API', () => {
  const validKey = 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=';
  const validApiKey = 'super-secret-admin-key';

  beforeEach(() => {
    // Clear out peers map before each test
    peers.clear();
    // Use a dummy SSH key to bypass the "WG_SSH_KEY not configured" check
    process.env.WG_SSH_KEY = 'ZHVtbXlrZXk='; // "dummykey" in base64
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('Unauthenticated routes', () => {
    it('GET /api/health should return 200 without authentication', async () => {
      const response = await request(app).get('/api/health');
      expect(response.statusCode).toBe(200);
      expect(response.body.status).toBe('ok');
    });
  });

  describe('Authenticated routes with missing ADMIN_API_KEY (fail-closed)', () => {
    beforeAll(() => {
      process.env.ADMIN_API_KEY = '';
    });

    it('GET /api/peers should return 500 when ADMIN_API_KEY is not set', async () => {
      const response = await request(app).get('/api/peers');
      expect(response.statusCode).toBe(500);
      expect(response.body.error).toMatch(/ADMIN_API_KEY not configured/);
    });
  });

  describe('Authenticated routes with ADMIN_API_KEY configured', () => {
    beforeAll(() => {
      process.env.ADMIN_API_KEY = validApiKey;
    });

    it('GET /api/peers should return 401 if X-API-Key is missing', async () => {
      const response = await request(app).get('/api/peers');
      expect(response.statusCode).toBe(401);
      expect(response.body.error).toBe('Unauthorized');
    });

    it('GET /api/peers should return 401 if X-API-Key is incorrect', async () => {
      const response = await request(app).get('/api/peers').set('X-API-Key', 'wrong-key');
      expect(response.statusCode).toBe(401);
      expect(response.body.error).toBe('Unauthorized');
    });

    it('GET /api/peers should return 200 and list peers with correct X-API-Key', async () => {
      // Pre-seed a peer for testing
      peers.set(validKey, {
        name: 'Test Device',
        publicKey: validKey,
        assignedIP: '10.66.66.2/32',
        createdAt: new Date().toISOString()
      });

      const response = await request(app).get('/api/peers').set('X-API-Key', validApiKey);
      expect(response.statusCode).toBe(200);
      expect(response.body.count).toBe(1);
      expect(response.body.peers[0].name).toBe('Test Device');
    });

    it('POST /api/peers should return 401 if X-API-Key is missing', async () => {
      const response = await request(app).post('/api/peers').send({
        name: 'New Device',
        publicKey: validKey
      });
      expect(response.statusCode).toBe(401);
    });

    it('POST /api/peers should successfully provision peer with correct X-API-Key', async () => {
      const newKey = crypto.randomBytes(32).toString('base64');
      const response = await request(app)
        .post('/api/peers')
        .set('X-API-Key', validApiKey)
        .send({
          name: 'New Valid Device',
          publicKey: newKey
        });
      expect(response.statusCode).toBe(201);
      expect(response.body.message).toMatch(/registered/);
      expect(peers.has(newKey)).toBe(true);
    });

    it('DELETE /api/peers/:publicKey should return 401 if X-API-Key is missing', async () => {
      const encodedKey = encodeURIComponent(validKey);
      const response = await request(app).delete(`/api/peers/${encodedKey}`);
      expect(response.statusCode).toBe(401);
    });

    it('DELETE /api/peers/:publicKey should successfully revoke peer with correct X-API-Key', async () => {
      peers.set(validKey, {
        name: 'Device To Delete',
        publicKey: validKey,
        assignedIP: '10.66.66.2/32',
        createdAt: new Date().toISOString()
      });

      const encodedKey = encodeURIComponent(validKey);
      const response = await request(app)
        .delete(`/api/peers/${encodedKey}`)
        .set('X-API-Key', validApiKey);

      expect(response.statusCode).toBe(200);
      expect(response.body.message).toMatch(/revoked/);
      expect(peers.has(validKey)).toBe(false);
    });
  });
});
