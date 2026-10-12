import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

// Tests reuse the app's Vite config (aliases, plugins) and only add the test settings.
export default mergeConfig(viteConfig as never, defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
}))
