const { EventEmitter } = require('events');

jest.mock('ssh2', () => {
  const { EventEmitter } = require('events');
  class MockClient extends EventEmitter {
    constructor() {
      super();
      this.exec = jest.fn();
      this.end = jest.fn();
      this.connect = jest.fn();
      // Need to capture the instance so tests can access it
      MockClient.lastInstance = this;
    }
  }
  return { Client: MockClient };
}, { virtual: true });

describe('sshExec', () => {
  let originalEnv;

  beforeEach(() => {
    originalEnv = { ...process.env };
    // Provide a valid base64 key
    process.env.WG_SSH_KEY = Buffer.from('fake-private-key').toString('base64');
    process.env.WG_SSH_HOST = 'test-host';
    process.env.WG_SSH_PORT = '2222';
    process.env.WG_SSH_USER = 'test-user';

    jest.resetModules();
  });

  afterEach(() => {
    process.env = originalEnv;
    jest.clearAllMocks();
  });

  it('rejects if WG_SSH_KEY is not configured', async () => {
    process.env.WG_SSH_KEY = '';
    const { sshExec } = require('./index');

    await expect(sshExec('echo test')).rejects.toThrow('WG_SSH_KEY not configured — skipping SSH');
  });

  it('rejects if WG_SSH_KEY fails to decode from base64 (simulate error)', async () => {
    const originalFrom = Buffer.from;
    jest.spyOn(Buffer, 'from').mockImplementation((...args) => {
      if (args[0] === process.env.WG_SSH_KEY) throw new Error('Bad base64');
      return originalFrom(...args);
    });

    const { sshExec } = require('./index');
    await expect(sshExec('echo test')).rejects.toThrow('Failed to decode WG_SSH_KEY from base64');

    Buffer.from.mockRestore();
  });

  it('rejects on connection error', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test');

    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.emit('error', new Error('Connection failed'));

    await expect(promise).rejects.toThrow('Connection failed');
    expect(clientInstance.connect).toHaveBeenCalledWith({
      host: 'test-host',
      port: 2222,
      username: 'test-user',
      privateKey: 'fake-private-key',
      readyTimeout: 10000,
    });
  });

  it('resolves on successful command execution', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test');
    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.exec.mockImplementation((cmd, callback) => {
      const stream = new EventEmitter();
      stream.stderr = new EventEmitter();
      callback(null, stream);

      stream.emit('data', Buffer.from('success output\n'));
      stream.emit('close', 0); // exit code 0
    });

    clientInstance.emit('ready');

    const result = await promise;
    expect(result).toBe('success output');
    expect(clientInstance.end).toHaveBeenCalled();
  });

  it('rejects on command execution error (exec err)', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test');
    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.exec.mockImplementation((cmd, callback) => {
      callback(new Error('Exec failed'));
    });

    clientInstance.emit('ready');

    await expect(promise).rejects.toThrow('Exec failed');
    expect(clientInstance.end).toHaveBeenCalled();
  });

  it('rejects on non-zero exit code with stderr', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test');
    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.exec.mockImplementation((cmd, callback) => {
      const stream = new EventEmitter();
      stream.stderr = new EventEmitter();
      callback(null, stream);

      stream.stderr.emit('data', Buffer.from('some error\n'));
      stream.emit('close', 1); // exit code 1
    });

    clientInstance.emit('ready');

    await expect(promise).rejects.toThrow('SSH command failed (exit 1): some error\n');
    expect(clientInstance.end).toHaveBeenCalled();
  });

  it('rejects on non-zero exit code with stdout as fallback message', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test');
    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.exec.mockImplementation((cmd, callback) => {
      const stream = new EventEmitter();
      stream.stderr = new EventEmitter();
      callback(null, stream);

      stream.emit('data', Buffer.from('output error\n'));
      stream.emit('close', 2);
    });

    clientInstance.emit('ready');

    await expect(promise).rejects.toThrow('SSH command failed (exit 2): output error\n');
    expect(clientInstance.end).toHaveBeenCalled();
  });

  it('passes stdinData to the stream if provided', async () => {
    const { sshExec } = require('./index');
    const ssh2 = require('ssh2');

    const promise = sshExec('echo test', 'some input');
    const clientInstance = ssh2.Client.lastInstance;

    clientInstance.exec.mockImplementation((cmd, callback) => {
      const stream = new EventEmitter();
      stream.stderr = new EventEmitter();
      stream.stdin = {
        write: jest.fn(),
        end: jest.fn()
      };
      callback(null, stream);

      stream.emit('data', Buffer.from('ok'));
      stream.emit('close', 0);

      expect(stream.stdin.write).toHaveBeenCalledWith('some input');
      expect(stream.stdin.end).toHaveBeenCalled();
    });

    clientInstance.emit('ready');

    const result = await promise;
    expect(result).toBe('ok');
    expect(clientInstance.end).toHaveBeenCalled();
  });
});
