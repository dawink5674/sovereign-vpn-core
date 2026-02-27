---
description: The /dev-cycle workflow — high-precision development with minimal architectural churn
---

# /dev-cycle

**Goal:** High-precision development with minimal architectural churn.

For every user directive, strictly execute this 4-step loop:

## Step 1: Discovery (Read-Only)

1. Analyze the user request against current files.
2. **Constraint Check:** Determine the absolute minimum set of lines needing change to achieve the goal.
3. Propose an execution plan. Output: *"I am modifying [File] because [Functional Reason]. I will preserve existing [Pattern]."*

## Step 2: Implementation (Orchestrator)

1. Execute the minimal code changes.
2. Maintain consistency with surrounding code style.
3. Update the relevant `SKILL.md` if a new project-wide logic pattern was established.

## Step 3: Verification (Jules)

If the change impacts connectivity, security, or infrastructure:

1. Spawn a Jules task:
```bash
jules remote new --repo . --session "[Describe the verification objective]"
```
2. Monitor status:
```bash
jules remote status --session <SESSION_ID>
```
3. Iterate on failures until Jules returns a successful report.

## Step 4: Integration & Cleanup

1. Pull verified changes:
```bash
jules remote pull --session <SESSION_ID>
```
2. Run local linters.
3. Verify GCP resource status via `gcloud`.
4. Provide a **"Minimal Change Summary"** to the user.
