const express = require('express');
const crypto = require('crypto');

const app = express();
const PORT = process.env.PORT || 8080;

// Server config — in production, pull from Secret Manager
const SERVER_PUBLIC_KEY = process.env.SERVER_PUBLIC_KEY || 'G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=';
const SERVER_ENDPOINT = process.env.SERVER_ENDPOINT || '35.206.67.49:51820';
const VPN_SUBNET = '10.66.66';
const DNS_SERVERS = '1.1.1.1, 1.0.0.1';

app.use(express.json());

// In-memory peer store (replace with Firestore in production)
const peers = new Map();
let nextIP = 2; // .1 is the server

// ---------------------------------------------------------------------------
// Health check — Cloud Run readiness probe
// ---------------------------------------------------------------------------
app.get('/api/health', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'sovereign-vpn-control-plane',
    activePeers: peers.size,
    timestamp: new Date().toISOString(),
  });
});

// ---------------------------------------------------------------------------
// POST /api/peers — Zero-Trust peer provisioning
//
// The client generates its own keypair locally and sends ONLY the public key.
// The server NEVER sees the client's private key.
//
// Request:  { "name": "Device Name", "publicKey": "CLIENT_BASE64_PUBKEY" }
// Response: Server public key, endpoint, assigned IP, preshared key, DNS
//           → Client assembles its own WireGuard config locally.
// ---------------------------------------------------------------------------
app.post('/api/peers', (req, res) => {
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

    // Return everything the client needs to build its own config
    // NOTE: No private key ever leaves the client device
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
app.delete('/api/peers/:publicKey', (req, res) => {
  const { publicKey } = req.params;
  const decoded = decodeURIComponent(publicKey);

  if (!peers.has(decoded)) {
    return res.status(404).json({ error: 'Peer not found' });
  }

  const peer = peers.get(decoded);
  peers.delete(decoded);

  res.status(200).json({
    message: `Peer "${peer.name}" revoked`,
    removedPeer: {
      name: peer.name,
      publicKey: decoded,
      assignedIP: peer.assignedIP,
    },
    serverAction: `Run on server: wg set wg0 peer ${decoded} remove`,
  });
});

app.listen(PORT, () => {
  console.log(`Control Plane API listening on port ${PORT}`);
  console.log(`Zero-Trust mode: clients generate their own keys`);
});
