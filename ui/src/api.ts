export type UiConfig = {
  env?: string
  apiBaseUrl?: string
  build?: { version?: string; revision?: string }
}

export async function loadConfig(): Promise<UiConfig> {
  const r = await fetch('/config.json', { cache: 'no-store' })
  return r.json()
}

async function get<T>(base: string, path: string): Promise<T> {
  const r = await fetch(`${base.replace(/\/$/, '')}/${path.replace(/^\//, '')}`, {
    headers: { 'Accept': 'application/json' },
    credentials: 'omit',
    cache: 'no-store',
  })
  if (!r.ok) throw new Error(`${r.status} ${r.statusText}`)
  return r.json()
}

/** Example calls – adjust paths to your heatwise-view endpoints */
export const api = {
  health: (base: string) => get<{ status: string }>(base, '/health'),
  latestDecision: (base: string) => get<any>(base, '/decisions/latest'),
  recentStateChanges: (base: string, limit = 20) =>
    get<any[]>(base, `/state/changes?limit=${limit}`),
}
