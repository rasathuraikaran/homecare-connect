import { useState } from 'react'
import { api } from '../api/client'
import { STATUS_LABEL, STATUS_TONE } from '../constants/bookings'
import { jobLabel } from '../constants/jobs'
import { RATE_TYPES, formatMoney } from '../constants/rates'
import { Alert } from './Icons'
import StatusChip from './StatusChip'

const fmt = (iso) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

/** The care seeker's own requests, newest first. A request that is still waiting can be cancelled. */
export default function MyRequests({ items, reload }) {
  const [confirming, setConfirming] = useState(null)
  const [error, setError] = useState('')

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
            </li>
          )
        })}
      </ul>
    </section>
  )
}
