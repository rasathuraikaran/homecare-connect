import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import SeekerHome from './SeekerHome'
import Avatar from '../components/Avatar'
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
          <Avatar user={user} initials={initials} />
          <span className="user-meta"><strong>{user.fullName}</strong><em>{isCaregiver ? 'Caregiver' : 'Care seeker'}</em></span>
          {isCaregiver && <Link className="btn-ghost" to="/profile">Edit profile</Link>}
          <button className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>
      {isCaregiver ? (
        <section className="content">
          <h1>Welcome, {user.fullName.split(' ')[0]} 👋</h1>
          <p className="subtitle">Your profile is saved. Use "Edit profile" any time to add more details.</p>
        </section>
      ) : (
        <SeekerHome firstName={user.fullName.split(' ')[0]} />
      )}
    </div>
  )
}
