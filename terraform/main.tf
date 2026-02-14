# -----------------------------------------------------------------------------
# Service Account — Least Privilege (no default editor role)
# -----------------------------------------------------------------------------
resource "google_service_account" "wireguard_sa" {
  account_id   = "wireguard-vpn-sa"
  display_name = "WireGuard VPN Instance SA"
  description  = "Least-privilege SA for the WireGuard VM — logging and monitoring only"
}

resource "google_project_iam_member" "wireguard_log_writer" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.wireguard_sa.email}"
}

resource "google_project_iam_member" "wireguard_metric_writer" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.wireguard_sa.email}"
}

# -----------------------------------------------------------------------------
# Static External IP — STANDARD tier for 200 GB free egress
# -----------------------------------------------------------------------------
resource "google_compute_address" "wireguard_ip" {
  name         = "wireguard-static-ip"
  region       = var.region
  address_type = "EXTERNAL"
  network_tier = "STANDARD"
}

# -----------------------------------------------------------------------------
# Compute Instance — e2-micro (Always Free eligible)
# -----------------------------------------------------------------------------
resource "google_compute_instance" "wireguard" {
  name         = "wireguard-vpn"
  machine_type = "e2-micro"
  zone         = var.zone

  # Required for VPN traffic routing (NAT for Android / all clients)
  can_ip_forward = true

  boot_disk {
    initialize_params {
      image = "ubuntu-os-cloud/ubuntu-2204-lts"
      size  = 30 # GB — within free tier
      type  = "pd-standard"
    }
  }

  network_interface {
    network = "default"

    access_config {
      nat_ip       = google_compute_address.wireguard_ip.address
      network_tier = "STANDARD"
    }
  }

  metadata_startup_script = <<-EOT
    #!/bin/bash
    set -e

    # Enable IP forwarding (runtime)
    sysctl -w net.ipv4.ip_forward=1

    # Persist IP forwarding across reboots
    if ! grep -q "^net.ipv4.ip_forward=1" /etc/sysctl.conf; then
      echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
    fi

    # Install WireGuard
    apt-get update -y
    apt-get install -y wireguard

    echo "WireGuard installation complete."
  EOT

  service_account {
    email  = google_service_account.wireguard_sa.email
    scopes = ["cloud-platform"]
  }

  tags = ["wireguard-vpn"]

  labels = {
    purpose = "wireguard-vpn"
    tier    = "free"
  }
}

# -----------------------------------------------------------------------------
# Firewall: Allow WireGuard UDP traffic (mobile + desktop clients)
# -----------------------------------------------------------------------------
resource "google_compute_firewall" "allow_wireguard" {
  name    = "allow-wireguard-udp"
  network = "default"

  allow {
    protocol = "udp"
    ports    = ["51820"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["wireguard-vpn"]

  description = "Allow WireGuard VPN traffic from all clients (Android, desktop)"
}

# -----------------------------------------------------------------------------
# Firewall: Allow SSH only from Google IAP range (secure management)
# -----------------------------------------------------------------------------
resource "google_compute_firewall" "allow_iap_ssh" {
  name    = "allow-iap-ssh"
  network = "default"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"]
  target_tags   = ["wireguard-vpn"]

  description = "Allow SSH via Identity-Aware Proxy only"
}
