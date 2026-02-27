---
name: GCP Cloud Architect & Terminal Pro
description: Enterprise-grade GCP operations including Cloud Run revision management, GCE IAP security, and Terraform state integrity.
---

# GCP Cloud Architect & Terminal Pro

## Command Surface & Mastery

### Cloud Run (Control Plane)

- **Revision Control:** `gcloud run deploy vpn-control-plane --no-traffic --tag dev-version` (deploy without switching traffic).
- **Traffic Splitting:** `gcloud run services update-traffic vpn-control-plane --to-revisions=REV=100`.
- **Diagnostics:** `gcloud run services describe vpn-control-plane --region=us-central1` (check env vars and secrets).

### Compute Engine (WireGuard Gateway)

- **IAP Secure SSH (manual):** `gcloud compute ssh wireguard-vpn --tunnel-through-iap --project=sovereign-vpn-core --zone=us-central1-f`.
- **Firewall Audit:** `gcloud compute firewall-rules list --project=sovereign-vpn-core --format="yaml(name,allowed,sourceRanges,targetTags)"`.
- **SSH Architecture Note:** Cloud Run → WireGuard SSH uses direct TCP:22 (not IAP), so `default-allow-ssh` must remain. IAP SSH is for manual operator access only.

### Infrastructure as Code (Terraform)

- **State Protection:** Always use `-out=plan.tfplan` and `terraform show` before applying.
- **Isolation:** Use STANDARD tier network addresses for cost-effective egress.

## Operations Logic

- **Identity First:** Bind service accounts to specific roles (Log Writer, Metric Writer) instead of project-wide Editor permissions.
- **Secret Management:** Inject sensitive keys (like `WG_SSH_KEY`) as environment variables via Secret Manager volumes.
