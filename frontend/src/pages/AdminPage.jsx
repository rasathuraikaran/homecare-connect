import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import AdminShell from '../components/AdminShell'
import { Alert } from '../components/Icons'
import StatusChip from '../components/StatusChip'
import { alertLabel, ALERT_TONE, ATTEMPT_TEXT, STATUS_TONE } from '../constants/bookings'
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
const FEE_PERCENT = 5   // what is suggested when a visit is completed; the admin can type anything

const suggestedFee = (b) => (b.quotedAmount != null ? (Math.round(b.quotedAmount * FEE_PERCENT) / 100).toFixed(2) : '')

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export default function AdminPage() {
  const [filter, setFilter] = useState('REQUESTED')
  const [state, setState] = useState({ status: 'loading', list: [], error: '' })
  const [busyId, setBusyId] = useState(null)
  const [confirmCancel, setConfirmCancel] = useState(null)
  const [panel, setPanel] = useState(null)   // { id, kind: 'complete' | 'fee' | 'paid', fee }
  const [payOn, setPayOn] = useState(false)
  const [actionError, setActionError] = useState('')

  // whether online payments are on decides if completing a visit asks for a fee
  useEffect(() => { api('/payments/config').then((c) => setPayOn(c.mode !== 'OFF')).catch(() => {}) }, [])

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
      setPanel(null)
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  const setStatus = (id, status, fee) => run(id, () => api(`/admin/bookings/${id}/status`, { method: 'PATCH', body: fee == null ? { status } : { status, fee } }))
  const resend = (id) => run(id, () => api(`/admin/bookings/${id}/resend-alert`, { method: 'POST' }))
  const changeFee = (id, fee) => run(id, () => api(`/admin/bookings/${id}/fee`, { method: 'PUT', body: { fee } }))
  const resendPayment = (id) => run(id, () => api(`/admin/bookings/${id}/payment/resend`, { method: 'POST' }))
  const markPaid = (id) => run(id, () => api(`/admin/bookings/${id}/payment/mark-paid`, { method: 'POST' }))

  function submitFee(e, b) {
    e.preventDefault()
    const fee = Number(panel.fee)
    if (!(fee > 0)) {
      setActionError('Enter a fee of more than zero.')
      return
    }
    if (panel.kind === 'complete') setStatus(b.id, 'COMPLETED', fee)
    else changeFee(b.id, fee)
  }

  // with payments on, completing a visit first asks what the care seeker should pay
  function choose(b, to) {
    setActionError('')
    if (to === 'COMPLETED' && payOn) setPanel({ id: b.id, kind: 'complete', fee: suggestedFee(b) })
    else setStatus(b.id, to)
  }

  return (
    <AdminShell title="Booking requests" subtitle="Call the care seeker, arrange it with the caregiver, then update the status here.">
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
                  <span className="small"><span className="label">Care needed at</span> {b.address ?? 'Not given (older request)'}</span>
                </div>
                <div>
                  <span className="label">Chosen caregiver</span>
                  <strong>{b.caregiver.name}</strong>
                  <span className="muted small">{jobLabel(b.jobType)}</span>
                  <span className="muted small">{b.caregiver.email}</span>
                  {b.caregiverAddress && <span className="muted small">Lives at {b.caregiverAddress}</span>}
                </div>
                <div>
                  <span className="label">Charge asked for</span>
                  {rate ? <strong>{rate.label} · {formatMoney(b.quotedAmount)}</strong> : <span className="muted">Not chosen, discuss</span>}
                  {b.note && <span className="admin-note">"{b.note}"</span>}
                </div>
              </div>

              {b.payment && b.payment.state !== 'NONE' && (
                <section className="admin-pay" aria-label="Booking fee">
                  <div className="admin-pay-line">
                    <span className="label">Booking fee</span>
                    <strong>{formatMoney(b.payment.fee)}</strong>
                    <StatusChip label={b.payment.state === 'PAID' ? 'Paid' : 'Unpaid'} tone={b.payment.state === 'PAID' ? 'ok' : 'wait'} />
                    {b.payment.state === 'PAID' && (
                      <span className="muted small">{when(b.payment.paidAt)} · {b.payment.source === 'MANUAL' ? 'recorded by an admin, outside PayHere' : 'through PayHere'}</span>
                    )}
                    {b.payment.state === 'DUE' && b.payment.lastAttempt && <span className="muted small">{ATTEMPT_TEXT[b.payment.lastAttempt]}</span>}
                  </div>
                  {b.payment.needsAttention && (
                    <div className="alert" role="alert"><Alert />Check this fee in the PayHere dashboard: it was paid more than once, charged back, or paid in a different amount than the fee now set. Refund or fix it there.</div>
                  )}
                  {b.payment.state === 'DUE' && (
                    <div className="admin-pay-actions">
                      {payOn && <button type="button" className="link" disabled={busy} onClick={() => resendPayment(b.id)}>Send payment email again</button>}
                      <button type="button" className="link" disabled={busy} onClick={() => setPanel({ id: b.id, kind: 'fee', fee: String(b.payment.fee) })}>Change fee</button>
                      <button type="button" className="link" disabled={busy} onClick={() => setPanel({ id: b.id, kind: 'paid' })}>Mark paid outside PayHere</button>
                    </div>
                  )}
                </section>
              )}

              {panel?.id === b.id && panel.kind !== 'paid' && (
                <form className="fee-panel" onSubmit={(e) => submitFee(e, b)}>
                  <label>
                    Fee the care seeker pays (LKR)
                    <input type="number" inputMode="decimal" min="0.01" step="0.01" max="9999999.99" required autoFocus
                           value={panel.fee} onChange={(e) => setPanel({ ...panel, fee: e.target.value })} />
                  </label>
                  <span className="hint">
                    {panel.kind === 'complete'
                      ? (b.quotedAmount != null
                        ? `Suggested: ${FEE_PERCENT}% of the ${formatMoney(b.quotedAmount)} charge. Change it if you agreed something else.`
                        : 'No charge was chosen for this request, so type the fee you agreed.')
                      : 'The care seeker is emailed the new amount.'}
                  </span>
                  <div className="fee-panel-actions">
                    <button type="submit" className="btn-primary small-btn" disabled={busy}>
                      {panel.kind === 'complete' ? 'Mark completed and ask for payment' : 'Save fee and tell the care seeker'}
                    </button>
                    <button type="button" className="btn-ghost" onClick={() => setPanel(null)}>Back</button>
                  </div>
                </form>
              )}

              {panel?.id === b.id && panel.kind === 'paid' && (
                <div className="fee-panel">
                  <span>Did {b.seeker.name} pay {formatMoney(b.payment.fee)} another way, such as a bank deposit or cash? This cannot be undone here.</span>
                  <div className="fee-panel-actions">
                    <button type="button" className="btn-primary small-btn" disabled={busy} onClick={() => markPaid(b.id)}>Yes, mark it paid</button>
                    <button type="button" className="btn-ghost" onClick={() => setPanel(null)}>Not yet</button>
                  </div>
                </div>
              )}

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
                              onClick={() => choose(b, a.to)}>
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
    </AdminShell>
  )
}
