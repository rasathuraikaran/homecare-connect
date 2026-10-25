import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import WelcomePage from './pages/WelcomePage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'

function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="center">Loading…</p>
  return user ? children : <Navigate to="/welcome" replace />
}

function GuestOnly({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <p className="center">Loading…</p>
  return user ? <Navigate to="/" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route path="/welcome" element={<GuestOnly><WelcomePage /></GuestOnly>} />
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/" element={<RequireAuth><DashboardPage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
