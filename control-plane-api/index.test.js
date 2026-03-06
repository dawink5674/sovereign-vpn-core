const request = require('supertest');
const { app, peers } = require('./index');

describe('Control Plane API Security Tests', () => {
  const TEST_API_KEY = 'test-secret-key';

  beforeEach(() => {
    // Reset the peers map before each test
    peers.clear();
  });

  afterEach(() => {
    // Clean up environment variables
    delete process.env.ADMIN_API_KEY;
  });

  describe('Authentication Middleware', () => {
    it('should fail with 500 if ADMIN_API_KEY is not configured', async () => {
      // Ensure it's not set
      delete process.env.ADMIN_API_KEY;

      const res = await request(app).get('/api/peers');

      expect(res.statusCode).toEqual(500);
      expect(res.body).toHaveProperty('error');
      expect(res.body.error).toContain('Server misconfiguration: ADMIN_API_KEY not set');
    });

    it('should fail with 401 if X-API-Key header is missing', async () => {
      process.env.ADMIN_API_KEY = TEST_API_KEY;

      const res = await request(app).get('/api/peers');

      expect(res.statusCode).toEqual(401);
      expect(res.body).toHaveProperty('error');
      expect(res.body.error).toEqual('Missing X-API-Key header');
    });

    it('should fail with 401 if X-API-Key header is incorrect', async () => {
      process.env.ADMIN_API_KEY = TEST_API_KEY;

      const res = await request(app)
        .get('/api/peers')
        .set('X-API-Key', 'wrong-key');

      expect(res.statusCode).toEqual(401);
      expect(res.body).toHaveProperty('error');
      expect(res.body.error).toEqual('Invalid API key');
    });

    it('should fail with 401 for incorrect key length (timingSafeEqual guard)', async () => {
        process.env.ADMIN_API_KEY = TEST_API_KEY;

        const res = await request(app)
          .get('/api/peers')
          .set('X-API-Key', 'short');

        expect(res.statusCode).toEqual(401);
        expect(res.body).toHaveProperty('error');
        expect(res.body.error).toEqual('Invalid API key');
    });

    it('should succeed with 200 for GET /api/peers if authenticated correctly', async () => {
      process.env.ADMIN_API_KEY = TEST_API_KEY;

      const res = await request(app)
        .get('/api/peers')
        .set('X-API-Key', TEST_API_KEY);

      expect(res.statusCode).toEqual(200);
      expect(res.body).toHaveProperty('count');
      expect(res.body).toHaveProperty('peers');
      expect(res.body.count).toEqual(0);
    });

    it('should enforce authentication on POST /api/peers', async () => {
        process.env.ADMIN_API_KEY = TEST_API_KEY;

        const res = await request(app)
          .post('/api/peers')
          .send({ name: 'test', publicKey: 'dummy' });

        expect(res.statusCode).toEqual(401);
    });

    it('should enforce authentication on DELETE /api/peers/:publicKey', async () => {
        process.env.ADMIN_API_KEY = TEST_API_KEY;

        const res = await request(app)
          .delete('/api/peers/dummy_key');

        expect(res.statusCode).toEqual(401);
    });
  });
});
