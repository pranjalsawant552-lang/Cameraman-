import { useEffect, useState } from 'react'
import { Bell, KeyRound, LogOut, User } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Switch, Avatar } from '@/components/ui/primitives'
import { Input } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { ImageField } from '@/components/shared/ImageField'
import { useAuthStore } from '@/store/useAuthStore'
import { useToastStore } from '@/store/useToastStore'
import { changePassword, fetchNotificationPreferences, logoutAllDevices, setMarketingPreference, setNotificationPreference, updateMyProfile, type NotificationPreferenceView } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'

const MOBILE_RE = /^[6-9]\d{9}$/
const msg = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback)

/** Profile, consent, password and session controls, shared by the client portal and the admin panel. */
/** `allowPhotoUpload` is staff-only: the upload endpoint (/cms/media/upload) is an admin API. */
export function AccountSettings({ showMarketing = true, allowPhotoUpload = false }: { showMarketing?: boolean; allowPhotoUpload?: boolean }) {
  const { user, refreshUser, logout } = useAuthStore()
  const push = useToastStore((s) => s.push)

  const [profile, setProfile] = useState({ firstName: user?.firstName ?? '', lastName: user?.lastName ?? '', mobile: user?.mobile ?? '', profilePhotoUrl: user?.profilePhotoUrl ?? '' })
  const [profileError, setProfileError] = useState<string | null>(null)
  const [savingProfile, setSavingProfile] = useState(false)

  const [optIn, setOptIn] = useState(user?.marketingOptIn ?? true)
  const [savingConsent, setSavingConsent] = useState(false)

  const [pw, setPw] = useState({ current: '', next: '', confirm: '' })
  const [pwError, setPwError] = useState<string | null>(null)
  const [savingPw, setSavingPw] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const [prefs, setPrefs] = useState<NotificationPreferenceView[] | null>(null)
  const [prefsError, setPrefsError] = useState<string | null>(null)

  useEffect(() => {
    fetchNotificationPreferences().then(setPrefs).catch((e) => setPrefsError(msg(e, 'Could not load your notification settings.')))
  }, [])

  async function togglePref(category: string, enabled: boolean) {
    setPrefs((all) => all?.map((p) => (p.category === category ? { ...p, enabled } : p)) ?? null)
    try {
      await setNotificationPreference(category, enabled)
      if (category === 'MARKETING') { setOptIn(enabled); await setMarketingPreference(enabled); await refreshUser() }
    } catch (e) {
      setPrefs((all) => all?.map((p) => (p.category === category ? { ...p, enabled: !enabled } : p)) ?? null)
      push(msg(e, 'Could not save that setting.'))
    }
  }

  async function saveProfile() {
    setProfileError(null)
    if (!profile.firstName.trim()) return setProfileError('First name is required.')
    if (profile.mobile && !MOBILE_RE.test(profile.mobile.trim())) return setProfileError('Enter a valid 10-digit Indian mobile number.')
    setSavingProfile(true)
    try {
      await updateMyProfile({
        firstName: profile.firstName.trim(), lastName: profile.lastName.trim(),
        mobile: profile.mobile.trim() || undefined, profilePhotoUrl: profile.profilePhotoUrl || undefined,
      })
      await refreshUser()
      push('Profile updated')
    } catch (e) {
      setProfileError(msg(e, 'Could not save your profile.'))
    } finally { setSavingProfile(false) }
  }

  async function toggleConsent(v: boolean) {
    const previous = optIn
    setOptIn(v); setSavingConsent(true)
    try {
      await setMarketingPreference(v)
      await refreshUser()
      push(v ? "You'll now receive reminders and suggestions" : "You've been opted out of reminders and suggestions")
    } catch (e) {
      setOptIn(previous)
      push(msg(e, 'Could not update your preference.'))
    } finally { setSavingConsent(false) }
  }

  async function savePassword() {
    setPwError(null)
    if (!pw.current) return setPwError('Enter your current password.')
    if (pw.next.length < 8) return setPwError('The new password must be at least 8 characters.')
    if (pw.next.length > 100) return setPwError('The new password is too long (100 characters max).')
    if (pw.next === pw.current) return setPwError('Choose a password different from your current one.')
    if (pw.next !== pw.confirm) return setPwError('The new passwords do not match.')
    setSavingPw(true)
    try {
      await changePassword(pw.current, pw.next, pw.confirm)
      push('Password changed. Please sign in again.')
      // The server revokes all refresh tokens on a password change, so end this session cleanly.
      setTimeout(() => { logout(); window.location.assign('/login') }, 800)
    } catch (e) {
      setPwError(msg(e, 'Could not change your password.'))
    } finally { setSavingPw(false) }
  }

  async function signOutEverywhere() {
    setSigningOut(true)
    try {
      await logoutAllDevices()
    } catch { /* even if the call fails, end this session */ }
    logout()
    window.location.assign('/login')
  }

  return (
    <div className="max-w-2xl space-y-5">
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><User className="h-4.5 w-4.5 text-accent" /> Your details</CardTitle></CardHeader>
        <CardContent className="pt-3">
          <div className="mb-5 flex items-center gap-4">
            <Avatar src={profile.profilePhotoUrl || undefined} name={`${profile.firstName} ${profile.lastName}`} size={64} />
            {allowPhotoUpload && <div className="flex-1"><ImageField label="Profile photo" value={profile.profilePhotoUrl} onChange={(url) => setProfile((p) => ({ ...p, profilePhotoUrl: url }))} /></div>}
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input id="acc-first" label="First name" maxLength={60} value={profile.firstName} onChange={(e) => setProfile({ ...profile, firstName: e.target.value })} autoComplete="given-name" />
            <Input id="acc-last" label="Last name" maxLength={60} value={profile.lastName} onChange={(e) => setProfile({ ...profile, lastName: e.target.value })} autoComplete="family-name" />
            <Input id="acc-email" label="Email (contact the studio to change)" value={user?.email ?? ''} readOnly disabled />
            <Input id="acc-mobile" label="Mobile" inputMode="numeric" maxLength={10} value={profile.mobile} onChange={(e) => setProfile({ ...profile, mobile: e.target.value.replace(/\D/g, '') })} autoComplete="tel-national" />
          </div>
          {profileError && <p role="alert" className="mt-4 text-sm text-danger">{profileError}</p>}
        </CardContent>
        <div className="flex justify-end px-6 pb-6"><Button onClick={saveProfile} loading={savingProfile}>Save changes</Button></div>
      </Card>

      {showMarketing && (
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2"><Bell className="h-4.5 w-4.5 text-accent" /> Reminders &amp; suggestions</CardTitle></CardHeader>
          <CardContent className="pt-3">
            <div className="flex items-start justify-between gap-4 rounded-[var(--radius-md)] border border-border p-4">
              <div>
                <p className="text-sm font-medium text-ink">Stay in touch for reminders</p>
                <p className="mt-1 text-sm text-ink-muted">
                  We'll occasionally reach out with birthday shoot reminders, anniversary session suggestions, offers or a note
                  asking how your photos turned out. Turn this off any time. It never affects booking confirmations,
                  payment receipts or delivery notifications, which we always send.
                </p>
              </div>
              <Switch checked={optIn} onChange={toggleConsent} disabled={savingConsent} label="Receive reminders and suggestions" />
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><Bell className="h-4.5 w-4.5 text-accent" /> Notifications</CardTitle></CardHeader>
        <CardContent className="space-y-2 pt-3">
          {prefsError && <p role="alert" className="text-sm text-danger">{prefsError}</p>}
          {!prefs && !prefsError && <p className="text-sm text-ink-muted">Loading…</p>}
          {prefs?.filter((p) => showMarketing || p.category !== 'MARKETING').map((p) => (
            <div key={p.category} className="flex items-center justify-between gap-4 rounded-[var(--radius-md)] border border-border px-4 py-3">
              <div>
                <p className="text-sm font-medium text-ink">{p.label}{p.mandatory && <span className="ml-2 text-xs font-normal text-ink-faint">always on</span>}</p>
                <p className="text-xs text-ink-muted">{p.description}</p>
              </div>
              <Switch checked={p.enabled} disabled={p.mandatory} onChange={(v) => togglePref(p.category, v)} label={p.label} />
            </div>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><KeyRound className="h-4.5 w-4.5 text-accent" /> Password &amp; sessions</CardTitle></CardHeader>
        <CardContent className="space-y-4 pt-3">
          <Input id="pw-current" label="Current password" type="password" autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input id="pw-new" label="New password (8+ characters)" type="password" autoComplete="new-password" maxLength={100} value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />
            <Input id="pw-confirm" label="Confirm new password" type="password" autoComplete="new-password" maxLength={100} value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />
          </div>
          {pwError && <p role="alert" className="text-sm text-danger">{pwError}</p>}
          <div className="flex flex-wrap justify-between gap-2">
            <Button variant="outline" onClick={signOutEverywhere} loading={signingOut}><LogOut className="h-4 w-4" /> Log out of all devices</Button>
            <Button onClick={savePassword} loading={savingPw}>Change password</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
