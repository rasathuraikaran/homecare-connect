import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AdminTabs from './AdminTabs'
import { HeartHouse } from './Icons'

/** The frame shared by the admin pages: top bar, section tabs and a heading. */
export default function AdminShell({ title, subtitle, children }) {
  const { logout } = useAuth()
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect <span className="admin-tag">Admin</span></div>
        <div className="user">
          <Link className="btn-ghost" to="/">Back to my page</Link>
          <button className="btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </header>

      <main className="content wide">
        <AdminTabs />
        <div className="page-head">
          <h1>{title}</h1>
          <p className="subtitle">{subtitle}</p>
        </div>
        {children}
      </main>
    </div>
  )
}
