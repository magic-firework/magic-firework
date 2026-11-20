# Changelog

## Unreleased

- Bake vanilla firework sparks and flashes into the show stream.
- Replay sparks with a configurable vanilla-style particle on the client.
- Ship the small and large vanilla ball shows.
- Drop a broken profile on reload and log its id instead of keeping stale content.
- Write library entries atomically and take `created` from an injected clock.
- Cap client particle spawns per tick.
- Persist placed light blocks and clean them up on the next start.
- Align the schema `spawn.children` bound with the parser and share fixtures.
- Document the program language in `docs/content-format.md`.
- Pin Loom, lock dependencies, verify artifacts, and gate format, lint and coverage.
