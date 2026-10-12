import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { CheckCircle2, Mail, MapPin, MessageCircle, Phone, Send } from 'lucide-react'
import { FacebookIcon, InstagramIcon, YoutubeIcon } from '@/components/shared/SocialIcons'
import { Input, Textarea } from '@/components/ui/Input'
import { Button } from '@/components/ui/Button'
import { PageHero } from '@/components/public/PageHero'
import { Magnetic, Reveal, Tilt } from '@/components/public/motionKit'
import { fetchPublicSiteInfo, parseServices, submitContactInquiry, type PublicSiteInfo } from '@/lib/api'
import { ApiError } from '@/lib/apiClient'
import { DEFAULT_SERVICES, whatsappLink } from '@/lib/publicContent'
import { usePageMeta } from '@/lib/usePageMeta'
import { cn } from '@/lib/utils'

const MAX_MESSAGE = 1000
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function Burst() {
  // A brief ring of sparks behind the success tick.
  return (
    <div className="pointer-events-none absolute inset-0 flex items-center justify-center" aria-hidden>
      {Array.from({ length: 12 }).map((_, i) => {
        const a = (i / 12) * Math.PI * 2
        return (
          <motion.span
            key={i}
            className="absolute h-2 w-2 rounded-full bg-accent"
            initial={{ x: 0, y: 0, opacity: 1, scale: 1 }}
            animate={{ x: Math.cos(a) * 90, y: Math.sin(a) * 90, opacity: 0, scale: 0.3 }}
            transition={{ duration: 0.9, ease: 'easeOut' }}
          />
        )
      })}
    </div>
  )
}

