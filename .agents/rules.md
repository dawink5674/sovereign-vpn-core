# Sovereign VPN — Orchestration Rules

## 1. Minimalist Refactoring Policy (CRITICAL)

- **Preservation of Vision:** Do NOT rewrite existing code for "readability" or "stylistic preference" unless it violates a core functional rule.
- **Impact Analysis:** Before modifying ANY file, the agent must state: *"I am modifying [File] because [Functional Reason]. I will preserve existing [Pattern]."*
- **Scoped Changes:** Keep PRs and commits focused on a single feature or bug. Do NOT combine "cleanups" with "feature updates."

## 2. Delegation Matrix

| Role | Responsibility |
|------|---------------|
| **Gemini / Opus (Architects)** | High-level logic, Kotlin Compose UI design, complex architectural decisions |
| **Jules (The Hands)** | Code execution, VM-based testing, security scanning, resolving merge conflicts |

## 3. Mandatory Safety Gates

- **Infrastructure:** Any change to `terraform/` or `Dockerfile` **MUST** be verified by a Jules session before integration.
- **VPN Core:** Any change to `VpnManager.kt` logic (handshakes, key rotation) **MUST** be reviewed against the Mobile & VPN Specialist skill.
- **SSH Automation:** Any change to `index.js` (control-plane-api) **MUST** be remotely verified by Jules (e.g., verifying 32-byte public key registration).

### Jules Testing & APK Deployment Workflow
- **Mandatory Wait:** When the user requests Jules for testing, the Antigravity agent MUST wait for Jules to fully complete its test suite and reporting before proceeding.
- **Compare and Contrast:** The agent MUST pull and review Jules' test results/changes, actively compare them against the local codebase, and merge the best aspects of both tests.
- **Final Build:** The agent is expressly forbidden from building or pushing a new APK until this comparison and integration is complete to ensure the highest quality build.

### Firewall Architecture

- `default-allow-ssh` (tcp:22 from `0.0.0.0/0`) — **Required.** Cloud Run SSHes directly to the WireGuard VM for peer auto-apply. Do NOT delete this rule unless a VPC Connector with scoped firewall is deployed first.
- `default-allow-rdp` (tcp:3389 from `0.0.0.0/0`) — **Unused.** Should be deleted to reduce attack surface.
- `allow-iap-ssh` — For manual operator SSH sessions only (via `--tunnel-through-iap`).

## 4. Final Cleanup

After every dev-cycle iteration:

1. Run lint and remove unused imports.
2. Update the relevant `SKILL.md` if a new project-wide pattern was established.
3. Verify GCP resource status via `gcloud`.
