#!/bin/bash
set -e

# Ensure WireGuard is installed
apt-get update -y > /dev/null 2>&1
apt-get install -y wireguard > /dev/null 2>&1

# Generate server keys
wg genkey | tee /etc/wireguard/server_private.key | wg pubkey > /etc/wireguard/server_public.key
chmod 600 /etc/wireguard/server_private.key

PRIV=$(cat /etc/wireguard/server_private.key)
PUB=$(cat /etc/wireguard/server_public.key)
IFACE=$(ip route show default | awk '{print $5}')

# Create WireGuard config
cat > /etc/wireguard/wg0.conf << WGEOF
[Interface]
Address = 10.66.66.1/24
ListenPort = 51820
PrivateKey = ${PRIV}
PostUp = iptables -t nat -A POSTROUTING -o ${IFACE} -j MASQUERADE; iptables -A FORWARD -i wg0 -j ACCEPT; iptables -A FORWARD -o wg0 -j ACCEPT
PostDown = iptables -t nat -D POSTROUTING -o ${IFACE} -j MASQUERADE; iptables -D FORWARD -i wg0 -j ACCEPT; iptables -D FORWARD -o wg0 -j ACCEPT
WGEOF

chmod 600 /etc/wireguard/wg0.conf

# Enable and start
systemctl enable wg-quick@wg0
systemctl start wg-quick@wg0

echo "=== WIREGUARD SETUP COMPLETE ==="
echo "SERVER_PUBLIC_KEY=${PUB}"
wg show wg0
