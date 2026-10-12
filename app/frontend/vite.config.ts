import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  // sockjs-client (a dependency of the realtime-notifications WebSocket
  // client) references Node's `global` object directly — webpack polyfills
  // this automatically, Vite does not, so without this the app throws
  // "Uncaught ReferenceError: global is not defined" on load and never
  // renders anything at all.
  define: {
    global: 'globalThis',
  },
  server: {
    host: true,
    allowedHosts: true,
  },
})
