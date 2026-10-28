import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, HeartHouse } from '../components/Icons'
import StatusChip from '../components/StatusChip'
import { alertLabel, ALERT_TONE, STATUS_TONE } from '../constants/bookings'
import { jobLabel } from '../constants/jobs'
import { RATE_TYPES, formatMoney } from '../constants/rates'

const FILTERS = [
  { value: 'REQUESTED', label: 'Needs action' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: '', label: 'All' },
]
const STATUS_TEXT = { REQUESTED: 'Requested', CONFIRMED: 'Confirmed', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }
const NEXT = {
  REQUESTED: [{ to: 'CONFIRMED', label: 'Mark confirmed', primary: true }, { to: 'CANCELLED', label: 'Cancel', danger: true }],
  CONFIRMED: [{ to: 'COMPLETED', label: 'Mark completed', primary: true }, { to: 'CANCELLED', label: 'Cancel', danger: true }],
}
const POLL_MS = 30_000

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export default function AdminPage() {
  const { logout } = useAuth()
  const [filter, setFilter] = useState('REQUESTED')
  const [state, setState] = useState({ status: 'loading', list: [], error: '' })
  const [busyId, setBusyId] = useState(null)
  const [confirmCancel, setConfirmCancel] = useState(null)
  const [actionError, setActionError] = useState('')

  const load = useCallback((silent = false) => {
    if (!silent) setState((s) => ({ ...s, status: 'loading' }))
    return api(`/admin/bookings${filter ? `?status=${filter}` : ''}`)
      .then((list) => setState({ status: 'done', list, error: '' }))
      .catch((e) => { if (!silent) setState({ status: 'error', list: [], error: e.message }) })
  }, [filter])

  // load now, then check for new requests every 30 seconds
  useEffect(() => {
    load()
    const timer = setInterval(() => load(true), POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  function applyUpdate(updated) {
    setState((s) => ({
      ...s,
      list: filter && updated.status !== filter ? s.list.filter((b) => b.id !== updated.id) : s.list.map((b) => (b.id === updated.id ? updated : b)),
    }))
  }

  async function run(id, request) {
    setBusyId(id)
    setActionError('')
    try {
      applyUpdate(await request())
      setConfirmCancel(null)
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  const setStatus = (id, status) => run(id, () => api(`/admin/bookings/${id}/status`, { method: 'PATCH', body: { status } }))
  const resend = (id) => run(id, () => api(`/admin/bookings/${id}/resend-alert`, { method: 'POST' }))

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
        <div className="page-head">
          <h1>Booking requests</h1>
          <p className="subtitle">Call the care seeker, arrange it with the caregiver, then update the status here.</p>
        </div>

        <div className="admin-bar">
          <div className="filters" role="tablist">
            {FILTERS.map((f) => (
              <button key={f.value} type="button" role="tab" aria-selected={filter === f.value}
                      className={filter === f.value ? 'filter on' : 'filter'} onClick={() => setFilter(f.value)}>{f.label}</button>
            ))}
          </div>
          <button type="button" className="btn-ghost" onClick={() => load()}>Refresh</button>
        </div>

        {actionError && <div className="alert" role="alert"><Alert />{actionError}</div>}
        {state.status === 'error' && <div className="alert" role="alert"><Alert />{state.error}</div>}
        {state.status === 'loading' && <div className="admin-list">{[0, 1].map((i) => <div key={i} className="block skeleton" style={{ minHeight: 150 }} />)}</div>}
        {state.status === 'done' && state.list.length === 0 && (
          <div className="empty"><strong>No requests here</strong><span>New requests appear automatically.</span></div>
        )}

        <div className="admin-list">
          {state.list.map((b) => {
            const rate = RATE_TYPES.find((r) => r.value === b.rateType)
            const busy = busyId === b.id
            return (
              <article key={b.id} className="block admin-card">
                <header className="admin-card-head">
                  <strong>Request #{b.id}</strong>
                  <span className="muted small">{when(b.createdAt)}</span>
                  <StatusChip label={STATUS_TEXT[b.status]} tone={STATUS_TONE[b.status]} />
                </header>

                <div className="admin-people">
                  <div>
                    <span className="label">Care seeker</span>
                    <strong>{b.seeker.name}</strong>
                    <a href={`tel:${b.seekerPhone.replace(/[^0-9+]/g, '')}`}>{b.seekerPhone}</a>
                    <span className="muted small">{b.seeker.email}</span>
                  </div>
                  <div>
                    <span className="label">Chosen caregiver</span>
                    <strong>{b.caregiver.name}</strong>
                    <span className="muted small">{jobLabel(b.jobType)}</span>
                    <span className="muted small">{b.caregiver.email}</span>
                  </div>
                  <div>
                    <span className="label">Charge asked for</span>
                    {rate ? <strong>{rate.label} · {formatMoney(b.quotedAmount)}</strong> : <span className="muted">Not chosen, discuss</span>}
                    {b.note && <span className="admin-note">"{b.note}"</span>}
                  </div>
                </div>

                <footer className="admin-card-foot">
                  <span className="alert-state">
                    {b.alert ? <StatusChip label={alertLabel(b.alert)} tone={ALERT_TONE[b.alert.status]} /> : <span className="muted small">No alert recorded</span>}
                    {b.alert?.error && <span className="error-text small">{b.alert.error}</span>}
                    {b.alert && (b.alert.status === 'FAILED' || b.alert.status === 'DRY_RUN') && (
                      <button type="button" className="link" disabled={busy} onClick={() => resend(b.id)}>Send alert again</button>
                    )}
                  </span>

                  <span className="admin-actions">
                    {(NEXT[b.status] ?? []).map((a) => (
                      a.danger && confirmCancel !== b.id ? (
                        <button key={a.to} type="button" className="btn-ghost" disabled={busy} onClick={() => setConfirmCancel(b.id)}>{a.label}</button>
                      ) : (
                        <button key={a.to} type="button" className={a.primary ? 'btn-primary small-btn' : 'btn-ghost danger'} disabled={busy}
                                onClick={() => setStatus(b.id, a.to)}>
                          {a.danger ? 'Yes, cancel this booking' : a.label}
                        </button>
                      )
                    ))}
                  </span>
                </footer>
              </article>
            )
          })}
        </div>
      </main>
    </div>
  )
}
