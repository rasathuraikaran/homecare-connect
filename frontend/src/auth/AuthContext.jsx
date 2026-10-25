import { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { api, tokenStore } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!tokenStore.get())

  useEffect(() => {
    if (!tokenStore.get()) return
    api('/auth/me')
      .then(setUser)
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false))
  }, [])

  const handleAuth = useCallback(({ token, user }) => {
    tokenStore.set(token)
    setUser(user)
    return user
  }, [])

  const login = (credentials) => api('/auth/login', { method: 'POST', body: credentials }).then(handleAuth)
  const register = (details) => api('/auth/register', { method: 'POST', body: details }).then(handleAuth)
  const googleLogin = (payload) => api('/auth/google', { method: 'POST', body: payload }).then(handleAuth)
  const logout = () => {
    tokenStore.clear()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, googleLogin, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
