// Studio branding constants. These are legitimate configuration (not demo
// data): the studio name is used in page titles, auth screens and the
// public marketing site. Override it per-deployment by setting
// VITE_STUDIO_NAME in frontend/.env — see .env.example.
export const STUDIO_NAME = (import.meta.env.VITE_STUDIO_NAME as string | undefined)?.trim() || 'Cameraman Photo Studio'
