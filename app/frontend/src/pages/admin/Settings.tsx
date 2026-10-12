import { useEffect, useState } from 'react'
import { Check, Palette, Plus, Trash2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, Switch } from '@/components/ui/primitives'
import { Input, Select } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { THEMES, useThemeStore } from '@/store/useThemeStore'
import { cn } from '@/lib/utils'
import { fetchStudioSettings, updateStudioSettings, parseAboutStats, parseServices, type StudioSettings, type AboutStat, type PublicService } from '@/lib/api'
import { ImageField } from '@/components/shared/ImageField'
import { SERVICE_ICON_OPTIONS, resolveServiceIcon } from '@/lib/serviceIcons'
import { useToastStore } from '@/store/useToastStore'
import { ApiError } from '@/lib/apiClient'
import { AccountSettings } from '@/components/account/AccountSettings'

export default function AdminSettings() {
  const { theme, setTheme } = useThemeStore()
  const push = useToastStore((s) => s.push)
  const [notifPrefs, setNotifPrefs] = useState({ bookings: true, payments: true, lowStock: true, suggestions: true })

  const [settings, setSettings] = useState<StudioSettings>({})
  const [loadingSettings, setLoadingSettings] = useState(true)
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPayment, setSavingPayment] = useState(false)
  const [savingSession, setSavingSession] = useState(false)
  const [savingPublic, setSavingPublic] = useState(false)
  const [aboutStats, setAboutStats] = useState<AboutStat[]>([])
  const [services, setServices] = useState<PublicService[]>([])

  useEffect(() => {
    fetchStudioSettings()
      .then((s) => {
        setSettings(s)
        setAboutStats(parseAboutStats(s.aboutStatsJson))
        setServices(parseServices(s.servicesJson))
      })
      .catch((err) => push(err instanceof ApiError ? err.message : 'Could not load studio settings.'))
      .finally(() => setLoadingSettings(false))
  }, [push])

  function field(key: keyof StudioSettings) {
    return {
      value: (settings[key] ?? '') as string,
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => setSettings((s) => ({ ...s, [key]: e.target.value })),
    }
  }

  async function saveSection(keys: (keyof StudioSettings)[], setSaving: (v: boolean) => void, label: string) {
    setSaving(true)
    try {
      const partial: Partial<StudioSettings> = {}
      keys.forEach((k) => { partial[k] = settings[k] as never })
      await updateStudioSettings(partial)
      push(`${label} saved`)
    } catch (err) {
      push(err instanceof ApiError ? err.message : `Could not save ${label.toLowerCase()}. Please try again.`)
    } finally {
      setSaving(false)
    }
  }

  async function savePublicWebsite() {
    setSavingPublic(true)
    try {
      await updateStudioSettings({
        instagramUrl: settings.instagramUrl, facebookUrl: settings.facebookUrl,
        youtubeUrl: settings.youtubeUrl, mapUrl: settings.mapUrl, footerTagline: settings.footerTagline,
        aboutStatsJson: JSON.stringify(aboutStats.filter((s) => s.value.trim() && s.label.trim())),
        servicesJson: JSON.stringify(services.filter((s) => s.title.trim())),
      })
      push('Public website settings saved')
    } catch (err) {
      push(err instanceof ApiError ? err.message : 'Could not save public website settings. Please try again.')
    } finally {
      setSavingPublic(false)
    }
  }

  return (
    <div className="max-w-3xl space-y-5">
      <section aria-label="My account"><h2 className="mb-3 font-display text-lg text-ink">My account</h2><AccountSettings showMarketing={false} allowPhotoUpload /></section>
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><Palette className="h-4.5 w-4.5 text-accent" /> Appearance</CardTitle></CardHeader>
        <CardContent className="pt-3">
          <p className="mb-4 text-sm text-ink-muted">Pick the look that fits your studio's brand. This applies instantly across the whole dashboard.</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {THEMES.map((t) => (
              <button
                key={t.id}
                onClick={() => setTheme(t.id)}
                className={cn(
                  'relative flex flex-col gap-3 rounded-[var(--radius-lg)] border-2 p-4 text-left transition-all duration-200',
                  theme === t.id ? 'border-accent shadow-[var(--shadow-pop)]' : 'border-border hover:border-ink-faint'
                )}
              >
                <div className="flex h-16 items-center gap-1.5 rounded-[var(--radius-md)] p-3" style={{ backgroundColor: t.swatch[0] }}>
                  <span className="h-3.5 w-3.5 rounded-full" style={{ backgroundColor: t.swatch[1] }} />
                  <span className="h-2 flex-1 rounded-full opacity-60" style={{ backgroundColor: t.swatch[2] }} />
                </div>
                <div className="flex items-start justify-between">
                  <div>
                    <p className="font-display text-base text-ink">{t.label}</p>
                    <p className="text-xs text-ink-faint">{t.blurb}</p>
                  </div>
                  {theme === t.id && <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-accent text-accent-ink"><Check className="h-3 w-3" /></span>}
                </div>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>Studio profile</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 pt-3 sm:grid-cols-2">
          <Input label="Studio name" disabled={loadingSettings} {...field('studioName')} />
          <Input label="Owner name" disabled={loadingSettings} {...field('ownerName')} />
          <Input label="Contact email" disabled={loadingSettings} {...field('email')} />
          <Input label="Phone" disabled={loadingSettings} {...field('phone')} />
          <Input label="WhatsApp number" disabled={loadingSettings} {...field('whatsappNumber')} />
          <Input label="GST number" disabled={loadingSettings} {...field('gstNumber')} />
          <Input label="Address" className="sm:col-span-2" disabled={loadingSettings} {...field('address')} />
          <Input label="Tagline" className="sm:col-span-2" disabled={loadingSettings} {...field('tagline')} />
          <ImageField label="Logo" value={settings.logoUrl ?? ''} onChange={(url) => setSettings((s) => ({ ...s, logoUrl: url }))} />
          <ImageField label="Watermark logo" value={settings.watermarkLogoUrl ?? ''} onChange={(url) => setSettings((s) => ({ ...s, watermarkLogoUrl: url }))} />
        </CardContent>
        <div className="flex justify-end px-6 pb-6">
          <Button
            disabled={loadingSettings || savingProfile}
            onClick={() => saveSection(['studioName', 'ownerName', 'email', 'phone', 'whatsappNumber', 'gstNumber', 'address', 'tagline', 'logoUrl', 'watermarkLogoUrl'], setSavingProfile, 'Studio profile')}
          >
            {savingProfile ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Booking windows</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 pt-3 sm:grid-cols-3">
          <Input type="number" min="0" label="Booking lead time (hrs)" disabled={loadingSettings} {...field('bookingLeadTimeHours')} />
          <Input type="number" min="0" label="Cancellation window (hrs)" disabled={loadingSettings} {...field('cancellationWindowHours')} />
          <Input type="number" min="0" label="Reschedule window (hrs)" disabled={loadingSettings} {...field('rescheduleWindowHours')} />
        </CardContent>
        <div className="flex justify-end px-6 pb-6">
          <Button
            disabled={loadingSettings || savingProfile}
            onClick={() => saveSection(['bookingLeadTimeHours', 'cancellationWindowHours', 'rescheduleWindowHours'], setSavingProfile, 'Booking windows')}
          >
            {savingProfile ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Session &amp; security</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 pt-3 sm:grid-cols-2">
          <Input type="number" min="0" max="1440" label="Auto sign-out after inactivity (minutes)" disabled={loadingSettings} {...field('sessionIdleTimeoutMinutes')} />
          <p className="self-end pb-2 text-xs text-ink-faint">If nobody uses the app for this long, the user is signed out automatically. Anyone actively working stays signed in. Use 0 to turn this off.</p>
        </CardContent>
        <div className="flex justify-end px-6 pb-6">
          <Button
            disabled={loadingSettings || savingSession}
            onClick={async () => {
              const n = Number(settings.sessionIdleTimeoutMinutes ?? 30)
              if (!Number.isFinite(n) || n < 0 || n > 1440) { push('Enter a number of minutes between 0 and 1440.'); return }
              await saveSection(['sessionIdleTimeoutMinutes'], setSavingSession, 'Session timeout')
              localStorage.setItem('cameraman.idleTimeoutMinutes', String(Math.floor(n)))
            }}
          >
            {savingSession ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Payment details</CardTitle></CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 pt-3 sm:grid-cols-2">
          <Input label="Bank name" disabled={loadingSettings} {...field('bankName')} />
          <Input label="Account number" disabled={loadingSettings} {...field('bankAccount')} />
          <Input label="IFSC code" disabled={loadingSettings} {...field('bankIfsc')} />
          <Input label="UPI ID" disabled={loadingSettings} {...field('upiId')} />
        </CardContent>
        <p className="px-6 text-xs text-ink-faint">Shown on invoices and payment reminders sent to clients.</p>
        <div className="flex justify-end px-6 pb-6 pt-4">
          <Button
            disabled={loadingSettings || savingPayment}
            onClick={() => saveSection(['bankName', 'bankAccount', 'bankIfsc', 'upiId'], setSavingPayment, 'Payment details')}
          >
            {savingPayment ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Public website</CardTitle></CardHeader>
        <CardContent className="space-y-5 pt-3">
          <p className="text-sm text-ink-muted">
            Controls the footer, contact page, and About page on the public site — social links, location, the
            business stats shown on About, and the "What we shoot" list on the home page.
          </p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input label="Instagram URL" placeholder="https://instagram.com/yourstudio" disabled={loadingSettings} {...field('instagramUrl')} />
            <Input label="Facebook URL" placeholder="https://facebook.com/yourstudio" disabled={loadingSettings} {...field('facebookUrl')} />
            <Input label="YouTube URL" placeholder="https://youtube.com/@yourstudio" disabled={loadingSettings} {...field('youtubeUrl')} />
            <Input label="Google Maps link" placeholder="https://maps.google.com/…" disabled={loadingSettings} {...field('mapUrl')} />
            <Input label="Footer tagline" className="sm:col-span-2" disabled={loadingSettings} {...field('footerTagline')} />
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-sm font-medium text-ink">About page stats</p>
              <Button type="button" variant="outline" size="sm" onClick={() => setAboutStats((s) => [...s, { value: '', label: '' }])}>
                <Plus className="h-3.5 w-3.5" /> Add stat
              </Button>
            </div>
            {aboutStats.length === 0 && <p className="text-xs text-ink-faint">No stats set — the About page's stats row won't be shown until you add at least one.</p>}
            <div className="space-y-2">
              {aboutStats.map((stat, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input placeholder="Value (e.g. 500+)" value={stat.value} onChange={(e) => setAboutStats((s) => s.map((x, j) => j === i ? { ...x, value: e.target.value } : x))} className="w-32" />
                  <Input placeholder="Label (e.g. sessions shot)" value={stat.label} onChange={(e) => setAboutStats((s) => s.map((x, j) => j === i ? { ...x, label: e.target.value } : x))} className="flex-1" />
                  <button onClick={() => setAboutStats((s) => s.filter((_, j) => j !== i))} className="text-ink-faint hover:text-danger"><Trash2 className="h-4 w-4" /></button>
                </div>
              ))}
            </div>
          </div>

          <div>
            <div className="mb-2 flex items-center justify-between">
              <p className="text-sm font-medium text-ink">"What we shoot" services (home page)</p>
              <Button type="button" variant="outline" size="sm" onClick={() => setServices((s) => [...s, { icon: 'camera', title: '', desc: '' }])}>
                <Plus className="h-3.5 w-3.5" /> Add service
              </Button>
            </div>
            {services.length === 0 && <p className="text-xs text-ink-faint">No services set — the home page will show its built-in default list until you add at least one.</p>}
            <div className="space-y-3">
              {services.map((svc, i) => {
                const Icon = resolveServiceIcon(svc.icon)
                return (
                  <div key={i} className="flex items-start gap-2 rounded-[var(--radius-md)] border border-border p-3">
                    <Icon className="mt-2 h-4 w-4 shrink-0 text-accent" />
                    <div className="flex-1 space-y-2">
                      <div className="flex gap-2">
                        <Select value={svc.icon} onChange={(e) => setServices((s) => s.map((x, j) => j === i ? { ...x, icon: e.target.value } : x))} className="w-32">
                          {SERVICE_ICON_OPTIONS.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
                        </Select>
                        <Input placeholder="Title" value={svc.title} onChange={(e) => setServices((s) => s.map((x, j) => j === i ? { ...x, title: e.target.value } : x))} className="flex-1" />
                      </div>
                      <Input placeholder="Description" value={svc.desc} onChange={(e) => setServices((s) => s.map((x, j) => j === i ? { ...x, desc: e.target.value } : x))} />
                    </div>
                    <button onClick={() => setServices((s) => s.filter((_, j) => j !== i))} className="mt-2 text-ink-faint hover:text-danger"><Trash2 className="h-4 w-4" /></button>
                  </div>
                )
              })}
            </div>
          </div>
        </CardContent>
        <div className="flex justify-end px-6 pb-6">
          <Button disabled={loadingSettings || savingPublic} onClick={savePublicWebsite}>
            {savingPublic ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </Card>

      <Card>
        <CardHeader><CardTitle>Notification preferences</CardTitle></CardHeader>
        <CardContent className="pt-3">
          <div className="divide-y divide-border">
            {[
              { key: 'bookings', label: 'New bookings & cancellations', desc: 'Get notified when a booking is created or cancelled.' },
              { key: 'payments', label: 'Payments received', desc: 'Get notified when a client makes a payment.' },
              { key: 'lowStock', label: 'Low & out of stock alerts', desc: 'Get notified when inventory crosses the reorder level.' },
              { key: 'suggestions', label: 'Suggestion queue summaries', desc: 'Daily summary of auto-sent and pending-review suggestions.' },
            ].map((item, i) => (
              <div key={item.key} className={cn('flex items-center justify-between py-3.5', i === 0 && 'pt-0')}>
                <div>
                  <p className="text-sm font-medium text-ink">{item.label}</p>
                  <p className="text-xs text-ink-faint">{item.desc}</p>
                </div>
                <Switch
                  checked={notifPrefs[item.key as keyof typeof notifPrefs]}
                  onChange={(v) => setNotifPrefs((p) => ({ ...p, [item.key]: v }))}
                />
              </div>
            ))}
          </div>
          <p className="mt-3 text-xs text-ink-faint">
            These toggles are a personal, in-browser preference for this device only — they don't yet change what the
            backend sends. Wire them to a per-user notification-preferences endpoint if silencing specific categories
            server-side matters.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
