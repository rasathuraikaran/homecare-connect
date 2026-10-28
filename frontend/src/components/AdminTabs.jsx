import { useEffect, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { api } from '../api/client'

const tab = ({ isActive }) => (isActive ? 'admin-tab on' : 'admin-tab')

export default function AdminTabs() {
  const [waiting, setWaiting] = useState(0)

  // how many caregivers have documents waiting, checked now and every 30 seconds
  useEffect(() => {
    let alive = true
    const check = () => api('/admin/verifications/summary').then((s) => alive && setWaiting(s.needsReview)).catch(() => {})
    check()
    const timer = setInterval(check, 30_000)
    return () => { alive = false; clearInterval(timer) }
  }, [])

  return (
    <nav className="admin-tabs" aria-label="Admin sections">
      <NavLink to="/admin" end className={tab}>Booking requests</NavLink>
      <NavLink to="/admin/verification" className={tab}>
        Verification {waiting > 0 && <span className="count-badge" aria-label={`${waiting} waiting`}>{waiting}</span>}
      </NavLink>
    </nav>
  )
}
