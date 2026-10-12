// Persists the JWT pair issued by POST /auth/login (see AuthDto.AuthResponse
// on the backend) across page reloads. Kept in one small module so swapping
// the storage mechanism later (e.g. httpOnly cookie via a BFF) only touches
// this file.

const ACCESS_KEY = 'cameraman.accessToken'
const REFRESH_KEY = 'cameraman.refreshToken'
const EXPIRES_KEY = 'cameraman.accessTokenExpiresAt'

export interface TokenPair {
  accessToken: string | null | undefined
  refreshToken?: string | null
  expiresIn: number // seconds, from the backend's AuthResponse.expiresIn
}

export const tokenStorage = {
  save({ accessToken, refreshToken, expiresIn }: TokenPair) {
    // Never persist a missing token as the literal string "null"/"undefined".
    if (!accessToken) throw new Error('Sign-in did not return a session. Please try again.')
    localStorage.setItem(ACCESS_KEY, accessToken)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    else localStorage.removeItem(REFRESH_KEY)
    localStorage.setItem(EXPIRES_KEY, String(Date.now() + expiresIn * 1000))
  },
  getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_KEY)
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY)
  },
  isAccessTokenExpired(): boolean {
    const expiresAt = Number(localStorage.getItem(EXPIRES_KEY) || 0)
    // 10s safety margin so we refresh slightly before the server rejects us.
    return !expiresAt || Date.now() > expiresAt - 10_000
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(EXPIRES_KEY)
  },
}
