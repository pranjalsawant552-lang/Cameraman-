import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useRef, useState } from 'react'
import { AnimatePresence, motion, useReducedMotion, useScroll, useSpring } from 'framer-motion'
import { ArrowUp, Aperture, Command, Mail, MapPin, Menu, MessageCircle, Phone, X } from 'lucide-react'
import { FacebookIcon, InstagramIcon, YoutubeIcon } from '@/components/shared/SocialIcons'
import { ThemeSwitcher } from '@/components/layout/ThemeSwitcher'
import { CommandPalette } from '@/components/public/CommandPalette'
import { Magnetic } from '@/components/public/motionKit'
import { useCoarsePointer } from '@/components/public/pointer'
import { Button } from '@/components/ui/Button'
import { STUDIO_NAME } from '@/lib/branding'
import { whatsappLink } from '@/lib/publicContent'
import { fetchPublicSiteInfo, type PublicSiteInfo } from '@/lib/api'
import { cn } from '@/lib/utils'

const NAV = [
  { to: '/', label: 'Home', end: true },
  { to: '/services', label: 'Services' },
  { to: '/packages', label: 'Packages' },
  { to: '/about', label: 'About' },
  { to: '/faq', label: 'FAQ' },
  { to: '/contact', label: 'Contact' },
]

