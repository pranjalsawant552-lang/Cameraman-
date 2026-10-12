import type { SlotAvailability } from './types'

/** Backend LocalTime may arrive as "10:00:00"; the UI works in "HH:mm". */
export const hhmm = (t?: string) => (t ? t.slice(0, 5) : '')

export function addHours(time: string, hours: number): string {
  const [h, m] = hhmm(time).split(':').map(Number)
  const total = h * 60 + m + Math.round(hours * 60)
  const hh = Math.min(23, Math.floor(total / 60))
  const mm = total % 60
  return `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`
}

export function toIsoDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function parseIsoDate(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/**
 * A start slot is bookable only if it AND the following (hours-1) slots are free,
 * because the shoot occupies a continuous block. Returns the set of valid start times.
 */
export function bookableStarts(slots: SlotAvailability[], hours: number): Set<string> {
  const need = Math.max(1, Math.ceil(hours))
  const out = new Set<string>()
  for (let i = 0; i + need <= slots.length; i++) {
    let ok = true
    for (let j = 0; j < need; j++) {
      const s = slots[i + j]
      if (!s.available) { ok = false; break }
      if (j > 0 && hhmm(s.startTime) !== hhmm(slots[i + j - 1].endTime)) { ok = false; break }
    }
    if (ok) out.add(hhmm(slots[i].startTime))
  }
  return out
}

/** 6-row month grid starting Monday; null cells pad the edges. */
export function monthCells(year: number, month: number): (Date | null)[] {
  const first = new Date(year, month, 1)
  const lead = (first.getDay() + 6) % 7
  const days = new Date(year, month + 1, 0).getDate()
  const cells: (Date | null)[] = Array(lead).fill(null)
  for (let d = 1; d <= days; d++) cells.push(new Date(year, month, d))
  while (cells.length % 7 !== 0) cells.push(null)
  return cells
}

export function categoryLabel(c: string): string {
  return c.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (x) => x.toUpperCase())
}
