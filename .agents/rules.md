# Sovereign VPN — Orchestration Rules

## 1. Minimalist Refactoring Policy (CRITICAL)

- **Preservation of Vision:** Do NOT rewrite existing code for "readability" or "stylistic preference" unless it violates a core functional rule.
- **Impact Analysis:** Before modifying ANY file, the agent must state: *"I am modifying [File] because [Functional Reason]. I will preserve existing [Pattern]."*
- **Scoped Changes:** Keep PRs and commits focused on a single feature or bug. Do NOT combine "cleanups" with "feature updates."

## 2. Delegation Matrix

| Role | Responsibility |
|------|---------------|
| **Gemini / Opus (Architects)** | High-level logic, Kotlin Compose UI design, complex architectural decisions |
| **Firebase Test Lab (Automated QA)** | Robo tests, instrumentation tests, crash detection, UI validation on virtual devices |
| **Physical Device (Manual QA)** | VPN tunnel verification, download/upload stats, real-network GeoIP testing |

## 3. Mandatory Safety Gates

- **Infrastructure:** Any change to `terraform/` or `Dockerfile` **MUST** pass a Firebase robo test and GCP resource verification before integration.
- **VPN Core:** Any change to `VpnManager.kt` logic (handshakes, key rotation) **MUST** be reviewed against the Mobile & VPN Specialist skill and manually tested on a physical device.
- **SSH Automation:** Any change to `index.js` (control-plane-api) **MUST** be verified via `gcloud` CLI and Cloud Run logs.

### Firebase Testing & APK Deployment Workflow
- **Automated First:** All code changes must pass a Firebase Test Lab robo test before being pushed to GitHub.
- **Manual Second:** VPN-specific changes (tunnel state, bypass networking, download/upload stats) must be sideloaded and manually verified on a physical device.
- **Final Build:** The agent is expressly forbidden from pushing a new APK until both automated (Firebase) and manual (physical device) verification confirm no regressions.

### Firewall Architecture

- `default-allow-ssh` (tcp:22 from `0.0.0.0/0`) — **Required.** Cloud Run SSHes directly to the WireGuard VM for peer auto-apply. Do NOT delete this rule unless a VPC Connector with scoped firewall is deployed first.
- `default-allow-rdp` (tcp:3389 from `0.0.0.0/0`) — **Unused.** Should be deleted to reduce attack surface.
- `allow-iap-ssh` — For manual operator SSH sessions only (via `--tunnel-through-iap`).

## 4. Final Cleanup

After every dev-cycle iteration:

1. Run lint and remove unused imports.
2. Update the relevant `SKILL.md` if a new project-wide pattern was established.
3. Verify GCP resource status via `gcloud`.
