import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            // Frontend calls fetch('/api/...'), Vite proxies to your cluster
            '/api': {
                target: 'http://localhost:53741', // or your ingress URL
                changeOrigin: true,
                rewrite: p => p.replace(/^\/api/, ''),
            },
        },
    },
    build: { sourcemap: true }
})
