import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './apiClient'
import { backoffMs, describeUploadError, fingerprintOf, formatBytes, isRetryable, MAX_UPLOAD_BYTES, resolveContentType, runPool, validateImageFile, withRetry } from './galleryUpload'

describe('resolveContentType', () => {
  it('keeps a declared allowed type', () => {
    expect(resolveContentType({ name: 'a.jpg', type: 'image/jpeg' })).toBe('image/jpeg')
  })
  it('falls back to the extension when the browser leaves the type blank (HEIC, TIFF)', () => {
    expect(resolveContentType({ name: 'IMG_1.HEIC', type: '' })).toBe('image/heic')
    expect(resolveContentType({ name: 'scan.tif', type: '' })).toBe('image/tiff')
    expect(resolveContentType({ name: 'x.heif', type: '' })).toBe('image/heic')
  })
  it('returns the declared type for things we do not know', () => {
    expect(resolveContentType({ name: 'notes.pdf', type: 'application/pdf' })).toBe('application/pdf')
  })
})

describe('validateImageFile', () => {
  it('accepts a normal photo', () => {
    expect(validateImageFile({ name: 'a.jpg', type: 'image/jpeg', size: 2_000_000 })).toBeNull()
  })
  it('accepts a HEIC with no declared type', () => {
    expect(validateImageFile({ name: 'a.heic', type: '', size: 1000 })).toBeNull()
  })
  it('rejects empty files, oversize files and other types', () => {
    expect(validateImageFile({ name: 'a.jpg', type: 'image/jpeg', size: 0 })).toMatch(/empty/i)
    expect(validateImageFile({ name: 'a.jpg', type: 'image/jpeg', size: MAX_UPLOAD_BYTES + 1 })).toMatch(/100 MB/)
    expect(validateImageFile({ name: 'a.pdf', type: 'application/pdf', size: 10 })).toMatch(/Unsupported/)
  })
})

describe('formatBytes', () => {
  it('formats across units', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(formatBytes(NaN)).toBe('0 B')
  })
})

describe('runPool', () => {
  it('never runs more than the limit at once and finishes every item', async () => {
    let active = 0, peak = 0
    const done: number[] = []
    await runPool([1, 2, 3, 4, 5, 6, 7], 3, async (n) => {
      active++; peak = Math.max(peak, active)
      await new Promise((r) => setTimeout(r, 5))
      done.push(n); active--
    })
    expect(peak).toBeLessThanOrEqual(3)
    expect(done.sort()).toEqual([1, 2, 3, 4, 5, 6, 7])
  })
  it('keeps going when a worker throws', async () => {
    const seen = vi.fn()
    await runPool([1, 2, 3], 2, async (n) => { seen(n); if (n === 2) throw new Error('boom') })
    expect(seen).toHaveBeenCalledTimes(3)
  })
  it('handles an empty list', async () => {
    await expect(runPool([], 3, async () => undefined)).resolves.toBeUndefined()
  })
})

describe('fingerprintOf', () => {
  it('is stable for the same file and differs when anything changes', () => {
    const a = { name: 'IMG_1.jpg', size: 5, lastModified: 100 }
    expect(fingerprintOf(a)).toBe('IMG_1.jpg|5|100')
    expect(fingerprintOf({ ...a, size: 6 })).not.toBe(fingerprintOf(a))
    expect(fingerprintOf({ ...a, lastModified: 101 })).not.toBe(fingerprintOf(a))
  })
})

describe('isRetryable', () => {
  it('retries network failures, throttling and server errors only', () => {
    expect(isRetryable(new ApiError('x', 0, 'NETWORK'))).toBe(true)
    expect(isRetryable(new ApiError('x', 503))).toBe(true)
    expect(isRetryable(new ApiError('x', 429))).toBe(true)
    expect(isRetryable(new ApiError('x', 400, 'INVALID_FILE_TYPE'))).toBe(false)
    expect(isRetryable(new ApiError('x', 404))).toBe(false)
    expect(isRetryable(new ApiError('x', 0, 'CANCELLED'))).toBe(false)
    expect(isRetryable(new TypeError('Failed to fetch'))).toBe(true)
  })
})

describe('describeUploadError', () => {
  it('gives actionable wording', () => {
    expect(describeUploadError(new ApiError('x', 0, 'NETWORK'))).toMatch(/internet/i)
    expect(describeUploadError(new ApiError('x', 413))).toMatch(/too large/i)
    expect(describeUploadError(new ApiError('Google Drive storage is full.', 500, 'STORAGE_ERROR'))).toBe('Google Drive storage is full.')
  })
})

describe('withRetry', () => {
  const noWait = () => Promise.resolve()
  it('retries a retryable failure then succeeds', async () => {
    const fn = vi.fn().mockRejectedValueOnce(new ApiError('x', 503)).mockResolvedValue('ok')
    const onRetry = vi.fn()
    await expect(withRetry(fn, { retries: 3, wait: noWait, onRetry })).resolves.toBe('ok')
    expect(fn).toHaveBeenCalledTimes(2)
    expect(onRetry).toHaveBeenCalledWith(2, 4)
  })
  it('does not retry a non-retryable error', async () => {
    const fn = vi.fn().mockRejectedValue(new ApiError('bad', 400))
    await expect(withRetry(fn, { retries: 3, wait: noWait })).rejects.toThrow('bad')
    expect(fn).toHaveBeenCalledTimes(1)
  })
  it('gives up after the retry budget', async () => {
    const fn = vi.fn().mockRejectedValue(new ApiError('down', 502))
    await expect(withRetry(fn, { retries: 2, wait: noWait })).rejects.toThrow('down')
    expect(fn).toHaveBeenCalledTimes(3)
  })
})

describe('backoffMs', () => {
  it('grows exponentially and is capped', () => {
    const zero = () => 0
    expect(backoffMs(1, 1000, 20000, zero)).toBe(1000)
    expect(backoffMs(3, 1000, 20000, zero)).toBe(4000)
    expect(backoffMs(10, 1000, 20000, zero)).toBe(20000)
  })
})
