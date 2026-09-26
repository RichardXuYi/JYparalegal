---
id: add-skillhub-skill-store
title: Add the SkillHub store to the Skills page
scenario: plugin-lifecycle-management
taskType: plugin-lifecycle
intent: Let users search and install skills from SkillHub (skillhub.cn) inside the Skills page. The upstream installer is a bash script that writes a bash wrapper and needs a working python3, neither of which exists on a stock Windows desktop, so the app fetches the kit itself and runs the CLI with the uv-managed Python it already ships.
touchedAreas:
  - electron/services/skills/skillhub-service.ts
  - electron/services/skills/local-skill-service.ts
  - electron/utils/uv-setup.ts
  - electron/main/ipc-handlers.ts
  - shared/host-api/contract.ts
  - src/lib/host-api.ts
  - src/pages/Skills/index.tsx
  - shared/i18n/locales/**
  - README.md
expectedUserBehavior:
  - Opening 安装技能 in the Skills page prepares the store once (kit download + managed Python) and then shows SkillHub search results.
  - Searching by keyword lists SkillHub skills with their name, namespace handle and version.
  - Installing a skill writes it into the signed-in account's skills directory and the skill then appears in the Skills list, enabled.
  - Uninstalling removes the installed skill directory and its SkillHub lock entry.
  - A SkillHub install is discovered by the local skill scanner even though the CLI nests it under an `@handle/` folder.
  - If preparation fails, the sheet shows the failure with a retry action instead of an empty list.
requiredProfiles:
  - fast
acceptance:
  - The store never depends on a user-installed python3 or on bash.
  - Searching an empty query does not call the CLI (SkillHub has no browse-all mode) and does not surface an error.
  - Install targets the scoped skills directory returned by getOpenClawSkillsDir().
  - Installed skills are matched against namespaced search results by their trailing slug segment.
  - The `@handle/` scan level is enabled only for the managed skills root, so workspace and agents roots keep their one-level semantics.
docs:
  required: true
---

Use this task spec when changing how the Skills page discovers, searches, or installs third-party skills.

SkillHub is an external Python CLI. Its kit is downloaded to the app's data directory and executed with the uv-managed Python 3.12; the CLI name and slug reach argv only after validation, and installs are confined to the per-account skills directory.