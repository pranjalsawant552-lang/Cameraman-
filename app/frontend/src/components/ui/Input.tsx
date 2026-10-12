import { forwardRef, useId, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Search } from 'lucide-react'
import { cn } from '@/lib/utils'

const fieldBase =
  'w-full bg-surface-2 border border-border rounded-[var(--radius-sm)] px-3.5 h-10 text-sm text-ink placeholder:text-ink-faint outline-none transition-all duration-150 focus:border-accent focus:ring-2 focus:ring-accent/20'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label?: string }>(
  ({ className, label, id, ...props }, ref) => {
  const autoId = useId(); const fieldId = id ?? autoId
  return (
    <div className="flex flex-col gap-1.5">
      {label && <label htmlFor={fieldId} className="text-sm font-medium text-ink-muted">{label}</label>}
      <input ref={ref} id={fieldId} className={cn(fieldBase, className)} {...props} />
    </div>
  )
})
Input.displayName = 'Input'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }>(
  ({ className, label, id, ...props }, ref) => {
  const autoId = useId(); const fieldId = id ?? autoId
  return (
    <div className="flex flex-col gap-1.5">
      {label && <label htmlFor={fieldId} className="text-sm font-medium text-ink-muted">{label}</label>}
      <textarea ref={ref} id={fieldId} className={cn(fieldBase, 'h-auto min-h-24 py-2.5 resize-y leading-relaxed', className)} {...props} />
    </div>
  )
})
Textarea.displayName = 'Textarea'

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { label?: string }>(
  ({ className, label, id, children, ...props }, ref) => {
  const autoId = useId(); const fieldId = id ?? autoId
  return (
    <div className="flex flex-col gap-1.5">
      {label && <label htmlFor={fieldId} className="text-sm font-medium text-ink-muted">{label}</label>}
      <select ref={ref} id={fieldId} className={cn(fieldBase, 'appearance-none bg-no-repeat pr-8', className)}
        style={{
          backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%239a9fc7' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
          backgroundPosition: 'right 0.75rem center',
        }}
        {...props}
      >
        {children}
      </select>
    </div>
  )
})
Select.displayName = 'Select'

export function SearchInput({ value, onChange, placeholder = 'Search...' }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-faint" />
      <input
        type="search"
        aria-label={placeholder.replace(/…|\.\.\.$/, '')}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={cn(fieldBase, 'pl-9')}
      />
    </div>
  )
}
