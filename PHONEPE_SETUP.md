# PhonePe setup

TexOS uses PhonePe Standard Checkout on the backend. Credentials are never sent
to the browser. Restaurant customer payments and TexOS subscription payments
use separate merchant accounts and configuration.

## Restaurant customer payments

1. Ask PhonePe to approve/provision the restaurant-wise merchant or partner
   integration that allows each restaurant to receive settlement directly in
   its own account. TexOS must not collect or route these funds.
2. Keep `TEXOS_PHONEPE_RESTAURANT_MERCHANTS_ENABLED=false` until PhonePe
   confirms that model and provides the account-specific credentials. The
   customer online-payment option remains unavailable while it is false.
3. Generate `TEXOS_PHONEPE_CREDENTIAL_ENCRYPTION_KEY` locally:

   ```powershell
   node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
   ```

   Put the resulting 32-byte Base64 key in `TexOS-backend/.env`. Back it up
   securely; changing it later makes already-stored merchant secrets unreadable.
4. In Owner Dashboard → Billing → PhonePe settings, enter that restaurant's
   PhonePe merchant ID, client ID/version, client secret, environment, webhook
   key ID, and webhook secret. Secrets are encrypted before database storage.
5. Set `TEXOS_PHONEPE_PARTNER_INTEGRATION=true` only if PhonePe provisions
   TexOS as a partner and directs TexOS to send the partner merchant header.
6. Configure PhonePe webhooks to call
   `https://<your-backend-host>/api/webhooks/phonepe` and provide the matching
   webhook key ID/secret in the restaurant's settings.

Do not enable restaurant payments or use a workaround that settles through the
TexOS account without PhonePe's explicit approval.

## TexOS subscription payments

Put the following separate TexOS merchant credentials in
`TexOS-backend/.env`:

- `TEXOS_PHONEPE_SUBSCRIPTIONS_CLIENT_ID`
- `TEXOS_PHONEPE_SUBSCRIPTIONS_CLIENT_SECRET`
- `TEXOS_PHONEPE_SUBSCRIPTIONS_CLIENT_VERSION`
- `TEXOS_PHONEPE_SUBSCRIPTIONS_MERCHANT_ID`
- `TEXOS_PHONEPE_SUBSCRIPTIONS_ENVIRONMENT` (`SANDBOX` or `PRODUCTION`)
- `TEXOS_PHONEPE_SUBSCRIPTIONS_WEBHOOK_KEY_ID`
- `TEXOS_PHONEPE_SUBSCRIPTIONS_WEBHOOK_SECRET`

Configure that merchant's webhook to the same `/api/webhooks/phonepe` endpoint.
Never reuse the SaaS credentials for restaurant payments or put credentials in
frontend environment variables.

Restaurant owners enter their own restaurant's separate PhonePe credentials
under Billing in the dashboard. Those are encrypted in the database; saving
them does not edit `.env` or change the TexOS subscription merchant settings.

## Database and verification

Flyway applies the payment and receipt migrations on backend startup. Payments
are confirmed using PhonePe's server-side order status; browser redirects alone
do not mark a payment successful. Keep the backend publicly reachable over
HTTPS for webhooks and reconciliation.
