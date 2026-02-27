---
name: Jules Senior Specialist
description: Advanced Jules CLI orchestration for remote VM debugging, asynchronous task execution, and security-hardened peer review.
---

# Jules Senior Specialist

## Advanced Terminal Skills Reference

This section serves as a comprehensive reference for agents to interact with the Jules environment.

### 1. Authentication & System

- **`jules login`** — Authenticates the local environment with Google. Run once during project initialization or if session tokens expire.
- **`jules logout`** — Clears local authentication credentials. Use for security cleanup on shared environments.
- **`jules version`** — Displays the current CLI version. Use to verify environment consistency across IDE instances.
- **`jules help`** — Detailed command breakdown. Use `jules remote --help` to discover new flags for specific subcommands.

### 2. Task Management (`jules remote`)

The `remote` command is the primary interface for delegating work to Jules' isolated cloud VMs.

- **`jules remote list --repo`** — Lists all repositories currently connected to the Jules account.
- **`jules remote list --session`** — Lists active and historical coding sessions. Use this to check the `SESSION_ID` before running `status` or `pull` commands.
- **`jules remote new`** — The core command for triggering async work.
  - `--repo <org/repo>` — Specify target repo (defaults to current directory).
  - `--session "<prompt>"` — The actual instruction for Jules.
  - `--parallel <number>` — Spawns multiple VMs to attempt the same task with different approaches.
- **`jules remote status --session <id>`** — Polls the real-time activity of a running VM. Tail this output to provide "Status Updates" to the user without interrupting the task.
- **`jules remote pull --session <id>`** — Downloads and applies code changes from a Jules session directly to the local filesystem. Use for immediate verification before a GitHub commit is made.
- **`jules remote stop --session <id>`** — Terminates a running VM session to save resources.

### 3. Interactive TUI

- **`jules`** — Running the command without arguments launches the Terminal User Interface.
  - Features: Side-by-side diff viewer, interactive task creation, and visual session management.

## Advanced Workflows & Delegation

### Remote Verification (The "Safety Gate")

When changes impact the core VPN logic (`VpnManager.kt`) or SSH automation (`index.js`), the orchestrator MUST use Jules to verify the logic in a clean state:

```bash
jules remote new --repo . --session "Deploy the VPN Control Plane in the VM. Verify that the SSH auto-apply logic correctly handles a 32-byte public key registration."
```

### Security Auditing

Direct Jules to perform "Red Team" analysis on infrastructure files:

- `"Jules, execute a security scan on terraform/main.tf and identify any non-IAP SSH paths."`
- `"Jules, run npm audit and automatically generate a fix branch for any high-severity vulnerabilities."`

### Senior Decision Logic

1. **Discovery (Gemini/Opus):** Architect determines the "Minimal Change" required.
2. **Execution (Jules):** Orchestrator triggers Jules via `jules remote new` to handle the heavy lifting or high-risk execution. For testing, orchestrator MUST wait for Jules to fully report results.
3. **Compare & Contrast (Gemini/Opus):** When Jules completes testing/changes, the orchestrator uses `jules remote pull` to review the work. The orchestrator must actively contrast Jules' approach against the current local code and synthesize the best elements from both sets of tests.
4. **Verification & Deployment (Gemini/Opus):** Make the final code changes based on the comparison. Only *after* this synthesis is complete should a new APK be compiled and pushed. Do NOT push an APK before reviewing Jules' tasks.

> **Conclusion:** If Jules produces a cleaner, more secure result than a local edit, it is accepted. If Jules suggests a large refactor, the orchestrator must reject it in favor of the original project vision. Always combine the best of both approaches when testing.
