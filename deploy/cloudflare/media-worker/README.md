# Casamento Media Worker

This Worker is the only public route to the private R2 bucket. It accepts the signed URLs produced by `R2StorageService`; the HMAC payload is `object-key + "\n" + unix-expiry-seconds`.

Before deployment, replace `bucket_name` in `wrangler.toml` and configure the Worker route to the value of `MEDIA_DELIVERY_BASE_URL`, for example `media.example.com/*`.

```bash
npx wrangler login
npx wrangler secret put MEDIA_DELIVERY_SIGNING_KEY
npx wrangler r2 bucket cors set casamento-media-prod --file r2-cors.production.json
npx wrangler deploy
```

Keep the same random secret in the Worker and `MEDIA_DELIVERY_SIGNING_KEY` on the VPS. The R2 bucket must remain private; do not enable its `r2.dev` URL or a public custom domain.

The browser uploads to the signed S3 PUT URL, not this Worker. Replace the domain and bucket name in `r2-cors.production.json` before applying it; the policy permits only `PUT` from the frontend origin with `Content-Type`.

For local direct-upload tests, merge `http://localhost:5173` into the production CORS policy or temporarily apply `r2-cors.development.json`. The frontend intentionally uses the legacy proxied upload only in Vite development mode, so routine local testing does not require modifying the production bucket CORS policy.