export default function PublicContact() {
  usePageMeta('Contact', 'Send us a message about a package, availability or an event — we reply within a business day.')
  const [params] = useSearchParams()
  const [form, setForm] = useState({ name: '', email: '', mobile: '', message: '' })
  const [topic, setTopic] = useState<string>(params.get('topic') ?? '')
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<PublicSiteInfo>({})

  useEffect(() => { fetchPublicSiteInfo().then(setInfo).catch(() => {}) }, [])

  const topics = useMemo(() => {
    const parsed = parseServices(info.servicesJson)
    return (parsed.length ? parsed : DEFAULT_SERVICES).map((s) => s.title)
  }, [info.servicesJson])

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }))
  }

  const errors = {
    name: form.name.trim() ? '' : 'Please tell us your name.',
    email: !form.email.trim() ? 'Email is required.' : EMAIL_RE.test(form.email.trim()) ? '' : 'That email looks incomplete.',
    mobile: form.mobile.trim() && form.mobile.replace(/\D/g, '').length < 10 ? 'Enter a 10-digit mobile number, or leave it blank.' : '',
    message: form.message.trim() ? '' : 'A short message helps us help you.',
  }
  const hasErrors = Object.values(errors).some(Boolean)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setTouched({ name: true, email: true, mobile: true, message: true })
    if (hasErrors) return
    setSubmitting(true)
    try {
      const body = topic ? `Interested in: ${topic}\n\n${form.message.trim()}` : form.message.trim()
      await submitContactInquiry({ name: form.name.trim(), email: form.email.trim(), mobile: form.mobile.trim() || undefined, message: body })
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send your message. Please try again, or call us directly.')
    } finally {
      setSubmitting(false)
    }
  }

  const wa = whatsappLink(info.whatsappNumber, 'Hi, I would like to know more about a shoot.')
  const contactCards = [
    info.phone && { icon: Phone, label: 'Call us', value: info.phone, href: `tel:${info.phone.replace(/\s+/g, '')}` },
    info.email && { icon: Mail, label: 'Email', value: info.email, href: `mailto:${info.email}` },
    info.address && { icon: MapPin, label: 'Visit the studio', value: info.address, href: info.mapUrl },
  ].filter(Boolean) as { icon: typeof Phone; label: string; value: string; href?: string }[]
  const hasSocial = info.instagramUrl || info.facebookUrl || info.youtubeUrl
  const showErr = (k: keyof typeof errors) => (touched[k] && errors[k] ? errors[k] : '')

  return (
    <div>
      <PageHero eyebrow="Contact" title="Let's talk about your shoot" accentLast={2} subtitle="Questions about a package, availability, or an event we haven't listed? Send a message and we'll reply within a business day." />

      <section className="mx-auto grid max-w-6xl grid-cols-1 gap-10 px-4 py-16 sm:px-6 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="space-y-4">
          {wa && (
            <Reveal>
              <a href={wa} target="_blank" rel="noopener noreferrer" className="group flex items-center gap-4 rounded-[var(--radius-lg)] border border-[#25D366]/40 bg-[#25D366]/10 p-5 transition hover:-translate-y-0.5 hover:bg-[#25D366]/15">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#25D366] text-white"><MessageCircle className="h-6 w-6" /></span>
                <span className="flex-1"><span className="block font-medium text-ink">Chat on WhatsApp</span><span className="block text-sm text-ink-muted">Usually the fastest way to reach us</span></span>
              </a>
            </Reveal>
          )}
          {contactCards.map((c, i) => {
            const inner = (
              <Tilt className="rounded-[var(--radius-lg)]" max={6}>
                <div className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-surface/80 p-5 backdrop-blur">
                  <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-accent/12 text-accent" style={{ transform: 'translateZ(30px)' }}><c.icon className="h-5 w-5" /></span>
                  <span className="min-w-0"><span className="block text-xs font-semibold uppercase tracking-[0.12em] text-ink-faint">{c.label}</span><span className="block break-words text-sm text-ink">{c.value}</span></span>
                </div>
              </Tilt>
            )
            return (
              <Reveal key={c.label} delay={i * 0.08}>
                {c.href ? <a href={c.href} target={c.href.startsWith('http') ? '_blank' : undefined} rel="noopener noreferrer" className="block">{inner}</a> : inner}
              </Reveal>
            )
          })}
          {contactCards.length === 0 && !wa && (
            <p className="rounded-[var(--radius-lg)] border border-dashed border-border p-5 text-sm text-ink-faint">Contact details haven't been set up yet — use the form and we'll get back to you.</p>
          )}
          {hasSocial && (
            <Reveal className="flex items-center gap-3 pt-2 text-ink-muted">
              {info.instagramUrl && <a href={info.instagramUrl} target="_blank" rel="noopener noreferrer" aria-label="Instagram" className="flex h-11 w-11 items-center justify-center rounded-full border border-border transition hover:-translate-y-1 hover:border-accent hover:text-accent"><InstagramIcon className="h-5 w-5" /></a>}
              {info.facebookUrl && <a href={info.facebookUrl} target="_blank" rel="noopener noreferrer" aria-label="Facebook" className="flex h-11 w-11 items-center justify-center rounded-full border border-border transition hover:-translate-y-1 hover:border-accent hover:text-accent"><FacebookIcon className="h-5 w-5" /></a>}
              {info.youtubeUrl && <a href={info.youtubeUrl} target="_blank" rel="noopener noreferrer" aria-label="YouTube" className="flex h-11 w-11 items-center justify-center rounded-full border border-border transition hover:-translate-y-1 hover:border-accent hover:text-accent"><YoutubeIcon className="h-5 w-5" /></a>}
            </Reveal>
          )}
          {info.mapUrl && !info.address && (
            <a href={info.mapUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline"><MapPin className="h-4 w-4" /> Get directions</a>
          )}
        </div>

        <Reveal delay={0.1}>
          <div className="border-spin relative overflow-hidden rounded-[var(--radius-xl)] border border-border bg-surface/90 p-6 backdrop-blur sm:p-9">
            <AnimatePresence mode="wait">
              {submitted ? (
                <motion.div key="done" initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="relative flex flex-col items-center py-14 text-center" role="status">
                  <Burst />
                  <motion.span initial={{ scale: 0, rotate: -90 }} animate={{ scale: 1, rotate: 0 }} transition={{ type: 'spring', stiffness: 220, damping: 14 }}><CheckCircle2 className="h-16 w-16 text-success" /></motion.span>
                  <p className="mt-5 font-display text-2xl text-ink">Message sent</p>
                  <p className="mt-2 max-w-xs text-sm text-ink-muted">Thanks for reaching out — we'll get back to you soon.</p>
                  <div className="mt-7 flex flex-wrap justify-center gap-3">
                    <Link to="/packages"><Button variant="outline" className="rounded-full">Browse packages</Button></Link>
                    <Link to="/"><Button variant="ghost" className="rounded-full">Back home</Button></Link>
                  </div>
                </motion.div>
              ) : (
                <motion.form key="form" onSubmit={handleSubmit} noValidate exit={{ opacity: 0 }} className="space-y-5">
                  <div>
                    <p className="text-sm font-medium text-ink-muted" id="topic-label">What are you planning? <span className="text-ink-faint">(optional)</span></p>
                    <div className="mt-2.5 flex flex-wrap gap-2" role="group" aria-labelledby="topic-label">
                      {topics.map((t) => (
                        <button type="button" key={t} aria-pressed={topic === t} onClick={() => setTopic(topic === t ? '' : t)} className={cn('rounded-full border px-3.5 py-1.5 text-xs font-medium transition', topic === t ? 'border-accent bg-accent text-accent-ink' : 'border-border text-ink-muted hover:border-accent hover:text-ink')}>{t}</button>
                      ))}
                    </div>
                  </div>
                  <div className="grid gap-5 sm:grid-cols-2">
                    <div>
                      <Input label="Name" value={form.name} autoComplete="name" onBlur={() => setTouched((t) => ({ ...t, name: true }))} onChange={(e) => set('name', e.target.value)} aria-invalid={!!showErr('name')} />
                      {showErr('name') && <p role="alert" className="mt-1 text-xs text-danger">{showErr('name')}</p>}
                    </div>
                    <div>
                      <Input label="Email" type="email" value={form.email} autoComplete="email" onBlur={() => setTouched((t) => ({ ...t, email: true }))} onChange={(e) => set('email', e.target.value)} aria-invalid={!!showErr('email')} />
                      {showErr('email') && <p role="alert" className="mt-1 text-xs text-danger">{showErr('email')}</p>}
                    </div>
                  </div>
                  <div>
                    <Input label="Mobile (optional)" value={form.mobile} inputMode="tel" autoComplete="tel" onBlur={() => setTouched((t) => ({ ...t, mobile: true }))} onChange={(e) => set('mobile', e.target.value)} placeholder="9876543210" aria-invalid={!!showErr('mobile')} />
                    {showErr('mobile') && <p role="alert" className="mt-1 text-xs text-danger">{showErr('mobile')}</p>}
                  </div>
                  <div>
                    <Textarea label="Message" value={form.message} maxLength={MAX_MESSAGE} onBlur={() => setTouched((t) => ({ ...t, message: true }))} onChange={(e) => set('message', e.target.value)} rows={5} placeholder="Tell us about the shoot you're planning…" aria-invalid={!!showErr('message')} />
                    <div className="mt-1 flex justify-between text-xs">
                      <span role={showErr('message') ? 'alert' : undefined} className="text-danger">{showErr('message')}</span>
                      <span className="text-ink-faint">{form.message.length}/{MAX_MESSAGE}</span>
                    </div>
                  </div>
                  {error && <p role="alert" className="rounded-[var(--radius-md)] bg-danger-bg px-3 py-2 text-sm text-danger">{error}</p>}
                  <Magnetic className="block"><Button type="submit" size="lg" className="w-full rounded-full" loading={submitting}>{submitting ? 'Sending…' : <>Send message <Send className="h-4 w-4" /></>}</Button></Magnetic>
                  <p className="text-center text-sm text-ink-muted">Ready to pick a date? <Link to="/portal/book" className="font-medium text-accent hover:underline">Book a session online</Link>.</p>
                </motion.form>
              )}
            </AnimatePresence>
          </div>
        </Reveal>
      </section>
    </div>
  )
}
