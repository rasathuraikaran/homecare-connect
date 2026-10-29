import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { postToPayHere } from '../api/payhere'
import { PAYMENT_HELP, STATUS_LABEL, STATUS_TONE } from '../constants/bookings'
import { jobLabel } from '../constants/jobs'
import { RATE_TYPES, formatMoney } from '../constants/rates'
import { Alert } from './Icons'
import StatusChip from './StatusChip'

const fmt = (iso) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

/** The care seeker's own requests, newest first. A request that is still waiting can be cancelled, and a completed visit's fee can be paid. */
export default function MyRequests({ items, reload }) {
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(null)
  const [paying, setPaying] = useState(null)
  const [error, setError] = useState('')

  // coming back from PayHere with the Back button restores this page as it was, so the button must not stay stuck
  useEffect(() => {
    const reset = (e) => { if (e.persisted) setPaying(null) }
    window.addEventListener('pageshow', reset)
    return () => window.removeEventListener('pageshow', reset)
  }, [])

  async function pay(id) {
    setError('')
    setPaying(id)
    try {
      const checkout = await api(`/bookings/${id}/pay`, { method: 'POST' })
      if (checkout.mode === 'SIMULATED') {
        navigate(`/pay/simulated?booking=${id}&order=${encodeURIComponent(checkout.orderId)}`)
        return
      }
      postToPayHere(checkout.actionUrl, checkout.fields)
    } catch (e) {
      setError(e.message)
      setPaying(null)
    }
  }

  async function cancel(id) {
    setError('')
    try {
      await api(`/bookings/${id}/cancel`, { method: 'POST' })
      setConfirming(null)
      reload()
    } catch (e) {
      setError(e.message)
    }
  }

  if (!items.length) return null
  return (
    <section id="my-requests" className="block my-requests">
      <h2>Your requests</h2>
      {error && <div className="alert" role="alert"><Alert />{error}</div>}
      <ul className="bk-list">
        {items.map((b) => {
          const rate = RATE_TYPES.find((r) => r.value === b.rateType)
          return (
            <li key={b.id}>
              <div className="bk-main">
                <strong>{b.caregiverName}</strong>
                <span className="muted small">{jobLabel(b.jobType)} · requested {fmt(b.createdAt)} · #{b.id}</span>
                {rate && <span className="muted small">{rate.label} {formatMoney(b.quotedAmount)}</span>}
              </div>
              <StatusChip label={STATUS_LABEL[b.status]} tone={STATUS_TONE[b.status]} />
              {b.status === 'REQUESTED' && (confirming === b.id ? (
                <span className="bk-confirm">
                  <button type="button" className="btn-ghost danger" onClick={() => cancel(b.id)}>Yes, cancel</button>
                  <button type="button" className="btn-ghost" onClick={() => setConfirming(null)}>Keep it</button>
                </span>
              ) : (
                <button type="button" className="btn-ghost" onClick={() => setConfirming(b.id)}>Cancel request</button>
              ))}
              {b.payment?.state === 'DUE' && (
                <div className="bk-pay">
                  <span className="bk-pay-text">
                    <strong>Booking fee {formatMoney(b.payment.fee)}</strong>
                    <span className="muted small">{PAYMENT_HELP[b.payment.lastAttempt] ?? 'Pay securely by card through PayHere.'}</span>
                  </span>
                  <button type="button" className="btn-primary small-btn" disabled={paying === b.id} onClick={() => pay(b.id)}>
                    {paying === b.id ? 'Opening PayHere…' : ['FAILED', 'CANCELED', 'CHARGEDBACK'].includes(b.payment.lastAttempt) ? 'Try again' : 'Pay now'}
                  </button>
                </div>
              )}
              {b.payment?.state === 'PAID' && (
                <div className="bk-pay paid">
                  <span className="bk-pay-text">
                    <strong>Booking fee {formatMoney(b.payment.fee)}</strong>
                    <span className="muted small">Paid on {fmt(b.payment.paidAt)}. Thank you.</span>
                  </span>
                  <StatusChip label="Paid" tone="ok" />
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
