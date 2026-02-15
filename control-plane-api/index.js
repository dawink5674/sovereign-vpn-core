const express = require('express');
const crypto = require('crypto');
const { Client } = require('ssh2');

const app = express();
const PORT = process.env.PORT || 8080;

// Server config — in production, pull from Secret Manager
const SERVER_PUBLIC_KEY = process.env.SERVER_PUBLIC_KEY || 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=';
const SERVER_ENDPOINT = process.env.SERVER_ENDPOINT || '35.206.67.49:51820';
const VPN_SUBNET = '10.66.66';
const DNS_SERVERS = '1.1.1.1, 1.0.0.1';

// SSH config for WireGuard server — set as Cloud Run env vars
const WG_SSH_HOST = process.env.WG_SSH_HOST || '35.206.67.49';
const WG_SSH_PORT = parseInt(process.env.WG_SSH_PORT || '22');
const WG_SSH_USER = process.env.WG_SSH_USER || 'root';
const WG_SSH_KEY = process.env.WG_SSH_KEY || ''; // base64-encoded private key
const WG_INTERFACE = process.env.WG_INTERFACE || 'wg0';

app.use(express.json());

// In-memory peer store (replace with Firestore in production)
const peers = new Map();
let nextIP = 2; // .1 is the server

// ---------------------------------------------------------------------------
// SSH helper — execute a command on the WireGuard server
// ---------------------------------------------------------------------------
function sshExec(command, stdinData = null) {
  return new Promise((resolve, reject) => {
    if (!WG_SSH_KEY) {
      return reject(new Error('WG_SSH_KEY not configured — skipping SSH'));
    }

    const conn = new Client();
    let output = '';
    let errorOutput = '';

    conn.on('ready', () => {
      conn.exec(command, (err, stream) => {
        if (err) {
          conn.end();
          return reject(err);
        }

        // If we need to pipe data to stdin (e.g. preshared key)
        if (stdinData) {
          stream.stdin.write(stdinData);
          stream.stdin.end();
        }

        stream.on('data', (data) => { output += data.toString(); });
        stream.stderr.on('data', (data) => { errorOutput += data.toString(); });

        stream.on('close', (code) => {
          conn.end();
          if (code === 0) {
            resolve(output.trim());
          } else {
            reject(new Error(`SSH command failed (exit ${code}): ${errorOutput || output}`));
          }
        });
      });
    });

    conn.on('error', (err) => reject(err));

    // Decode base64 private key
    let privateKey;
    try {
      privateKey = Buffer.from(WG_SSH_KEY, 'base64').toString('utf-8');
    } catch (e) {
      return reject(new Error('Failed to decode WG_SSH_KEY from base64'));
    }

    conn.connect({
      host: WG_SSH_HOST,
      port: WG_SSH_PORT,
      username: WG_SSH_USER,
      privateKey,
      readyTimeout: 10000,
    });
  });
}

// ---------------------------------------------------------------------------
// Apply a peer to the live WireGuard interface via SSH
// Uses `wg set` which adds the peer without restarting the interface
// ---------------------------------------------------------------------------
async function applyPeerToServer(publicKey, presharedKey, assignedIP) {
  try {
    // wg set requires the preshared key via a file/pipe
    // We use a temp file approach: echo key > /tmp/psk && wg set ... && rm /tmp/psk
    const pskFile = `/tmp/psk_${Date.now()}`;
    const commands = [
      `echo '${presharedKey}' > ${pskFile}`,
      `sudo wg set ${WG_INTERFACE} peer ${publicKey} preshared-key ${pskFile} allowed-ips ${assignedIP}`,
      `rm -f ${pskFile}`,
    ].join(' && ');

    await sshExec(commands);
    console.log(`✅ Peer ${publicKey.substring(0, 8)}... applied to ${WG_INTERFACE}`);

    // Also ensure IP forwarding and NAT (idempotent)
    await sshExec([
      'sudo sysctl -q -w net.ipv4.ip_forward=1',
      `sudo iptables -t nat -C POSTROUTING -s ${VPN_SUBNET}.0/24 -o eth0 -j MASQUERADE 2>/dev/null || sudo iptables -t nat -A POSTROUTING -s ${VPN_SUBNET}.0/24 -o eth0 -j MASQUERADE`,
    ].join(' && '));

    return { success: true };
  } catch (err) {
    console.error(`⚠️  SSH apply failed: ${err.message}`);
    return { success: false, error: err.message };
  }
}

// ---------------------------------------------------------------------------
// Remove a peer from the live WireGuard interface via SSH
// ---------------------------------------------------------------------------
async function removePeerFromServer(publicKey) {
  try {
    await sshExec(`sudo wg set ${WG_INTERFACE} peer ${publicKey} remove`);
    console.log(`✅ Peer ${publicKey.substring(0, 8)}... removed from ${WG_INTERFACE}`);
    return { success: true };
  } catch (err) {
    console.error(`⚠️  SSH remove failed: ${err.message}`);
    return { success: false, error: err.message };
  }
}

