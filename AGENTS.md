# Mandatory Agent Instructions

You are implementing a compatibility-first, free, open-source Android-to-Mac PS3-style game controller for RPCS3.

## Non-negotiable safety and preservation rules

1. **Never delete, overwrite, move, or mass-reformat existing user files without explicit approval.**
2. Before editing an existing repository, inspect `git status`, list the tree, read existing README/docs/build files, and identify uncommitted work. Preserve it.
3. If the current workspace contains an existing project, integrate into it carefully. Do not replace it with this scaffold.
4. Make a backup before any migration or broad refactor. Never use destructive commands such as `rm -rf`, `git reset --hard`, `git clean -fd`, force-push, or overwrite an existing file to “fix” the project.
5. Do not install kernel/system extensions, change macOS security settings, grant permissions, or run privileged commands without the user explicitly approving the specific action.
6. Never commit secrets, tokens, local IP addresses if they are sensitive, signing keys, keystores, certificates, private screenshots, or machine-specific paths.
7. Never claim a build/test passed unless you actually ran it and captured its result. Label unavailable tests as NOT RUN and failures as BLOCKED/FAILED.
8. Do not make a keyboard/mouse mapping count as successful completion. Requirement is a genuine virtual gamepad recognized by RPCS3.

## Required workflow

- Read `PHASES.md`, `docs/ARCHITECTURE.md`, and `docs/COMPATIBILITY_MATRIX.md` before coding.
- Work strictly in phase order. Complete and verify each phase before advancing.
- Update `docs/PROGRESS.md` and `docs/DECISION_LOG.md` after each meaningful step.
- Keep changes small, review diffs, and run tests after changes.
- Use feature branches when practical; never force-push.
- Prefer stable, free, maintained tools and official documentation.
- Verify current API, license, OS support, and build requirements from primary sources before selecting dependencies.
- Keep dependencies minimal. Do not add paid services, subscriptions, cloud relays, analytics, or telemetry.
- If a compatibility gate fails, do not continue as though it passed. Investigate only non-destructive alternatives and record the evidence. Stop at a real external blocker and provide precise manual next steps.
- Do not promise overnight completion. Continue autonomously through tasks that are safe and available, but stop for approval, missing hardware, required reboot, system permissions, or an unresolved compatibility decision.

## Completion reporting

At the end of each phase, report:
- files changed;
- commands/tests executed;
- exact pass/fail/not-run outcomes;
- known limitations;
- next phase and whether its gate is satisfied.

Before publishing:
- run available builds, tests, lint, secret scan, and `git diff --check`;
- inspect `git status` and the full diff;
- confirm license and third-party notices;
- ensure no credentials or machine-specific files are staged;
- ask the user before creating/pushing a public GitHub repository or publishing a release unless explicit permission was given for that exact action.
