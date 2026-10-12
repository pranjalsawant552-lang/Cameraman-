import { forwardRef } from 'react'
import type { ButtonHTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium transition-all duration-200 disabled:opacity-50 disabled:pointer-events-none active:scale-[0.98]',
  {
    variants: {
      variant: {
        primary: 'bg-accent text-accent-ink hover:brightness-110 shadow-[var(--shadow-pop)]',
        secondary: 'bg-surface-2 text-ink hover:bg-surface-3 border border-border',
        ghost: 'text-ink-muted hover:text-ink hover:bg-surface-2',
        outline: 'border border-border text-ink hover:bg-surface-2',
        danger: 'bg-danger-bg text-danger hover:brightness-95 border border-danger/20',
      },
      size: {
        sm: 'h-8 px-3 text-sm rounded-[var(--radius-sm)]',
        md: 'h-10 px-4 text-sm rounded-[var(--radius-md)]',
        lg: 'h-12 px-6 text-base rounded-[var(--radius-md)]',
        icon: 'h-10 w-10 rounded-[var(--radius-md)]',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  }
)

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />}
      {children}
    </button>
  )
)
Button.displayName = 'Button'
