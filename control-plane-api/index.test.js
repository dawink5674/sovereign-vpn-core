const EventEmitter = require('events');
const ssh2 = require('ssh2');

process.env.WG_SSH_KEY = Buffer.from('test-private-key').toString('base64');

const { applyPeerToServer } = require('./index.js');

jest.mock('ssh2');

describe('applyPeerToServer edge case tests', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  afterAll(() => {
    delete process.env.WG_SSH_KEY;
  });

  it('should return success: false and error message when sshExec throws a connection error', async () => {
    class MockClient extends EventEmitter {
      constructor() {
        super();
      }
      connect() {
        setTimeout(() => this.emit('error', new Error('Mock SSH connection error')), 10);
      }
      end() {}
    }

    ssh2.Client.mockImplementation(() => new MockClient());

    const result = await applyPeerToServer('pubKey', 'presharedKey', '10.66.66.2/32');

    expect(result).toEqual({ success: false, error: 'Mock SSH connection error' });
  });

  it('should return success: false when SSH command execution fails with non-zero exit code', async () => {
    class MockClient extends EventEmitter {
      constructor() {
        super();
      }
      connect() {
        setTimeout(() => this.emit('ready'), 10);
      }
      exec(command, callback) {
        const stream = new EventEmitter();
        stream.stderr = new EventEmitter();

        callback(null, stream);

        // Simulate output and close
        setTimeout(() => {
          stream.stderr.emit('data', 'Command not found');
          stream.emit('close', 1);
        }, 10);
      }
      end() {}
    }

    ssh2.Client.mockImplementation(() => new MockClient());

    const result = await applyPeerToServer('pubKey', 'presharedKey', '10.66.66.2/32');

    expect(result.success).toBe(false);
    expect(result.error).toContain('SSH command failed (exit 1)');
    expect(result.error).toContain('Command not found');
  });
});
