# Robokassa storefront production release — 2026-10-31

## Release record

- Code change commit: `2678563` (`feat(payments): prepare Robokassa storefront and offer`).
- Production baseline: `/opt/astro`, commit `2d4898c`; the live checkout had unrelated modified and untracked files. It was not reset, pulled, or replaced.
- The deployed package was made by applying only the Robokassa storefront changes from the commit to a fresh snapshot of the four live frontend files. Existing production content outside those changes was preserved.
- Backend payment files already matched production and were not redeployed.
- Backup: `/opt/astro/release_backups/robokassa-20261031-2678563-predeploy/`.
- Staging and SHA-256 manifest: `/opt/astro/release_staging/robokassa-20261031-2678563/`.

## Verification

- Full tests: **562 passed, 2 skipped**; one Starlette/httpx deprecation warning from the test environment.
- Robokassa/payment targets: **19 passed**.
- `node --check frontend/js/i18n.js`, `git diff --check`, staged JavaScript syntax, and staged release-content checks passed.
- Production SHA-256 checks passed for `frontend/index.html`, `frontend/css/style.css`, `frontend/js/i18n.js`, `frontend/privacy.html`, and `frontend/img/robokassa-logo.svg`.
- Production HTTP smoke checks passed for the homepage, versioned CSS and i18n assets; `astro` service remained active.
- Browser check on `https://astrosmap.ru/`: Russian and English offers show Robokassa, the 149 ₽ one-time PDF report, receipt wording, and refund terms. The standalone privacy policy describes Robokassa for new payments and YooKassa for historical payments. Footer logo loaded at 161 px in light and dark themes.

## Remaining before real payments

- The production Robokassa config remains `is_test=true`; no real charge was attempted.
- The last available portal screenshot showed the shop as new/not activated. Shop approval and confirmation that the configured credentials are live credentials are still required before switching to live mode. Do not accept legal terms on the user's behalf.
