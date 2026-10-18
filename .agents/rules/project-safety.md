---
trigger: always_on
description: "Safety, preservation, phase gates, and verified testing rules for the PS3 joystick project."
---

# PS3 Joystick Project Safety Rules

- Never delete, overwrite, or mass-reformat existing user files without explicit approval.
- Inspect existing project files and Git status before changes; preserve all existing work.
- Read `PHASES.md` and follow phases in order.
- Genuine virtual gamepad recognized by RPCS3 is a hard requirement. Keyboard/mouse mapping is not success.
- Do not begin full implementation until Phase 1 proves a programmatically controllable backend on the actual Mac/RPCS3 environment.
- Do not invent APIs, compatibility, test results, or performance figures.
- Use free tools and verify licenses/support from official sources.
- Do not install system extensions, change security settings, reboot, or run privileged commands without explicit approval.
- Run relevant tests after changes and record exact results in `docs/PROGRESS.md`.
- Keep secrets, credentials, signing keys, and machine-specific configuration out of Git.
- Never force-push or use destructive cleanup/reset commands.
- Report blocked and unrun tests honestly.
