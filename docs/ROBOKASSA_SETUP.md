# Robokassa setup for astrosmap.ru

New site purchases use Robokassa. Previously created YooKassa payments continue to be checked with YooKassa.

## Server configuration

Create `data/robokassa.json` on the server (this file is ignored by Git and must never be committed):

```json
{
  "merchant_login": "STORE_LOGIN",
  "password1": "TEST_PASSWORD_1",
  "password2": "TEST_PASSWORD_2",
  "hash_algorithm": "md5",
  "is_test": true
}
```

Use the test password pair from this store's Robokassa technical settings. `hash_algorithm` must exactly match the store setting (`md5`, `sha256`, `sha384`, or `sha512`). The service creates this empty test-mode template if the file is missing; an empty configuration refuses checkout.

## Robokassa dashboard

For the test store, set the ResultURL to `https://astrosmap.ru/api/robokassa/result`, method `POST`, and the configured hash algorithm. Set the success return URL to `https://astrosmap.ru/?payment=check`; the integration also signs the per-payment `SuccessUrl2` containing the attempt identifier. Keep the YooKassa callback configured for already-created YooKassa payments.

The checkout sends receipt line items for each plan (`payment_object: service`, `tax: none`). It omits `sno`, allowing Robokassa's self-employed / NPD setup to select the applicable tax mode. A combined plan + consultation is split into two correctly priced services; consultation is marked as full prepayment. The report is marked full settlement because it is generated after purchase. The receipt total is fixed server-side and equals the charge amount. A verified account email is included when available.

## Test, then activate

With the test password pair and `is_test: true`, complete a Robokassa test payment for a report, subscription, and a plan with consultation. Confirm that each ResultURL notification activates exactly the corresponding entitlement and that the Robocheki SMZ receipt is created in «Мой налог» and delivered to the payer. Verify mismatched or unsigned callbacks do not activate purchases.

Only after the test payment and receipt checks succeed, replace the test password pair with the live pair and set `is_test` to `false`. Do not change this flag without the live credentials: Robokassa documents that an omitted/zero IsTest starts a real-payment flow.
