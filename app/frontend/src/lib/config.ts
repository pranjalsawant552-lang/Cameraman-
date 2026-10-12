// Central place that resolves where the app's Spring Boot backend lives.
//
// Set VITE_API_BASE_URL in frontend/.env before building for production,
// e.g.:
//   VITE_API_BASE_URL=https://api.yourstudio.com/api
//
// This app has no demo/mock-data mode — every screen talks to the real
// backend. If VITE_API_BASE_URL isn't set, App.tsx shows a "backend not
// configured" screen instead of the app, rather than silently falling back
// to fabricated data. See frontend/.env.example and the deployment guide
// in DEPLOYMENT.md.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') || ''

/** True once a backend URL has been configured. Used by App.tsx to decide
 *  whether to render the app or a setup screen. */
export const BACKEND_CONFIGURED = API_BASE_URL.length > 0

if (import.meta.env.DEV && !BACKEND_CONFIGURED) {
  // eslint-disable-next-line no-console
  console.warn('[cameraman] VITE_API_BASE_URL is not set — the app will show a setup screen until it is configured.')
}
