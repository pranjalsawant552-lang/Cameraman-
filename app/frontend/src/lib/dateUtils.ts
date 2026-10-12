/** Local (browser-timezone) YYYY-MM-DD key — deliberately not toISOString(),
 *  which is UTC and would silently shift the date by one in most timezones. */
export function localDateKey(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Today's date as a local YYYY-MM-DD key. Replaces the old fixed demo
 *  anchor date (ATTENDANCE_TODAY) that used to make "today" always show
 *  2026-09-04 regardless of when the app was actually opened. */
export function todayLocalDateKey(): string {
  return localDateKey(new Date())
}
