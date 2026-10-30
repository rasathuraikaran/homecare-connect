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
const RECEIPT_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
const MAX_RECEIPT = 5 * 1024 * 1024

/** The care seeker's own requests, newest first. A request that is still waiting can be cancelled, and a completed visit can be paid for. */
export default function MyRequests({ items, reload }) {
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState(null)
  const [paying, setPaying] = useState(null)
  const [ways, setWays] = useState({ payhere: false, bank: false })   // which ways of paying the admin has switched on
  const [bankOpen, setBankOpen] = useState(null)                       // the request whose bank details are showing
  const [bankInfo, setBankInfo] = useState({})
  const [draft, setDraft] = useState({})                               // per request: the receipt file and note being typed
  const [sending, setSending] = useState(null)
  const [sentCount, setSentCount] = useState(0)                        // changes after a send, so the file field is emptied
  const [error, setError] = useState('')

  useEffect(() => { api('/payments/config').then((c) => setWays({ payhere: c.payhere, bank: c.bank })).catch(() => {}) }, [])

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

  async function toggleBank(id) {
    if (bankOpen === id) {
      setBankOpen(null)
      return
    }
    setError('')
    try {
      const info = await api(`/bookings/${id}/pay/bank`)
      setBankInfo((m) => ({ ...m, [id]: info }))
      setBankOpen(id)
    } catch (e) {
      setError(e.message)
    }
  }

  async function sendReceipt(e, id) {
    e.preventDefault()
    const { file, note } = draft[id] ?? {}
    if (!file) return setError('Choose the receipt (a screenshot or PDF of your transfer) first.')
    if (!RECEIPT_TYPES.includes(file.type)) return setError('The receipt must be a JPG, PNG, WebP or PDF file.')
    if (file.size > MAX_RECEIPT) return setError('The receipt must be 5 MB or smaller.')
    setError('')
    setSending(id)
    try {
      const form = new FormData()
      form.append('file', file)
      if (note) form.append('note', note)
      await api(`/bookings/${id}/pay/receipt`, { method: 'POST', form })
      setDraft((d) => ({ ...d, [id]: {} }))
      setSentCount((n) => n + 1)
      reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setSending(null)
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
                <>
                  <div className="bk-pay">
                    <span className="bk-pay-text">
                      <strong>{b.payment.caregiverAmount != null ? 'Amount to pay' : 'Booking fee'} {formatMoney(b.payment.total)}</strong>
                      {b.payment.caregiverAmount != null && (
                        <span className="muted small">Caregiver {formatMoney(b.payment.caregiverAmount)} + service fee {formatMoney(b.payment.fee)}</span>
                      )}
                      {b.payment.receipts?.length > 0 && (
                        <span className="receipt-sent">Receipt sent {fmt(b.payment.receipts[b.payment.receipts.length - 1].uploadedAt)}. We will mark it paid when the money reaches our account.</span>
                      )}
                      <span className="muted small">
                        {ways.payhere ? (PAYMENT_HELP[b.payment.lastAttempt] ?? 'Pay securely by card through PayHere.')
                          : ways.bank ? 'Send the amount to our bank account. We mark it paid when it arrives.'
                            : 'Our team will contact you about how to pay.'}
                      </span>
                    </span>
                    <span className="bk-pay-actions">
                      {b.payment.receipts?.length > 0 && <StatusChip label="Pending admin approval" tone="wait" />}
                      {ways.payhere && (
                        <button type="button" className="btn-primary small-btn" disabled={paying === b.id} onClick={() => pay(b.id)}>
                          {paying === b.id ? 'Opening PayHere…' : ['FAILED', 'CANCELED', 'CHARGEDBACK'].includes(b.payment.lastAttempt) ? 'Try again' : 'Pay now'}
                        </button>
                      )}
                      {ways.bank && (
                        <button type="button" className={ways.payhere ? 'btn-ghost' : 'btn-primary small-btn'} onClick={() => toggleBank(b.id)}>
                          {bankOpen === b.id ? 'Hide bank details' : 'Pay by bank transfer'}
                        </button>
                      )}
                    </span>
                  </div>
                  {bankOpen === b.id && bankInfo[b.id] && (
                    <dl className="bank-box">
                      <div><dt>Bank</dt><dd>{bankInfo[b.id].bank}{bankInfo[b.id].branch ? `, ${bankInfo[b.id].branch}` : ''}</dd></div>
                      <div><dt>Account name</dt><dd>{bankInfo[b.id].accountName}</dd></div>
                      <div><dt>Account number</dt><dd><code>{bankInfo[b.id].accountNumber}</code></dd></div>
                      <div><dt>Amount</dt><dd>{formatMoney(bankInfo[b.id].total)}</dd></div>
                      <div><dt>Reference</dt><dd><code>{bankInfo[b.id].reference}</code> <span className="muted small">write this with your transfer so we can match it</span></dd></div>
                    </dl>
                  )}
                  {bankOpen === b.id && bankInfo[b.id] && (
                    <form className="receipt-form" onSubmit={(e) => sendReceipt(e, b.id)}>
                      <strong>Paid by bank transfer? Send us the receipt</strong>
                      <span className="muted small">A screenshot or PDF of the transfer (JPG, PNG, WebP or PDF, up to 5 MB). We check our bank account, then mark your payment as paid.</span>
                      <input key={sentCount} type="file" accept="image/jpeg,image/png,image/webp,application/pdf" aria-label="Receipt file"
                             onChange={(e) => setDraft((d) => ({ ...d, [b.id]: { ...d[b.id], file: e.target.files[0] } }))} />
                      <input type="text" maxLength={300} placeholder="Note (optional), for example the bank you paid from" aria-label="Note"
                             value={draft[b.id]?.note ?? ''} onChange={(e) => setDraft((d) => ({ ...d, [b.id]: { ...d[b.id], note: e.target.value } }))} />
                      <button type="submit" className="btn-primary small-btn" disabled={sending === b.id}>{sending === b.id ? 'Sending…' : 'Send receipt'}</button>
                    </form>
                  )}
                </>
              )}
              {b.payment?.state === 'PAID' && (
                <div className="bk-pay paid">
                  <span className="bk-pay-text">
                    <strong>{b.payment.caregiverAmount != null ? 'Amount paid' : 'Booking fee'} {formatMoney(b.payment.total)}</strong>
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
