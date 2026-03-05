const request = require('supertest');
const { app, peers } = require('./index');

jest.mock('ssh2', () => {
  return {
    Client: jest.fn().mockImplementation(() => {
      return {
        on: jest.fn(),
        connect: jest.fn(),
        end: jest.fn(),
        exec: jest.fn()
      };
    })
  };
});

describe('Control Plane API Security Tests', () => {
  beforeEach(() => {
    peers.clear();
  });

  it('should reject a malicious public key on POST /api/peers', async () => {
    const maliciousKey = 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=; rm -rf /';
    const response = await request(app)
      .post('/api/peers')
      .send({
        name: 'hacker',
        publicKey: maliciousKey
      });

    expect(response.status).toBe(400);
    expect(response.body.error).toContain('Invalid public key');
    expect(peers.size).toBe(0);
  });

  it('should reject a malicious public key on DELETE /api/peers/:publicKey', async () => {
    // First setup a valid peer to ensure it doesn't get deleted accidentally
    const validKey = 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=';
    peers.set(validKey, {
      name: 'valid_user',
      publicKey: validKey,
      assignedIP: '10.66.66.2/32',
      presharedKey: 'somekey',
      createdAt: new Date().toISOString()
    });

    const maliciousKey = validKey + '; rm -rf /';
    const encodedMaliciousKey = encodeURIComponent(maliciousKey);

    const response = await request(app)
      .delete(`/api/peers/${encodedMaliciousKey}`);

    expect(response.status).toBe(400);
    expect(response.body.error).toContain('Invalid public key format');
    expect(peers.size).toBe(1); // The valid peer is still there
  });
});