// ---------------------------------------------------------------------------
// Health check — Cloud Run readiness probe
// ---------------------------------------------------------------------------
app.get('/api/health', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'sovereign-vpn-control-plane',
    activePeers: peers.size,
    sshConfigured: !!WG_SSH_KEY,
    timestamp: new Date().toISOString(),
  });
});

// ---------------------------------------------------------------------------
// POST /api/peers — Zero-Trust peer provisioning
//
// The client generates its own keypair locally and sends ONLY the public key.
// The server NEVER sees the client's private key.
//
// After registration, the peer is automatically applied to the WireGuard
// server via SSH so traffic can flow immediately.
// ---------------------------------------------------------------------------
app.post('/api/peers', async (req, res) => {
  try {
    const { name, publicKey } = req.body;

    if (!name || typeof name !== 'string' || name.trim().length === 0) {
      return res.status(400).json({ error: 'Peer name is required' });
    }

    if (!publicKey || typeof publicKey !== 'string') {
      return res.status(400).json({ error: 'Client public key (base64) is required' });
    }

    // Validate base64 key is 44 chars (32 bytes base64-encoded)
    const keyBuffer = Buffer.from(publicKey, 'base64');
    if (keyBuffer.length !== 32) {
      return res.status(400).json({
        error: 'Invalid public key: must be 32 bytes (Curve25519)',
      });
    }

    // Reject duplicate registrations
    if (peers.has(publicKey)) {
      return res.status(409).json({ error: 'Peer with this public key already exists' });
    }

    // Assign internal VPN IP
    const clientIP = `${VPN_SUBNET}.${nextIP}`;
    nextIP++;

    // Generate pre-shared key (symmetric, for post-quantum defense-in-depth)
    const presharedKey = crypto.randomBytes(32).toString('base64');

    // Store peer
    const peer = {
      name: name.trim(),
      publicKey,
      assignedIP: `${clientIP}/32`,
      presharedKey,
      createdAt: new Date().toISOString(),
    };
    peers.set(publicKey, peer);

    // Apply peer to WireGuard server via SSH (non-blocking for response)
    const sshResult = await applyPeerToServer(publicKey, presharedKey, `${clientIP}/32`);

    // Return everything the client needs to build its own config
    res.status(201).json({
      message: `Peer "${peer.name}" registered`,
      peer: {
        name: peer.name,
        assignedIP: `${clientIP}/32`,
        createdAt: peer.createdAt,
      },
      serverConfig: {
        serverPublicKey: SERVER_PUBLIC_KEY,
        endpoint: SERVER_ENDPOINT,
        presharedKey,
        dns: DNS_SERVERS,
        allowedIPs: '0.0.0.0/0, ::/0',
        persistentKeepalive: 25,
      },
      serverPeerBlock: `\n[Peer]\n# ${peer.name}\nPublicKey = ${publicKey}\nPresharedKey = ${presharedKey}\nAllowedIPs = ${clientIP}/32\n`,
      serverApplied: sshResult.success,
    });
  } catch (err) {
    console.error('Peer registration error:', err);
    res.status(500).json({ error: 'Failed to register peer', details: err.message });
  }
});

// ---------------------------------------------------------------------------
// GET /api/peers — List all active peers (no secrets exposed)
// ---------------------------------------------------------------------------
app.get('/api/peers', (_req, res) => {
  const peerList = Array.from(peers.values()).map(({ name, publicKey, assignedIP, createdAt }) => ({
    name,
    publicKey,
    assignedIP,
    createdAt,
  }));

  res.status(200).json({ count: peerList.length, peers: peerList });
});

// ---------------------------------------------------------------------------
// DELETE /api/peers/:publicKey — Revoke a peer
// ---------------------------------------------------------------------------
app.delete('/api/peers/:publicKey', async (req, res) => {
  const { publicKey } = req.params;
  const decoded = decodeURIComponent(publicKey);

  if (!peers.has(decoded)) {
    return res.status(404).json({ error: 'Peer not found' });
  }

  const peer = peers.get(decoded);
  peers.delete(decoded);

  // Remove peer from WireGuard server via SSH
  const sshResult = await removePeerFromServer(decoded);

  res.status(200).json({
    message: `Peer "${peer.name}" revoked`,
    removedPeer: {
      name: peer.name,
      publicKey: decoded,
      assignedIP: peer.assignedIP,
    },
    serverRemoved: sshResult.success,
  });
});

app.listen(PORT, () => {
  console.log(`Control Plane API listening on port ${PORT}`);
  console.log(`Zero-Trust mode: clients generate their own keys`);
  console.log(`SSH auto-apply: ${WG_SSH_KEY ? 'ENABLED' : 'DISABLED (set WG_SSH_KEY to enable)'}`);
});
