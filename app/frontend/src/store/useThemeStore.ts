import { create } from 'zustand'

export type ThemeName = 'noir' | 'editorial' | 'daylight' | 'aperture'

export interface ThemeMeta {
  id: ThemeName
  label: string
  blurb: string
  /** [background, accent, ink] — kept as the first three entries for existing consumers. */
  swatch: string[]
  /** Extra colours used by the live mini-preview in the theme picker. */
  preview: { bg: string; surface: string; accent: string; ink: string; glow: string }
  dark: boolean
}

export const THEMES: ThemeMeta[] = [
  {
    id: 'noir', label: 'Noir', dark: true,
    blurb: 'Obsidian and antique gold — cinematic and premium',
    swatch: ['#0b0a08', '#d8b26a', '#f5efe2'],
    preview: { bg: '#0b0a08', surface: '#1d1a15', accent: '#d8b26a', ink: '#f5efe2', glow: '#b8692f' },
  },
  {
    id: 'editorial', label: 'Editorial', dark: false,
    blurb: 'Warm paper and terracotta — like a printed magazine',
    swatch: ['#f4eee1', '#a8482a', '#241c12'],
    preview: { bg: '#f4eee1', surface: '#fcf9f2', accent: '#a8482a', ink: '#241c12', glow: '#e0a37a' },
  },
  {
    id: 'daylight', label: 'Daylight', dark: false,
    blurb: 'Gallery white and sage — airy, quiet, modern',
    swatch: ['#f7f8f5', '#2e7060', '#1b2220'],
    preview: { bg: '#f7f8f5', surface: '#ffffff', accent: '#2e7060', ink: '#1b2220', glow: '#bfe3d2' },
  },
  {
    id: 'aperture', label: 'Aperture', dark: true,
    blurb: 'Midnight indigo, coral and neon light leaks',
    swatch: ['#0b0d20', '#ff6a55', '#eeedfc'],
    preview: { bg: '#0b0d20', surface: '#1e2248', accent: '#ff6a55', ink: '#eeedfc', glow: '#7a5cff' },
  },
]

const STORAGE_KEY = 'cameraman-theme'

function isTheme(v: string | null): v is ThemeName {
  return THEMES.some((t) => t.id === v)
}

function applyThemeToDom(theme: ThemeName) {
  document.documentElement.setAttribute('data-theme', theme)
  // Keep the mobile browser chrome in step with the page background.
  const meta = THEMES.find((t) => t.id === theme)
  let tag = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!tag) {
    tag = document.createElement('meta')
    tag.name = 'theme-color'
    document.head.appendChild(tag)
  }
  if (meta) tag.content = meta.preview.bg
}

interface ThemeState {
  theme: ThemeName
  setTheme: (theme: ThemeName) => void
}

function readStored(): ThemeName {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return isTheme(stored) ? stored : 'noir'
  } catch {
    return 'noir'
  }
}

const initial = readStored()
applyThemeToDom(initial)

export const useThemeStore = create<ThemeState>((set) => ({
  theme: initial,
  setTheme: (theme) => {
    try { localStorage.setItem(STORAGE_KEY, theme) } catch { /* private mode — theme still applies for this session */ }
    applyThemeToDom(theme)
    set({ theme })
  },
}))
