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

## Step 3: Verification (Firebase Test Lab)

If the change impacts connectivity, UI, security, or infrastructure:

1. Build the APK:
// turbo
```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug
```

2. Run a Firebase robo test:
```bash
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device model=MediumPhone.arm,version=34 \
  --timeout=300s
```

3. Review the test results URL provided in the output.
4. For VPN-specific changes, instruct the user to sideload the APK on their physical device for manual tunnel verification.

## Step 4: Integration & Cleanup

1. Confirm Firebase tests passed (no crashes, ANRs, or UI regressions).
2. Run local linters and remove unused imports.
3. Verify GCP resource status via `gcloud` if infrastructure was modified.
4. Commit and push to GitHub.
5. Provide a **"Minimal Change Summary"** to the user.
