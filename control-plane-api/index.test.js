const request = require('supertest');
const { app, peers } = require('./index');
const crypto = require('crypto');

// Mock ssh2 completely
jest.mock('ssh2', () => {
  return {
    Client: jest.fn().mockImplementation(() => ({
      on: jest.fn(function(event, cb) {
        if (event === 'ready') {
          setTimeout(cb, 0);
        }
        return this;
      }),
      connect: jest.fn(),
      exec: jest.fn(function(cmd, cb) {
        // Return a dummy stream
        const stream = {
          stdin: {
            write: jest.fn(),
            end: jest.fn()
          },
          stderr: {
            on: jest.fn()
          },
          on: jest.fn(function(event, dataCb) {
            if (event === 'data') {
              dataCb(Buffer.from('ok'));
            } else if (event === 'close') {
              setTimeout(() => dataCb(0), 0);
            }
          })
        };
        setTimeout(() => cb(null, stream), 0);
      }),
      end: jest.fn()
    }))
  };
});

// Since the console logs noise about SSH failing, let's mock console.error
describe('POST /api/peers', () => {
  const validKey = crypto.randomBytes(32).toString('base64');
  let originalConsoleError;
  let originalConsoleLog;

  beforeAll(() => {
    originalConsoleError = console.error;
    originalConsoleLog = console.log;
    console.error = jest.fn();
    console.log = jest.fn();
  });

  afterAll(() => {
    console.error = originalConsoleError;
    console.log = originalConsoleLog;
  });

  beforeEach(() => {
    peers.clear();
    jest.clearAllMocks();
  });

  it('should return 400 if peer name is missing', async () => {
    const res = await request(app)
      .post('/api/peers')
      .send({ publicKey: validKey });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('Peer name is required');
  });

  it('should return 400 if public key is missing', async () => {
    const res = await request(app)
      .post('/api/peers')
      .send({ name: 'test-peer' });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('Client public key (base64) is required');
  });

  it('should return 400 if public key is invalid length', async () => {
    const res = await request(app)
      .post('/api/peers')
      .send({ name: 'test-peer', publicKey: 'shortkey' });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('Invalid public key: must be 32 bytes (Curve25519)');
  });

  it('should register a new peer successfully', async () => {
    const res = await request(app)
      .post('/api/peers')
      .send({ name: 'test-peer', publicKey: validKey });

    expect(res.status).toBe(201);
    expect(res.body.message).toBe('Peer "test-peer" registered');
    expect(res.body.peer.name).toBe('test-peer');
    expect(res.body.peer.assignedIP).toMatch(/^10\.66\.66\.\d+\/32$/);

    // Check if peer was added to the map
    expect(peers.has(validKey)).toBe(true);
    const storedPeer = peers.get(validKey);
    expect(storedPeer.name).toBe('test-peer');
    expect(storedPeer.publicKey).toBe(validKey);
  });

  it('should return 409 if peer already exists', async () => {
    // Register first
    await request(app)
      .post('/api/peers')
      .send({ name: 'first-peer', publicKey: validKey });

    // Try to register again with same key
    const res = await request(app)
      .post('/api/peers')
      .send({ name: 'duplicate-peer', publicKey: validKey });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe('Peer with this public key already exists');
  });
});