export default function PublicLayout() {
  const { pathname } = useLocation()
  const reduce = useReducedMotion()
  const coarse = useCoarsePointer()
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuPath, setMenuPath] = useState(pathname)
  // Close the mobile sheet whenever the route changes (adjusting state during render, not in an effect).
  if (menuPath !== pathname) { setMenuPath(pathname); setMenuOpen(false) }
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const [info, setInfo] = useState<PublicSiteInfo>({})
  const spotRef = useRef<HTMLDivElement>(null)

  const { scrollYProgress, scrollY } = useScroll()
  const progress = useSpring(scrollYProgress, { stiffness: 140, damping: 28, restDelta: 0.001 })

  useEffect(() => { fetchPublicSiteInfo().then(setInfo).catch(() => {}) }, [])

  // Every route change starts at the top.
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'auto' }) }, [pathname])

  useEffect(() => scrollY.on('change', (y) => setScrolled(y > 24)), [scrollY])

  // ⌘K / Ctrl+K
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((o) => !o)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Soft light that follows the cursor across the whole page.
  useEffect(() => {
    if (coarse || reduce) return
    let raf = 0
    function onMove(e: PointerEvent) {
      cancelAnimationFrame(raf)
      raf = requestAnimationFrame(() => {
        spotRef.current?.style.setProperty('--mx', `${e.clientX}px`)
        spotRef.current?.style.setProperty('--my', `${e.clientY}px`)
      })
    }
    window.addEventListener('pointermove', onMove, { passive: true })
    return () => { cancelAnimationFrame(raf); window.removeEventListener('pointermove', onMove) }
  }, [coarse, reduce])

  // Lock page scroll behind the mobile sheet; Escape closes it.
  useEffect(() => {
    if (!menuOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => { document.body.style.overflow = prev; window.removeEventListener('keydown', onKey) }
  }, [menuOpen])

  const wa = whatsappLink(info.whatsappNumber, `Hi ${STUDIO_NAME}, I'd like to know more about a shoot.`)
  const hasSocial = info.instagramUrl || info.facebookUrl || info.youtubeUrl

  return (
    <div className="relative isolate min-h-screen overflow-x-clip bg-bg text-ink">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[80] focus:rounded-[var(--radius-md)] focus:bg-accent focus:px-4 focus:py-2 focus:text-accent-ink">Skip to main content</a>

      {/* scroll progress */}
      <motion.div aria-hidden className="fixed inset-x-0 top-0 z-[55] h-[3px] origin-left bg-gradient-to-r from-accent via-aurora-2 to-aurora-3" style={{ scaleX: progress }} />

      {/* cursor light */}
      <div
        ref={spotRef}
        aria-hidden
        className="pointer-events-none fixed inset-0 z-0 hidden md:block"
        style={{ background: 'radial-gradient(520px circle at var(--mx, 70%) var(--my, 20%), color-mix(in srgb, var(--c-accent) 9%, transparent), transparent 65%)' }}
      />

      {/* floating glass navigation */}
      <header className="fixed inset-x-0 top-0 z-50 px-3 pt-3 sm:px-5">
        <motion.div
          animate={{ paddingTop: scrolled ? 6 : 10, paddingBottom: scrolled ? 6 : 10 }}
          transition={{ duration: 0.25 }}
          className={cn('glass mx-auto flex max-w-6xl items-center justify-between rounded-full px-3 transition-shadow duration-300 sm:px-4', scrolled && 'shadow-[var(--shadow-card)]')}
        >
          <Link to="/" className="flex items-center gap-2.5 pl-1 font-display text-base text-ink sm:text-lg">
            <span className="relative flex h-9 w-9 items-center justify-center rounded-full bg-accent text-accent-ink shadow-[var(--shadow-pop)]">
              <Aperture className="spin-slow h-[18px] w-[18px]" />
            </span>
            <span className="max-w-[9.5rem] truncate sm:max-w-none">{STUDIO_NAME}</span>
          </Link>

          <nav aria-label="Main" className="hidden items-center gap-1 lg:flex">
            {NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => cn('relative rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors', isActive ? 'text-accent-ink' : 'text-ink-muted hover:text-ink')}>
                {({ isActive }) => (
                  <>
                    {isActive && <motion.span layoutId="nav-pill" className="absolute inset-0 rounded-full bg-accent" transition={{ type: 'spring', stiffness: 380, damping: 32 }} />}
                    <span className="relative">{n.label}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setPaletteOpen(true)}
              aria-label="Open quick navigation"
              className="hidden items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-xs text-ink-faint transition hover:border-accent hover:text-ink md:flex"
            >
              <Command className="h-3.5 w-3.5" /> K
            </button>
            <ThemeSwitcher />
            <Link to="/login" className="hidden px-2 text-sm font-medium text-ink-muted hover:text-ink md:block">Sign in</Link>
            <Link to="/register" className="hidden md:block"><Button size="sm" className="rounded-full">Create account</Button></Link>
            <button className="flex h-10 w-10 items-center justify-center rounded-full text-ink hover:bg-surface-2 lg:hidden" onClick={() => setMenuOpen((v) => !v)} aria-label="Toggle menu" aria-expanded={menuOpen} aria-controls="mobile-menu">
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </motion.div>
      </header>

      {/* mobile sheet */}
      <AnimatePresence>
        {menuOpen && (
          <motion.div
            id="mobile-menu"
            className="fixed inset-0 z-40 flex flex-col bg-bg/95 px-6 pb-8 pt-28 backdrop-blur-xl lg:hidden"
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          >
            <div className="aurora" aria-hidden><i /><i /><i /></div>
            <nav aria-label="Mobile" className="relative flex flex-col">
              {NAV.map((n, i) => (
                <motion.div key={n.to} initial={{ opacity: 0, x: -24 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.05 + i * 0.05, duration: 0.4 }}>
                  <NavLink to={n.to} end={n.end} className={({ isActive }) => cn('flex items-baseline gap-3 border-b border-border py-3.5 font-display text-3xl', isActive ? 'text-accent' : 'text-ink')}>
                    <span className="font-sans text-xs text-ink-faint">0{i + 1}</span>{n.label}
                  </NavLink>
                </motion.div>
              ))}
            </nav>
            <div className="relative mt-auto flex flex-col gap-3">
              <Link to="/register"><Button size="lg" className="w-full">Create account</Button></Link>
              <Link to="/login"><Button size="lg" variant="outline" className="w-full">Sign in</Button></Link>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />

      <main id="main-content" tabIndex={-1} className="relative z-20 focus:outline-none">
        <motion.div key={pathname} initial={reduce ? false : { opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}>
          <Outlet />
        </motion.div>
      </main>

      {/* footer */}
      <footer className="relative z-10 overflow-hidden border-t border-border bg-surface/50">
        <div className="film-edge" aria-hidden />
        <div className="pointer-events-none absolute -bottom-10 left-1/2 -translate-x-1/2 select-none whitespace-nowrap font-display text-[17vw] leading-none text-ink/[0.04]" aria-hidden>
          {STUDIO_NAME.split(' ')[0]}
        </div>
        <div className="relative mx-auto grid max-w-6xl grid-cols-1 gap-10 px-4 py-14 sm:grid-cols-2 sm:px-6 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <p className="flex items-center gap-2 font-display text-xl text-ink"><Aperture className="h-5 w-5 text-accent" />{STUDIO_NAME}</p>
            <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-muted">
              {info.footerTagline || 'Studio and on-location photography for weddings, portraits, maternity, newborn and events.'}
            </p>
            <div className="mt-5">
              <Magnetic><Link to="/portal/book"><Button className="rounded-full">Book a shoot</Button></Link></Magnetic>
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-faint">Explore</p>
            <ul className="mt-4 space-y-2.5 text-sm">
              {NAV.map((n) => <li key={n.to}><Link to={n.to} className="text-ink-muted transition-colors hover:text-accent">{n.label}</Link></li>)}
            </ul>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-faint">Studio</p>
            <div className="mt-4 space-y-2.5 text-sm text-ink-muted">
              {info.address && <p className="flex items-start gap-2"><MapPin className="mt-0.5 h-4 w-4 shrink-0 text-ink-faint" /> {info.address}</p>}
              {info.phone && <p className="flex items-center gap-2"><Phone className="h-4 w-4 text-ink-faint" /> <a href={`tel:${info.phone.replace(/\s+/g, '')}`} className="hover:text-accent">{info.phone}</a></p>}
              {info.email && <p className="flex items-center gap-2"><Mail className="h-4 w-4 text-ink-faint" /> <a href={`mailto:${info.email}`} className="break-all hover:text-accent">{info.email}</a></p>}
              {!info.address && !info.phone && !info.email && <p className="text-ink-faint">Contact details haven't been set up yet.</p>}
            </div>
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-ink-faint">Client area</p>
            <ul className="mt-4 space-y-2.5 text-sm">
              <li><Link to="/login" className="text-ink-muted hover:text-accent">Sign in</Link></li>
              <li><Link to="/register" className="text-ink-muted hover:text-accent">Create account</Link></li>
              <li><Link to="/portal/book" className="text-ink-muted hover:text-accent">Book a shoot</Link></li>
            </ul>
            {hasSocial && (
              <div className="mt-5 flex items-center gap-2 text-ink-muted">
                {info.instagramUrl && <a href={info.instagramUrl} target="_blank" rel="noopener noreferrer" aria-label="Instagram" className="flex h-9 w-9 items-center justify-center rounded-full border border-border transition hover:-translate-y-0.5 hover:border-accent hover:text-accent"><InstagramIcon className="h-4 w-4" /></a>}
                {info.facebookUrl && <a href={info.facebookUrl} target="_blank" rel="noopener noreferrer" aria-label="Facebook" className="flex h-9 w-9 items-center justify-center rounded-full border border-border transition hover:-translate-y-0.5 hover:border-accent hover:text-accent"><FacebookIcon className="h-4 w-4" /></a>}
                {info.youtubeUrl && <a href={info.youtubeUrl} target="_blank" rel="noopener noreferrer" aria-label="YouTube" className="flex h-9 w-9 items-center justify-center rounded-full border border-border transition hover:-translate-y-0.5 hover:border-accent hover:text-accent"><YoutubeIcon className="h-4 w-4" /></a>}
              </div>
            )}
          </div>
        </div>
        <div className="relative flex flex-col items-center justify-between gap-3 border-t border-border px-4 py-5 text-xs text-ink-faint sm:flex-row sm:px-6 lg:mx-auto lg:max-w-6xl">
          <span>© {new Date().getFullYear()} {STUDIO_NAME}. All rights reserved.</span>
          <button onClick={() => window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' })} className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 transition hover:border-accent hover:text-accent">
            Back to top <ArrowUp className="h-3.5 w-3.5" />
          </button>
        </div>
      </footer>

      {/* floating WhatsApp — only when the studio has configured a number */}
      {wa && !menuOpen && (
        <a
          href={wa}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Chat on WhatsApp"
          className="fixed bottom-5 right-5 z-[45] flex h-14 w-14 items-center justify-center rounded-full bg-[#25D366] text-white shadow-[0_12px_30px_-8px_rgba(37,211,102,0.7)] transition-transform hover:scale-110"
        >
          <span className="absolute inset-0 rounded-full bg-[#25D366]" style={{ animation: 'pulse-ring 2.2s ease-out infinite' }} />
          <MessageCircle className="relative h-6 w-6" />
        </a>
      )}
    </div>
  )
}
