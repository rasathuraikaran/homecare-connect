const TOKEN_KEY = 'homecare.token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Fetches a file that needs the sign-in token, which a plain link cannot send. Returns a Blob. */
export async function apiBlob(path) {
  const token = tokenStore.get()
  let res
  try {
    res = await fetch(`/api${path}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  } catch {
    throw new Error("Can't reach the server. Check your connection and try again.")
  }
  if (!res.ok) {
    const data = await res.json().catch(() => null)
    throw new Error(data?.message || 'Could not open the file.')
  }
  return res.blob()
}

export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {}
  if (!form) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: form ?? (body ? JSON.stringify(body) : undefined),
    })
  } catch {
    throw new Error("Can't reach the server. Check your connection and try again.")
  }
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    const err = new Error(data?.message || 'Something went wrong. Please try again.')
    err.status = res.status
    err.errors = data?.errors ?? null
    throw err
  }
  return data
}
