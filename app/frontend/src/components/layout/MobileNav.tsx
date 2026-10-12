import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { Menu } from 'lucide-react'
import { Drawer } from '@/components/ui/Drawer'
import { SidebarContent } from './Sidebar'

/** Hamburger + slide-in navigation for screens narrower than the desktop sidebar (below 1024px). */
export function MobileNav() {
  const [open, setOpen] = useState(false)
  const { pathname } = useLocation()
  useEffect(() => { setOpen(false) }, [pathname])

  return (
    <>
      <button type="button" onClick={() => setOpen(true)} aria-label="Open navigation menu" aria-haspopup="dialog" aria-expanded={open}
        className="-ml-2 rounded-full p-2 text-ink-muted hover:bg-surface-2 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent lg:hidden">
        <Menu className="h-5 w-5" />
      </button>
      <Drawer open={open} onClose={() => setOpen(false)} title="Menu" side="left" bare>
        <SidebarContent animated={false} onNavigate={() => setOpen(false)} />
      </Drawer>
    </>
  )
}
