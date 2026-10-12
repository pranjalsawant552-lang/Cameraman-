import { create } from 'zustand'

export interface Toast {
  id: string
  message: string
  tone: 'success' | 'error' | 'info'
}

interface ToastState {
  toasts: Toast[]
  push: (message: string, tone?: Toast['tone']) => void
  dismiss: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (message, tone = 'success') => {
    const id = Math.random().toString(36).slice(2)
    set((s) => ({ toasts: [...s.toasts, { id, message, tone }] }))
    setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 3200)
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))
