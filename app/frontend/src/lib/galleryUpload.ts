// Reliable photo uploads: per-file progress, automatic retry with backoff, resume, and
// duplicate skipping. Originals are uploaded untouched (full resolution, no recompression).
//
// Flow for each file, decided by the server's answer to the "start upload" call:
//   POST /galleries/{id}/photos/presigned-url  (sends a name|size|lastModified fingerprint)
//     mode EXISTS   -> the photo is already in the gallery; nothing is sent.
//     mode DIRECT   -> PUT the file straight to the storage bucket (presigned URL), then
//                      POST /galleries/photos/{photoId}/confirm-upload.
//     mode CHUNKED  -> originals live in Google Drive: send the file in ~8 MB pieces to
//                      PUT /galleries/photos/{photoId}/chunk. Each piece is retried on its
//                      own, and after any failure we ask the server how many bytes it really
//                      has and carry on from there, so a dropped connection on a 90 MB photo
//                      costs one chunk, not the whole file. The last chunk completes it.
// If a DIRECT upload cannot reach storage at all (almost always a missing bucket CORS rule) the
// file falls back to the classic multipart upload so the studio is never blocked.

import { apiDelete, apiGet, apiPost, apiPostForm, apiPut, apiPutBinary, ApiError } from './apiClient'

export const MAX_UPLOAD_BYTES = 100 * 1024 * 1024
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/tiff', 'image/heic'] as const
const EXTENSION_TYPES: Record<string, string> = {
  jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp',
  tif: 'image/tiff', tiff: 'image/tiff', heic: 'image/heic', heif: 'image/heic',
}

export type UploadState = 'queued' | 'uploading' | 'confirming' | 'done' | 'error' | 'cancelled'

export interface UploadItem {
  id: string
  file: File
  state: UploadState
  /** 0..100 */
  progress: number
  error?: string
  /** Short status line shown next to the file: "Retrying (2 of 5)...", "Already in this gallery". */
  note?: string
}

/** The MIME type to upload with. Browsers often leave File.type empty for HEIC/TIFF. */
export function resolveContentType(file: Pick<File, 'name' | 'type'>): string {
  const declared = (file.type || '').toLowerCase()
  if (declared && (ALLOWED_IMAGE_TYPES as readonly string[]).includes(declared)) return declared
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  return EXTENSION_TYPES[ext] ?? declared
}

/** Returns a user-facing reason the file can't be uploaded, or null when it is fine. */
export function validateImageFile(file: Pick<File, 'name' | 'type' | 'size'>): string | null {
  if (file.size === 0) return 'The file is empty'
  if (file.size > MAX_UPLOAD_BYTES) return 'Larger than the 100 MB limit'
  const type = resolveContentType(file)
  if (!(ALLOWED_IMAGE_TYPES as readonly string[]).includes(type)) return 'Unsupported type (use JPEG, PNG, WebP, TIFF or HEIC)'
  return null
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const v = bytes / 1024 ** i
  return `${v >= 100 || i === 0 ? Math.round(v) : v.toFixed(1)} ${units[i]}`
}

/** Runs `worker` over `items` with at most `concurrency` in flight. Never rejects: workers
 *  are expected to record their own failures. */
export async function runPool<T>(items: T[], concurrency: number, worker: (item: T) => Promise<void>): Promise<void> {
  let next = 0
  const lanes = Array.from({ length: Math.max(1, Math.min(concurrency, items.length)) }, async () => {
    while (next < items.length) {
      const item = items[next++]
      try { await worker(item) } catch { /* recorded by the worker */ }
    }
  })
  await Promise.all(lanes)
}

/** PUT a file to a presigned URL, reporting upload progress (0..100). */
export function putWithProgress(
  url: string, file: File, contentType: string,
  onProgress: (pct: number) => void, signal?: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', url)
    xhr.setRequestHeader('Content-Type', contentType)
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)) }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) { onProgress(100); resolve() }
      else reject(new ApiError(`Storage rejected the upload (status ${xhr.status})`, xhr.status, 'STORAGE_REJECTED'))
    }
    xhr.onerror = () => reject(new ApiError('Could not reach storage', 0, 'STORAGE_NETWORK'))
    xhr.onabort = () => reject(new ApiError('Upload cancelled', 0, 'CANCELLED'))
    if (signal) {
      if (signal.aborted) { reject(new ApiError('Upload cancelled', 0, 'CANCELLED')); return }
      signal.addEventListener('abort', () => xhr.abort(), { once: true })
    }
    xhr.send(file)
  })
}

