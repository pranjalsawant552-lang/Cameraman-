// Real HTTP client for the Spring Boot backend. Every response the backend
// returns is wrapped as ApiResponse<T> (see common/response/ApiResponse.java):
//   { success, message, data, errorCode, errors, meta, timestamp }
// This module unwraps that envelope so callers just get `data` back, and
// throws a typed ApiError (carrying message + errorCode + field errors) on
// failure so UI code can show a real message instead of "[object Object]".
//
// Auth: attaches `Authorization: Bearer <accessToken>` from tokenStorage on
// every call. On a 401, it tries POST /auth/refresh exactly once using the
// stored refresh token, retries the original request, and only then gives
// up and forces a logout — concurrent requests that 401 at the same time
// share a single in-flight refresh instead of each firing their own.

import { API_BASE_URL } from './config'
import { tokenStorage } from './tokenStorage'

export class ApiError extends Error {
  status: number
  errorCode?: string
  fieldErrors?: { field: string; message: string }[]

  constructor(message: string, status: number, errorCode?: string, fieldErrors?: { field: string; message: string }[]) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.fieldErrors = fieldErrors
  }
}

interface ApiEnvelope<T> {
  success: boolean
  message?: string
  data?: T
  errorCode?: string
  errors?: { field: string; message: string }[]
}

let refreshInFlight: Promise<boolean> | null = null

async function doRefreshCall(): Promise<boolean> {
  const refreshToken = tokenStorage.getRefreshToken()
  if (!refreshToken) return false
  try {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return false
    const body = (await res.json()) as ApiEnvelope<{ accessToken: string; refreshToken: string; expiresIn: number }>
    if (!body.success || !body.data) return false
    tokenStorage.save(body.data)
    return true
  } catch {
    return false
  }
}

/**
 * Refreshes the access token. Safe against the two ways this used to log people
 * out mid-work:
 *  - concurrent calls in ONE tab share a single in-flight refresh;
 *  - concurrent refreshes across TABS are serialized with a Web Lock, and the
 *    second tab re-uses the tokens the first one just saved instead of replaying
 *    an already-rotated (now revoked) refresh token.
 */
export async function refreshAccessToken(): Promise<boolean> {
  if (!tokenStorage.getRefreshToken()) return false
  if (!refreshInFlight) {
    const tokenBefore = tokenStorage.getAccessToken()
    const run = async (): Promise<boolean> => {
      // Another tab may have refreshed while we waited for the lock.
      if (tokenStorage.getAccessToken() !== tokenBefore && !tokenStorage.isAccessTokenExpired()) return true
      if (await doRefreshCall()) return true
      // Lost a race with another tab that rotated the token first?
      return tokenStorage.getAccessToken() !== tokenBefore && !tokenStorage.isAccessTokenExpired()
    }
    const locks = typeof navigator !== 'undefined' ? (navigator as Navigator & { locks?: LockManager }).locks : undefined
    refreshInFlight = (locks ? locks.request('cameraman-token-refresh', run) : run())
      .catch(() => false)
      .finally(() => { refreshInFlight = null })
  }
  return refreshInFlight
}

function forceLogout(): never {
  tokenStorage.clear()
  // A hard redirect (rather than a store action) keeps this module free
  // of a circular dependency on useAuthStore.
  if (typeof window !== 'undefined') window.location.assign('/login?sessionExpired=1')
  throw new ApiError('Session expired', 401, 'SESSION_EXPIRED')
}

/**
 * fetch() with the Authorization header, proactive refresh when the access token
 * is (nearly) expired, and exactly one refresh-and-retry on a 401. Every request
 * path in this module goes through here — blob downloads and multipart uploads
 * used to skip the refresh and failed with "session expired" mid-session.
 */
async function authedFetch(url: string, init: RequestInit, opts: { skipAuthRetry?: boolean } = {}): Promise<Response> {
  const canRefresh = !opts.skipAuthRetry && !!tokenStorage.getRefreshToken()
  if (canRefresh && tokenStorage.getAccessToken() && tokenStorage.isAccessTokenExpired()) {
    if (!(await refreshAccessToken())) forceLogout()
  }
  const send = () => {
    const headers = new Headers(init.headers)
    const token = tokenStorage.getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    return fetch(url, { ...init, headers })
  }
  let res = await send()
  if (res.status === 401 && !opts.skipAuthRetry && tokenStorage.getAccessToken()) {
    // Expired/invalid token: try one refresh; with no refresh token (or a failed
    // refresh) the session is over, so send the user to login instead of leaving
    // every screen throwing "Unauthorized".
    if (canRefresh && (await refreshAccessToken())) res = await send()
    else forceLogout()
  }
  return res
}

interface RequestOptions {
  params?: Record<string, string | number | boolean | undefined>
  signal?: AbortSignal
  /** Set true for the two auth endpoints that must never trigger a refresh-retry loop. */
  skipAuthRetry?: boolean
}

