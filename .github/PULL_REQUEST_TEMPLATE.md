## What Changed

Briefly describe the change.

## Change Type

- [ ] Bug fix
- [ ] Feature
- [ ] Documentation
- [ ] Tests / refactoring

## Verification

- [ ] `./gradlew test`
- [ ] `./gradlew build`
- [ ] Not run, reason:

## Safety invariants

- [ ] No new write calls (`POST`/`PUT`/`DELETE`/`PATCH`) outside the approved opt-in surface,
      or the change is purely read-only
- [ ] Tools return `api/*` types only; nothing new is written to `System.out`
- [ ] The PR contains no secrets, API keys, or private URLs