async function readDimensions(file: File): Promise<{ width?: number; height?: number }> {
  // Only for reasonably small files, the server works the size out itself otherwise.
  if (file.size > 40 * 1024 * 1024 || typeof createImageBitmap !== 'function') return {}
  try {
    const bmp = await createImageBitmap(file)
    const dims = { width: bmp.width, height: bmp.height }
    bmp.close()
    return dims
  } catch {
    return {} // HEIC / TIFF aren't decodable in every browser; the server reads them
  }
}

// ---------------- retry, fingerprint, error wording ----------------

/** Identifies "the same photo" without hashing 100 MB in a phone browser. Camera files keep their
 *  name, size and modified time, so this reliably catches re-selected or re-tried photos. */
export function fingerprintOf(file: Pick<File, 'name' | 'size' | 'lastModified'>): string {
  return `${file.name}|${file.size}|${file.lastModified}`
}

/** Network blips, timeouts, throttling and server errors are worth retrying; a 4xx is not. */
export function isRetryable(err: unknown): boolean {
  if (err instanceof ApiError) {
    if (err.errorCode === 'CANCELLED') return false
    if (err.status === 0) return true
    return err.status === 408 || err.status === 425 || err.status === 429 || err.status >= 500
  }
  return err instanceof TypeError // fetch() reports a dropped connection as a TypeError
}

/** Turns any upload failure into a sentence a person can act on. */
export function describeUploadError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.errorCode === 'CANCELLED') return 'Cancelled'
    if (err.status === 0) return 'Connection lost. Check your internet and tap Retry; the upload continues where it stopped.'
    if (err.status === 413) return 'The server rejected this file as too large.'
    if (err.status === 401 || err.status === 403) return 'You no longer have permission to upload. Sign in again and retry.'
    if (err.status === 429) return 'Too many requests. Wait a moment and retry.'
    // The server's own message is already specific ("Google Drive storage is full...").
    if (err.message) return err.message
  }
  if (err instanceof TypeError) return 'Connection lost. Check your internet and tap Retry.'
  return err instanceof Error && err.message ? err.message : 'Upload failed. Please retry.'
}

const sleep = (ms: number, signal?: AbortSignal) => new Promise<void>((resolve, reject) => {
  if (signal?.aborted) { reject(new ApiError('Upload cancelled', 0, 'CANCELLED')); return }
  const timer = setTimeout(() => { signal?.removeEventListener('abort', onAbort); resolve() }, ms)
  const onAbort = () => { clearTimeout(timer); reject(new ApiError('Upload cancelled', 0, 'CANCELLED')) }
  signal?.addEventListener('abort', onAbort, { once: true })
})

/** Delay before retry number `attempt` (1-based): exponential, capped, with jitter. */
export function backoffMs(attempt: number, baseMs = 1000, maxMs = 20000, random = Math.random): number {
  return Math.min(maxMs, baseMs * 2 ** (attempt - 1)) + Math.round(random() * 400)
}

/** On a phone the connection often just goes away for a bit: wait for it instead of burning retries. */
async function waitForOnline(signal?: AbortSignal, maxWaitMs = 120000): Promise<void> {
  if (typeof navigator === 'undefined' || navigator.onLine !== false) return
  await new Promise<void>((resolve, reject) => {
    const done = () => { cleanup(); resolve() }
    const abort = () => { cleanup(); reject(new ApiError('Upload cancelled', 0, 'CANCELLED')) }
    const timer = setTimeout(done, maxWaitMs)
    const cleanup = () => {
      clearTimeout(timer)
      window.removeEventListener('online', done)
      signal?.removeEventListener('abort', abort)
    }
    window.addEventListener('online', done, { once: true })
    signal?.addEventListener('abort', abort, { once: true })
  })
}

