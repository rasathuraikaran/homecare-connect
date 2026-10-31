import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Ports can be changed with FRONTEND_PORT / BACKEND_PORT (start-service.sh sets them)
const frontendPort = Number(process.env.FRONTEND_PORT || 5173)
const backendPort = Number(process.env.BACKEND_PORT || 8081)
const backendTarget = process.env.BACKEND_URL || `http://localhost:${backendPort}`

export default defineConfig({
  plugins: [react()],
  server: {
    port: frontendPort,
    strictPort: true, // fail loudly instead of silently moving to another port (CORS expects this one)
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin')
          })
        },
      },
    },
  },
})
