import { useEffect, useId, useRef, useState } from 'react'
import { fetchClients } from '@/lib/api'
import type { Client } from '@/lib/types'
import { cn } from '@/lib/utils'

interface Props {
  label?: string
  value: string
  onChange: (text: string) => void
  /** Called when the user picks an existing client from the suggestions. */
  onSelect?: (client: Client) => void
  placeholder?: string
  className?: string
}

/**
 * Text input that suggests existing clients (by name / mobile / email) while typing.
 * Typing a brand-new name still works — suggestions are optional. Picking one calls
 * onSelect so the form can fill mobile/email and link the client id.
 */
export function ClientCombobox({ label = 'Client name', value, onChange, onSelect, placeholder, className }: Props) {
  const id = useId()
  const [items, setItems] = useState<Client[]>([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [loading, setLoading] = useState(false)
  const justPicked = useRef(false)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (justPicked.current) { justPicked.current = false; return }
    const q = value.trim()
    if (q.length < 2) { setItems([]); setOpen(false); return }
    let cancelled = false
    setLoading(true)
    const t = window.setTimeout(() => {
      fetchClients({ search: q, size: 6 })
        .then((res) => { if (!cancelled) { setItems(res.content); setOpen(true); setActive(-1) } })
        .catch(() => { if (!cancelled) setItems([]) })
        .finally(() => { if (!cancelled) setLoading(false) })
    }, 250)
    return () => { cancelled = true; window.clearTimeout(t) }
  }, [value])

  useEffect(() => {
    function onDoc(e: MouseEvent) { if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  function pick(c: Client) {
    justPicked.current = true
    onChange(`${c.firstName} ${c.lastName}`.trim())
    onSelect?.(c)
    setOpen(false)
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (!open || items.length === 0) return
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => (a + 1) % items.length) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => (a <= 0 ? items.length - 1 : a - 1)) }
    else if (e.key === 'Enter' && active >= 0) { e.preventDefault(); pick(items[active]) }
    else if (e.key === 'Escape') setOpen(false)
  }

  return (
    <div ref={boxRef} className={cn('relative flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-ink-muted">{label}</label>
      <input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={`${id}-list`}
        aria-autocomplete="list"
        autoComplete="off"
        value={value}
        placeholder={placeholder ?? 'Start typing to find an existing client'}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => items.length > 0 && setOpen(true)}
        onKeyDown={onKeyDown}
        className="h-10 w-full rounded-[var(--radius-sm)] border border-border bg-surface-2 px-3.5 text-sm text-ink outline-none transition-all placeholder:text-ink-faint focus:border-accent focus:ring-2 focus:ring-accent/20"
      />
      {open && (
        <ul id={`${id}-list`} role="listbox" className="absolute left-0 right-0 top-full z-50 mt-1 max-h-64 overflow-auto rounded-[var(--radius-md)] border border-border bg-surface shadow-lg">
          {items.length === 0 && !loading && <li className="px-3 py-2 text-xs text-ink-faint">No existing client — a new one will be used.</li>}
          {items.map((c, i) => (
            <li key={c.id} role="option" aria-selected={i === active}
              onMouseDown={(e) => { e.preventDefault(); pick(c) }}
              onMouseEnter={() => setActive(i)}
              className={cn('cursor-pointer px-3 py-2 text-sm', i === active ? 'bg-surface-2' : '')}>
              <span className="font-medium text-ink">{c.firstName} {c.lastName}</span>
              <span className="ml-2 text-xs text-ink-faint">{[c.mobile, c.email].filter(Boolean).join(' · ')}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