/**
 * Runs `fn`, retrying retryable failures up to `retries` more times with backoff.
 * `onRetry` receives the attempt that is about to start (2, 3, ...) so the UI can say so.
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  opts: { retries: number; signal?: AbortSignal; onRetry?: (nextAttempt: number, total: number) => void; baseMs?: number; wait?: (ms: number, signal?: AbortSignal) => Promise<void> },
): Promise<T> {
  const wait = opts.wait ?? sleep
  for (let attempt = 1; ; attempt++) {
    try {
      return await fn()
    } catch (err) {
      if (attempt > opts.retries || !isRetryable(err) || opts.signal?.aborted) throw err
      opts.onRetry?.(attempt + 1, opts.retries + 1)
      await wait(backoffMs(attempt, opts.baseMs), opts.signal)
      await waitForOnline(opts.signal)
    }
  }
}

// ---------------- uploading ----------------

interface UploadInit {
  uploadUrl?: string
  photoId: string
  key: string
  mode?: 'DIRECT' | 'CHUNKED' | 'EXISTS'
  chunkSize?: number
  uploadedBytes?: number
}
interface ChunkResult { complete: boolean; receivedBytes: number }

export interface UploadHooks {
  onState: (s: UploadState) => void
  onProgress: (pct: number) => void
  onNote?: (note: string | undefined) => void
  signal?: AbortSignal
}

const DEFAULT_CHUNK_BYTES = 8 * 1024 * 1024
const MAX_CHUNK_FAILURES = 6
const pct = (done: number, total: number) => Math.max(0, Math.min(100, Math.round((done / total) * 100)))

/** Sends the file in pieces, resuming from whatever the server really has after any failure. */
async function uploadChunked(file: File, init: UploadInit, hooks: UploadHooks): Promise<void> {
  const chunkSize = init.chunkSize && init.chunkSize > 0 ? init.chunkSize : DEFAULT_CHUNK_BYTES
  let offset = init.uploadedBytes ?? 0
  let failures = 0
  hooks.onProgress(pct(offset, file.size))
  if (offset > 0) hooks.onNote?.('Resuming where it stopped')

  while (offset < file.size) {
    const from = offset
    const piece = file.slice(from, Math.min(from + chunkSize, file.size))
    try {
      const result = await apiPutBinary<ChunkResult>(`/galleries/photos/${init.photoId}/chunk`, piece, {
        params: { offset: from, total: file.size },
        signal: hooks.signal,
        onProgress: (loaded) => hooks.onProgress(pct(from + loaded, file.size)),
      })
      failures = 0
      hooks.onNote?.(undefined)
      if (result.complete) { offset = file.size; break }
      offset = result.receivedBytes
    } catch (err) {
      if (!isRetryable(err)) throw err
      failures++
      if (failures >= MAX_CHUNK_FAILURES) throw err
      hooks.onNote?.(`Connection problem, retrying (${failures} of ${MAX_CHUNK_FAILURES - 1})...`)
      await sleep(backoffMs(failures), hooks.signal)
      await waitForOnline(hooks.signal)
      // Ask the server what it has: the failed chunk may or may not have landed.
      const known = await withRetry(
        () => apiGet<ChunkResult>(`/galleries/photos/${init.photoId}/upload-progress`, { signal: hooks.signal }),
        { retries: 3, signal: hooks.signal },
      )
      if (known.complete) { offset = file.size; break }
      offset = known.receivedBytes
    }
  }
  hooks.onProgress(100)
}

/** Uploads one file. Calls the hooks as it moves along; throws on failure. Resolves with whether the
 *  photo was already in the gallery (so nothing had to be sent). */
