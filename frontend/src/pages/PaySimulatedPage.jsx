import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { Alert } from '../components/Icons'
import PayShell from '../components/PayShell'

/** A pretend PayHere for trying the whole flow on one computer (PAYHERE_MODE=simulated). Nothing is charged. */
export default function PaySimulatedPage() {
  const [params] = useSearchParams()
  const id = Number(params.get('booking'))
  const order = params.get('order') ?? ''
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function choose(outcome) {
    setBusy(true)
    setError('')
    try {
      await api('/payments/simulate', { method: 'POST', body: { orderId: order, outcome } })
      navigate(outcome === 'cancel' ? `/pay/cancel?booking=${id}` : `/pay/return?booking=${id}`)
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  return (
    <PayShell>
      <p className="sim-banner" role="note">PayHere simulator. This is a pretend payment page, nothing is charged.</p>
      <h1>Pay the booking fee</h1>
      <p>Request #{id || '?'} · order <code>{order || 'unknown'}</code></p>
      {error && <div className="alert" role="alert"><Alert />{error}</div>}
      <div className="sim-actions">
        <button type="button" className="btn-primary" disabled={busy || !order} onClick={() => choose('success')}>Pay successfully</button>
        <button type="button" className="btn-ghost" disabled={busy || !order} onClick={() => choose('failed')}>Make the payment fail</button>
        <button type="button" className="btn-ghost" disabled={busy || !order} onClick={() => choose('cancel')}>Cancel</button>
      </div>
    </PayShell>
  )
}
