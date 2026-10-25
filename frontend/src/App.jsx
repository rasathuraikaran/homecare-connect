import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import WelcomePage from './pages/WelcomePage'
import RegisterPage from './pages/RegisterPage'
import ProfilePage from './pages/ProfilePage'
import DashboardPage from './pages/DashboardPage'

function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <p className="center">Loading…</p>
  if (!user) return <Navigate to="/welcome" replace />
  // Caregivers must finish their profile before using the app
  if (!user.profileCompleted && location.pathname !== '/profile') return <Navigate to="/profile" replace />
  return children
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
      <Route path="/profile" element={<RequireAuth><ProfilePage /></RequireAuth>} />
      <Route path="/" element={<RequireAuth><DashboardPage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