export async function uploadFileDirect(
  galleryId: string, file: File, hooks: UploadHooks, restarted = false,
): Promise<{ duplicate: boolean }> {
  const contentType = resolveContentType(file)
  const problem = validateImageFile(file)
  if (problem) throw new ApiError(problem, 400, 'INVALID_FILE')
  const fingerprint = fingerprintOf(file)
  const retryNote = (next: number, total: number) => hooks.onNote?.(`Retrying (${next} of ${total})...`)

  hooks.onState('uploading')
  const init = await withRetry(
    () => apiPost<UploadInit>(`/galleries/${galleryId}/photos/presigned-url`, undefined, {
      params: { filename: file.name, contentType, size: file.size, fingerprint },
      signal: hooks.signal,
    }),
    { retries: 3, signal: hooks.signal, onRetry: retryNote },
  )
  const mode = init.mode ?? (init.uploadUrl ? 'DIRECT' : 'EXISTS')

  if (mode === 'EXISTS') {
    hooks.onProgress(100)
    hooks.onNote?.('Already in this gallery')
    return { duplicate: true }
  }

  if (mode === 'CHUNKED') {
    try {
      await uploadChunked(file, init, hooks)
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'CANCELLED') {
        void apiDelete(`/galleries/photos/${init.photoId}`).catch(() => undefined)
        throw err
      }
      // The storage session expired (a long pause): start over once with a fresh one.
      if (err instanceof ApiError && err.errorCode === 'UPLOAD_SESSION_EXPIRED' && !restarted) {
        hooks.onProgress(0)
        hooks.onNote?.('Restarting upload')
        return uploadFileDirect(galleryId, file, hooks, true)
      }
      throw err
    }
    hooks.onNote?.(undefined)
    return { duplicate: false }
  }

  // DIRECT: straight to the bucket with the presigned URL.
  try {
    await withRetry(
      () => putWithProgress(init.uploadUrl as string, file, contentType, hooks.onProgress, hooks.signal),
      { retries: 2, signal: hooks.signal, onRetry: retryNote },
    )
  } catch (err) {
    if (err instanceof ApiError && err.errorCode === 'CANCELLED') {
      void apiDelete(`/galleries/photos/${init.photoId}`).catch(() => undefined)
      throw err
    }
    if (err instanceof ApiError && err.errorCode === 'STORAGE_NETWORK') {
      // Most likely the bucket has no CORS rule for this site. Fall back to a normal upload.
      void apiDelete(`/galleries/photos/${init.photoId}`).catch(() => undefined)
      hooks.onProgress(0)
      hooks.onNote?.('Using the standard upload')
      const form = new FormData()
      form.append('file', file)
      await apiPostForm(`/galleries/${galleryId}/photos/upload-single`, form, { signal: hooks.signal, params: { fingerprint } })
      hooks.onProgress(100)
      hooks.onNote?.(undefined)
      return { duplicate: false }
    }
    throw err
  }

  hooks.onState('confirming')
  hooks.onNote?.(undefined)
  const dims = await readDimensions(file)
  await withRetry(
    () => apiPost(`/galleries/photos/${init.photoId}/confirm-upload`, undefined, { params: dims, signal: hooks.signal }),
    { retries: 3, signal: hooks.signal },
  )
  return { duplicate: false }
}

/** Uploads many files with a small concurrency, updating `onItem` for every change. */
export async function uploadFilesDirect(
  galleryId: string, items: UploadItem[],
  onItem: (id: string, patch: Partial<UploadItem>) => void,
  opts: { concurrency?: number; signal?: AbortSignal } = {},
): Promise<{ succeeded: number; failed: number; duplicates: number }> {
  let succeeded = 0
  let failed = 0
  let duplicates = 0
  await runPool(items, opts.concurrency ?? 3, async (item) => {
    if (opts.signal?.aborted) { onItem(item.id, { state: 'cancelled' }); return }
    try {
      const { duplicate } = await uploadFileDirect(galleryId, item.file, {
        signal: opts.signal,
        onState: (state) => onItem(item.id, { state }),
        onProgress: (progress) => onItem(item.id, { progress }),
        onNote: (note) => onItem(item.id, { note }),
      })
      if (duplicate) duplicates++
      else succeeded++
      onItem(item.id, { state: 'done', progress: 100, error: undefined })
    } catch (err) {
      if (err instanceof ApiError && err.errorCode === 'CANCELLED') { onItem(item.id, { state: 'cancelled', note: undefined }); return }
      failed++
      onItem(item.id, { state: 'error', error: describeUploadError(err), note: undefined })
      throw err
    }
  })
  return { succeeded, failed, duplicates }
}

// ---------------- processing status, Drive import, originals expiry ----------------

export interface ImportProgress { running: boolean; total: number; done: number; failed: number; skipped?: number }
export interface UploadStatus {
  uploading: number; processing: number; ready: number; failed: number; driveImport?: ImportProgress
}

export const fetchUploadStatus = (galleryId: string) =>
  apiGet<UploadStatus>(`/galleries/${galleryId}/photos/upload-status`)

export const retryFailedPhotos = (galleryId: string) =>
  apiPost<number>(`/galleries/${galleryId}/photos/retry-failed`)

export const deleteGalleryPhoto = (photoId: string) => apiDelete<void>(`/galleries/photos/${photoId}`)

export const fetchDriveImportConfigured = () =>
  apiGet<{ configured: boolean }>('/galleries/drive-import/status').then((r) => r.configured)

export const startDriveImport = (galleryId: string, driveFolderUrl: string) =>
  apiPost<{ filesQueued: number }>(`/galleries/${galleryId}/photos/import-from-drive`, { driveFolderUrl })


/** days = null clears the auto-delete window. */
export const setOriginalsExpiry = (galleryId: string, days: number | null) =>
  apiPut<unknown>(`/galleries/${galleryId}/originals-expiry`, { days })

export const forcePurgeOriginals = (galleryId: string, confirmNoSelection: boolean) =>
  apiPost<{ purgedCount: number; purgedAt: string }>(`/galleries/${galleryId}/originals-expiry/force`, { confirmNoSelection })
