const express = require('express');
const crypto = require('crypto');
const { execSync } = require('child_process');

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
// POST /api/peers — Provision a new WireGuard peer
// Returns a complete client config (ready for Android/desktop import)
// ---------------------------------------------------------------------------
app.post('/api/peers', (req, res) => {
  try {
    const { name } = req.body;
    if (!name) {
      return res.status(400).json({ error: 'Peer name is required' });
    }

    // Generate client keypair using Node crypto (Curve25519)
    const clientPrivateKey = crypto.generateKeyPairSync('x25519');
    const privKeyDer = clientPrivateKey.privateKey.export({ type: 'pkcs8', format: 'der' });
    const pubKeyDer = clientPrivateKey.publicKey.export({ type: 'spki', format: 'der' });

    // Extract raw 32-byte keys from DER encoding
    const privateKeyBase64 = privKeyDer.subarray(-32).toString('base64');
    const publicKeyBase64 = pubKeyDer.subarray(-32).toString('base64');

    // Assign IP
    const clientIP = `${VPN_SUBNET}.${nextIP}`;
    nextIP++;

    // Generate pre-shared key for additional security layer
    const presharedKey = crypto.randomBytes(32).toString('base64');

    // Store peer info
    const peer = {
      name,
      publicKey: publicKeyBase64,
      assignedIP: `${clientIP}/32`,
      presharedKey,
      createdAt: new Date().toISOString(),
    };
    peers.set(publicKeyBase64, peer);

    // Generate client config for import (Android / desktop)
    const clientConfig = `[Interface]
PrivateKey = ${privateKeyBase64}
Address = ${clientIP}/32
DNS = ${DNS_SERVERS}

[Peer]
PublicKey = ${SERVER_PUBLIC_KEY}
PresharedKey = ${presharedKey}
Endpoint = ${SERVER_ENDPOINT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
`;

    // Peer config to add to server (would be sent via SSH in production)
    const serverPeerBlock = `
[Peer]
# ${name}
PublicKey = ${publicKeyBase64}
PresharedKey = ${presharedKey}
AllowedIPs = ${clientIP}/32
`;

    res.status(201).json({
      message: `Peer "${name}" provisioned successfully`,
      peer: {
        name,
        publicKey: publicKeyBase64,
        assignedIP: `${clientIP}/32`,
        createdAt: peer.createdAt,
      },
      clientConfig,
      serverPeerBlock,
      instructions: {
        android: 'Import clientConfig as a .conf file in the WireGuard Android app',
        desktop: 'Save clientConfig as wg-client.conf and run: wg-quick up ./wg-client.conf',
        server: 'Append serverPeerBlock to /etc/wireguard/wg0.conf and run: wg syncconf wg0 <(wg-quick strip wg0)',
      },
    });
  } catch (err) {
    console.error('Peer creation error:', err);
    res.status(500).json({ error: 'Failed to create peer', details: err.message });
  }
});

// ---------------------------------------------------------------------------
// GET /api/peers — List all active peers
// ---------------------------------------------------------------------------
app.get('/api/peers', (_req, res) => {
  const peerList = Array.from(peers.values()).map(({ name, publicKey, assignedIP, createdAt }) => ({
    name,
    publicKey,
    assignedIP,
    createdAt,
  }));

  res.status(200).json({
    count: peerList.length,
    peers: peerList,
  });
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
  console.log(`Server endpoint: ${SERVER_ENDPOINT}`);
});
