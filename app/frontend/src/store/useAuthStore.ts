import { create } from 'zustand'
import type { AuthUser } from '@/lib/types'
import { apiGet, apiPost, ApiError } from '@/lib/apiClient'
import { tokenStorage } from '@/lib/tokenStorage'
import { disconnectRealtime } from '@/lib/realtime'

export type Portal = 'admin' | 'portal'

interface BackendUserInfo {
  id: string
  email: string
  mobile?: string
  firstName: string
  lastName?: string
  profilePhotoUrl?: string
  status: string
  roles: string[]
  /** "MODULE:ACTION" codes granted by custom roles (empty for built-in roles). */
  permissions?: string[]
  emailVerified: boolean
  mobileVerified: boolean
  /** Only present on GET /users/me (UserProfile), not on the login/register
   *  AuthResponse (UserInfo) — optional here to cover both call sites. */
  marketingOptIn?: boolean
}

interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: BackendUserInfo
}

function portalForRoles(roles: string[]): Portal {
  return roles.includes('CLIENT') && roles.length === 1 ? 'portal' : 'admin'
}

function toAuthUser(u: BackendUserInfo): AuthUser {
  return {
    id: u.id,
    firstName: u.firstName,
    lastName: u.lastName ?? '',
    email: u.email,
    mobile: u.mobile ?? '',
    profilePhotoUrl: u.profilePhotoUrl,
    // Backend roles are plain strings (e.g. "CLIENT"); AuthUser's Role type
    // is the same set as a union rather than string, so a runtime-only
    // value from an unfamiliar role name would fail this cast loudly in
    // dev rather than silently mis-typing everyone as PHOTOGRAPHER.
    roles: u.roles as AuthUser['roles'],
    permissions: u.permissions ?? [],
    marketingOptIn: u.marketingOptIn ?? true,
  }
}

interface AuthState {
  user: AuthUser | null
  portal: Portal | null
  status: 'idle' | 'loading' | 'authenticated' | 'error'
  error: string | null
  /** Real credential login against POST /auth/login. Throws ApiError on failure. */
  login: (identifier: string, password: string) => Promise<void>
  register: (input: { email: string; password: string; firstName: string; lastName?: string; mobile: string }) => Promise<void>
  /** Restores a session from a persisted token on app load. Call once at startup. */
  hydrate: () => Promise<void>
  /** Re-reads GET /users/me so edits (name, photo, consent) show up everywhere. */
  refreshUser: () => Promise<void>
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  portal: null,
  status: 'idle',
  error: null,

  login: async (identifier: string, password: string) => {
    set({ status: 'loading', error: null })
    try {
      const data = await apiPost<AuthResponse>('/auth/login', { identifier, password, rememberMe: true }, { skipAuthRetry: true })
      tokenStorage.save(data)
      set({ user: toAuthUser(data.user), portal: portalForRoles(data.user.roles), status: 'authenticated' })
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to sign in. Please try again.'
      set({ status: 'error', error: message })
      throw err
    }
  },

  register: async (input) => {
    set({ status: 'loading', error: null })
    try {
      // POST /auth/register creates the account but does NOT return tokens (the
      // response carries null accessToken/refreshToken), so saving that response
      // stored the string "null" as the token and the new client could never get in.
      // Sign in with the credentials they just chose to get a real session.
      await apiPost('/auth/register', input, { skipAuthRetry: true })
      const data = await apiPost<AuthResponse>('/auth/login', { identifier: input.email, password: input.password, rememberMe: true }, { skipAuthRetry: true })
      tokenStorage.save(data)
      set({ user: toAuthUser(data.user), portal: 'portal', status: 'authenticated' })
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Could not create your account. Please try again.'
      set({ status: 'error', error: message })
      throw err
    }
  },

  hydrate: async () => {
    if (!tokenStorage.getAccessToken()) return
    set({ status: 'loading' })
    try {
      const me = await apiGet<BackendUserInfo>('/users/me')
      set({ user: toAuthUser(me), portal: portalForRoles(me.roles), status: 'authenticated' })
    } catch {
      tokenStorage.clear()
      set({ user: null, portal: null, status: 'idle' })
    }
  },

  refreshUser: async () => {
    try {
      const me = await apiGet<BackendUserInfo>('/users/me')
      set({ user: toAuthUser(me) })
    } catch {
      // keep the current user if the refresh fails
    }
  },

  logout: () => {
    const refreshToken = tokenStorage.getRefreshToken()
    // Best-effort — revokes the refresh token server-side (see AuthController#logout).
    // We don't await/block the UI on this or fail logout if it errors.
    if (refreshToken) apiPost('/auth/logout', { refreshToken }).catch(() => {})
    tokenStorage.clear()
    disconnectRealtime()
    set({ user: null, portal: null, status: 'idle', error: null })
  },
}))
