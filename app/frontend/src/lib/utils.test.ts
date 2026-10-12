import { describe, expect, it } from 'vitest'
import { cn, initials, ordinal, formatCurrency, formatDate } from './utils'
import { tokenStorage } from './tokenStorage'
import { localDateKey } from './dateUtils'

describe('utils', () => {
  it('cn merges conflicting tailwind classes', () => {
    expect(cn('px-2', 'px-4', undefined)).toBe('px-4')
  })
  it('initials takes at most two', () => {
    expect(initials('Anita Rao Kumar')).toBe('AR')
    expect(initials('')).toBe('')
  })
  it('ordinal handles the teens', () => {
    expect([1, 2, 3, 4, 11, 12, 13, 21, 22].map(ordinal)).toEqual(['1st', '2nd', '3rd', '4th', '11th', '12th', '13th', '21st', '22nd'])
  })
  it('formatCurrency uses rupees with no decimals', () => {
    expect(formatCurrency(1500)).toContain('1,500')
    expect(formatCurrency(1500)).toContain('₹')
  })
  it('localDateKey uses local, zero-padded parts', () => {
    expect(localDateKey(new Date(2025, 2, 5, 23, 59))).toBe('2025-03-05')
  })
})

describe('formatDate', () => {
  // Regression: an empty/invalid date used to throw a RangeError inside Intl and crash the Staff page.
  it('returns a dash instead of throwing for empty or invalid dates', () => {
    expect(formatDate('')).toBe('—')
    expect(formatDate(undefined)).toBe('—')
    expect(formatDate(null)).toBe('—')
    expect(formatDate('not-a-date')).toBe('—')
    expect(formatDate(new Date('nope'))).toBe('—')
  })
  it('still formats a valid date', () => {
    expect(formatDate('2026-10-10')).toMatch(/2026/)
  })
})

describe('tokenStorage.save', () => {
  it('never stores the literal string "null" when the server returns no tokens', () => {
    localStorage.clear()
    expect(() => tokenStorage.save({ accessToken: null, refreshToken: null, expiresIn: 900 })).toThrow()
    expect(localStorage.getItem('cameraman.accessToken')).toBeNull()
  })
  it('stores a normal token pair', () => {
    tokenStorage.save({ accessToken: 'a', refreshToken: 'r', expiresIn: 900 })
    expect(tokenStorage.getAccessToken()).toBe('a')
    expect(tokenStorage.getRefreshToken()).toBe('r')
    expect(tokenStorage.isAccessTokenExpired()).toBe(false)
  })
})
