/** Returns a safe in-app redirect target from a `?next=` value, or the fallback.
 *  Only same-origin absolute paths are accepted (prevents open redirects). */
export function safeNext(next: string | null | undefined, allowedPrefix: string, fallback: string): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.includes('\\')) return fallback
  return next.startsWith(allowedPrefix) ? next : fallback
}