function buildUrl(path: string, params?: RequestOptions['params']) {
  const url = new URL(`${API_BASE_URL}${path}`, window.location.origin)
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
    }
  }
  return url.toString()
}

async function request<T>(method: string, path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  const res = await authedFetch(
    buildUrl(path, options.params),
    {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: options.signal,
    },
    { skipAuthRetry: options.skipAuthRetry },
  )

  let envelope: ApiEnvelope<T> | undefined
  try {
    envelope = await res.json()
  } catch {
    // Non-JSON response (e.g. a raw PDF/blob endpoint) — caller should use
    // apiGetBlob instead for those; treat this as a hard failure otherwise.
  }

  if (!res.ok || !envelope?.success) {
    throw new ApiError(
      envelope?.message || `Request failed with status ${res.status}`,
      res.status,
      envelope?.errorCode,
      envelope?.errors,
    )
  }

  return envelope.data as T
}

export const apiGet = <T>(path: string, options?: RequestOptions) => request<T>('GET', path, undefined, options)
export const apiPost = <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('POST', path, body, options)
export const apiPut = <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('PUT', path, body, options)
export const apiPatch = <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('PATCH', path, body, options)
export const apiDelete = <T>(path: string, options?: RequestOptions) => request<T>('DELETE', path, undefined, options)

/** For binary responses (invoice PDFs etc.) which don't use the ApiResponse envelope. */
export async function apiGetBlob(path: string, options?: RequestOptions): Promise<Blob> {
  const res = await authedFetch(buildUrl(path, options?.params), { signal: options?.signal })
  if (!res.ok) throw new ApiError(`Request failed with status ${res.status}`, res.status)
  return res.blob()
}

/** For multipart/form-data uploads (CSV import, photo uploads). Deliberately
 * does NOT set Content-Type — the browser needs to set it itself so it can
 * include the multipart boundary, which we can't compute by hand. */
export async function apiPostForm<T>(path: string, form: FormData, options?: RequestOptions): Promise<T> {
  const res = await authedFetch(buildUrl(path, options?.params), { method: 'POST', body: form, signal: options?.signal })
  let envelope: ApiEnvelope<T> | undefined
  try {
    envelope = await res.json()
  } catch {
    // fall through to the error below
  }
  if (!res.ok || !envelope?.success) {
    throw new ApiError(envelope?.message || `Request failed with status ${res.status}`, res.status, envelope?.errorCode, envelope?.errors)
  }
  return envelope.data as T
}

/**
 * PUT raw bytes (a file slice) with upload progress. Uses XMLHttpRequest because fetch() cannot
 * report upload progress. Same auth behaviour as every other request here: proactive refresh when
 * the access token is (nearly) expired and exactly one refresh-and-retry on a 401. Used for the
 * chunked photo upload, where each call carries one few-MB slice of a large original.
 */
export async function apiPutBinary<T>(
  path: string,
  body: Blob,
  options: RequestOptions & { onProgress?: (loadedBytes: number) => void } = {},
): Promise<T> {
  if (tokenStorage.getRefreshToken() && tokenStorage.getAccessToken() && tokenStorage.isAccessTokenExpired()) {
    if (!(await refreshAccessToken())) forceLogout()
  }

  const attempt = () => new Promise<{ status: number; text: string }>((resolve, reject) => {
    if (options.signal?.aborted) { reject(new ApiError('Upload cancelled', 0, 'CANCELLED')); return }
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', buildUrl(path, options.params))
    xhr.setRequestHeader('Content-Type', 'application/octet-stream')
    const token = tokenStorage.getAccessToken()
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (e) => { if (e.lengthComputable) options.onProgress?.(e.loaded) }
    xhr.onload = () => resolve({ status: xhr.status, text: xhr.responseText })
    xhr.onerror = () => reject(new ApiError('Network problem while uploading', 0, 'NETWORK'))
    xhr.ontimeout = () => reject(new ApiError('The upload timed out', 0, 'NETWORK'))
    xhr.onabort = () => reject(new ApiError('Upload cancelled', 0, 'CANCELLED'))
    options.signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(body)
  })

  let res = await attempt()
  if (res.status === 401 && tokenStorage.getAccessToken()) {
    if (await refreshAccessToken()) res = await attempt()
    else forceLogout()
  }

  let envelope: ApiEnvelope<T> | undefined
  try {
    envelope = JSON.parse(res.text) as ApiEnvelope<T>
  } catch {
    // Non-JSON body (e.g. a proxy error page): reported below with the HTTP status.
  }
  if (res.status < 200 || res.status >= 300 || !envelope?.success) {
    throw new ApiError(
      envelope?.message || `Request failed with status ${res.status}`,
      res.status, envelope?.errorCode, envelope?.errors,
    )
  }
  return envelope.data as T
}
