import { useEffect, useState } from 'react'
import { api, loadConfig, type UiConfig } from './api'

export default function App() {
  const [config, setConfig] = useState<UiConfig | null>(null)
  const [health, setHealth] = useState<string>('unknown')
  const [latest, setLatest] = useState<any>(null)
  const [changes, setChanges] = useState<any[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    (async () => {
      try {
        const cfg = await loadConfig()
        setConfig(cfg)
        if (!cfg.apiBaseUrl) return

        const h = await api.health(cfg.apiBaseUrl)
        setHealth(h?.status ?? 'ok')

        const d = await api.latestDecision(cfg.apiBaseUrl)
        setLatest(d)

        const sc = await api.recentStateChanges(cfg.apiBaseUrl, 20)
        setChanges(sc)
      } catch (e: any) {
        setError(e?.message ?? String(e))
      }
    })()
  }, [])

  return (
    <div style={{ fontFamily: 'Inter, system-ui, sans-serif', padding: '2rem' }}>
      <h1>Heatwise UI</h1>
      <p>Talking to: <code>{config?.apiBaseUrl ?? '(not set)'}</code></p>

      {error && <p style={{ color: 'crimson' }}>Error: {error}</p>}

      <h2>Health</h2>
      <pre>{health}</pre>

      <h2>Latest decision</h2>
      <pre style={{ background: '#111', color: '#eee', padding: '1rem', borderRadius: 8 }}>
        {JSON.stringify(latest, null, 2)}
      </pre>

      <h2>Recent state changes</h2>
      <pre style={{ background: '#111', color: '#eee', padding: '1rem', borderRadius: 8 }}>
        {JSON.stringify(changes, null, 2)}
      </pre>

      <h2>Runtime Config</h2>
      <pre style={{ background: '#111', color: '#eee', padding: '1rem', borderRadius: 8 }}>
        {JSON.stringify(config, null, 2)}
      </pre>
    </div>
  )
}
