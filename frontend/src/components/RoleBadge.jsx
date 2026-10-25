import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { ROLES } from '../pages/RoleTabs'

/** Reads ?role= from the URL; returns null when missing or invalid. */
export function useChosenRole() {
  const [params] = useSearchParams()
  const role = params.get('role')
  return ROLES.some((r) => r.value === role) ? role : null
}

export function RequireRole({ role, children }) {
  return role ? children : <Navigate to="/welcome" replace />
}

export function RoleBadge({ role }) {
  const r = ROLES.find((x) => x.value === role)
  const Icon = r.icon
  return (
    <div className="role-badge">
      <span className="pt-chip"><Icon />{r.label}</span>
      <Link to="/welcome">Change</Link>
    </div>
  )
}
