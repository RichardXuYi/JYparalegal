---
id: packaged-dependency-resolution
title: Packaged Dependency Resolution
type: ai-coding-rule
appliesTo:
  - plugin-lifecycle-management
requiredProfiles:
  - fast
---

Flattening the OpenClaw dependency closure into `build/openclaw/node_modules/`
must preserve pnpm's per-consumer resolution. The gateway's own direct
dependencies (and the extra root requirements from
`scripts/openclaw-bundle-config.mjs`) own the top-level slots; any consumer
whose resolved instance differs gets a private copy under
`<consumer>/node_modules/`.

Do not deduplicate by package name alone. Keeping a single version per name
silently drops the other instances: `htmlparser2@10.1.0` declares
`entities@^7.0.1` and imports `fromCodePoint` from `entities/decode`, while
openclaw pins `entities@8.1.0`, which removed that export. The bundled gateway
then failed at ESM link time (`does not provide an export named
'fromCodePoint'`), which surfaced to users as an unrelated
state-migration/exit-78 failure.

Root requirements that cannot take a top-level slot must fail the bundle build
with an actionable message instead of dropping a version silently. Aligning a
conflicting consumer upward (pnpm-workspace.yaml `overrides`, or upgrading the
consumer) is preferred when the ecosystem allows it; where it does not (for
example `node-fetch` 2 vs 3 across the CJS/ESM boundary, the `yargs` 15/17
family behind `qrcode`, or the DOM family that `linkedom` itself splits), the
private copy is the correct outcome.

Any change to the flattening step in `scripts/bundle-openclaw.mjs` must be
verified by running the bundle and asserting that every dependency declared
inside `build/openclaw/node_modules/` resolves, from its declaring package, to
a version that satisfies its range.