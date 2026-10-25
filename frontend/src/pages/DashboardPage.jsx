import { useAuth } from '../auth/AuthContext'
import { HeartHouse } from '../components/Icons'

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const isCaregiver = user.role === 'CAREGIVER'
  const initials = user.fullName.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        <div className="user">
          <span className="avatar">{initials}</span>
          <span className="user-meta"><strong>{user.fullName}</strong><em>{isCaregiver ? 'Caregiver' : 'Care seeker'}</em></span>
          <button className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>
      <section className="content">
        <h1>Welcome, {user.fullName.split(' ')[0]} 👋</h1>
        <p className="subtitle">
          {isCaregiver
            ? 'Next step: build your profile so families can find you.'
            : 'Next step: browse caregivers and pick the right one.'}
        </p>
      </section>
    </div>
  )
}
