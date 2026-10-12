import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchStudioSettings } from '@/lib/api'
import { refreshAccessToken } from '@/lib/apiClient'
import { tokenStorage } from '@/lib/tokenStorage'
import { useAuthStore } from '@/store/useAuthStore'
import { useToastStore } from '@/store/useToastStore'

const ACTIVITY_KEY = 'cameraman.lastActivityAt'
const TIMEOUT_KEY = 'cameraman.idleTimeoutMinutes'
const DEFAULT_TIMEOUT_MIN = 30
const CHECK_EVERY_MS = 15_000
const WRITE_THROTTLE_MS = 5_000
const WARN_BEFORE_MS = 60_000
const REFRESH_WHEN_LEFT_MS = 2 * 60_000

function readTimeoutMinutes(): number {
  const raw = localStorage.getItem(TIMEOUT_KEY)
  const n = raw === null ? NaN : Number(raw)
  return Number.isFinite(n) && n >= 0 ? n : DEFAULT_TIMEOUT_MIN
}
function lastActivity(): number {
  return Number(localStorage.getItem(ACTIVITY_KEY)) || Date.now()
}

/**
 * Signs the user out after `sessionIdleTimeoutMinutes` (Settings → Session) of no
 * mouse/keyboard/touch/scroll activity in ANY tab. While the user IS active it also
 * keeps the access token fresh in the background, so a long working session never
 * hits a mid-task "session expired".
 */
export function useIdleLogout() {
  const navigate = useNavigate()
  const logout = useAuthStore((s) => s.logout)
  const push = useToastStore((s) => s.push)
  const warned = useRef(false)

  useEffect(() => {
    localStorage.setItem(ACTIVITY_KEY, String(Date.now()))
    let lastWrite = 0
    let stopped = false

    // Pick up the configured timeout (cached so it also applies right after a reload).
    fetchStudioSettings()
      .then((s) => {
        if (typeof s.sessionIdleTimeoutMinutes === 'number') localStorage.setItem(TIMEOUT_KEY, String(s.sessionIdleTimeoutMinutes))
      })
      .catch(() => {})

    function markActive() {
      const now = Date.now()
      if (now - lastWrite < WRITE_THROTTLE_MS) return
      lastWrite = now
      localStorage.setItem(ACTIVITY_KEY, String(now))
      warned.current = false
    }

    async function check() {
      if (stopped) return
      const timeoutMin = readTimeoutMinutes()
      const idleFor = Date.now() - lastActivity()

      if (timeoutMin > 0) {
        const limit = timeoutMin * 60_000
        if (idleFor >= limit) {
          stopped = true
          logout()
          navigate('/login?idle=1', { replace: true })
          return
        }
        if (limit - idleFor <= WARN_BEFORE_MS && !warned.current) {
          warned.current = true
          push('You will be signed out in about a minute due to inactivity. Move the mouse or press a key to stay signed in.', 'info')
        }
      }

      // Active user + token about to expire → refresh quietly.
      const stillActive = timeoutMin === 0 || idleFor < timeoutMin * 60_000
      const expiresAt = Number(localStorage.getItem('cameraman.accessTokenExpiresAt') || 0)
      if (stillActive && tokenStorage.getRefreshToken() && expiresAt && expiresAt - Date.now() < REFRESH_WHEN_LEFT_MS) {
        await refreshAccessToken()
      }
    }

    const events: (keyof WindowEventMap)[] = ['mousedown', 'mousemove', 'keydown', 'touchstart', 'scroll', 'wheel', 'click']
    events.forEach((e) => window.addEventListener(e, markActive, { passive: true }))
    const onVisible = () => { if (document.visibilityState === 'visible') void check() }
    document.addEventListener('visibilitychange', onVisible)
    const id = window.setInterval(() => void check(), CHECK_EVERY_MS)
    void check()

    return () => {
      stopped = true
      events.forEach((e) => window.removeEventListener(e, markActive))
      document.removeEventListener('visibilitychange', onVisible)
      window.clearInterval(id)
    }
  }, [logout, navigate, push])
}
