const TOKEN_KEY = 'homecare.token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {}
  if (!form) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: form ?? (body ? JSON.stringify(body) : undefined),
  })
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    const err = new Error(data?.message || 'Something went wrong')
    err.status = res.status
    throw err
  }
  return data
}
