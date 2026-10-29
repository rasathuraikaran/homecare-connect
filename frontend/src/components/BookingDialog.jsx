import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { jobLabel } from '../constants/jobs'
import { formatMoney, ratesList } from '../constants/rates'
import Field from './Field'
import { Alert } from './Icons'

const PHONE = /^\+?[0-9 ()-]{7,20}$/   // same rule as the server

/** A care seeker confirms they want this caregiver. Our team (the admin) is alerted and arranges it. */
export default function BookingDialog({ caregiver, defaultPhone, onClose, onBooked, onViewRequests }) {
  const ref = useRef(null)
  const [rateType, setRateType] = useState('')
  const [phone, setPhone] = useState(defaultPhone ?? '')
  const [address, setAddress] = useState('')
  const [note, setNote] = useState('')
  const [issues, setIssues] = useState([])
  const [busy, setBusy] = useState(false)
  const [booking, setBooking] = useState(null)
  const rates = ratesList(caregiver.rates)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog.open) dialog.showModal()
  }, [])

  async function submit(e) {
    e.preventDefault()
    if (!PHONE.test(phone.trim())) {
      setIssues(['Enter a valid phone number so our team can reach you'])
      return
    }
    if (!address.trim()) {
      setIssues(['Enter the address where the care is needed'])
      return
    }
    setIssues([])
    setBusy(true)
    try {
      const b = await api('/bookings', {
        method: 'POST',
        body: { caregiverId: caregiver.id, rateType: rateType || null, phone: phone.trim(), address: address.trim(), note },
      })
      setBooking(b)
      onBooked()
    } catch (err) {
      setIssues(err.errors?.length ? err.errors : [err.message])
    } finally {
      setBusy(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" onClose={onClose}
            onClick={(e) => { if (e.target === ref.current) ref.current.close() }}>
      <div className="dialog-body">
        <header className="dialog-head">
          <div>
            <h2>{booking ? 'Request sent' : `Request ${caregiver.fullName}`}</h2>
            <span className="muted">{jobLabel(caregiver.jobType)}</span>
          </div>
          <button type="button" className="btn-ghost" onClick={() => ref.current.close()}>Close</button>
        </header>

        {booking ? (
          <div className="bk-done" role="status">
            <p><strong>Thank you! Your request #{booking.id} is with our team.</strong></p>
            <p>We will call you on <strong>{phone.trim()}</strong> to arrange this with {caregiver.fullName}.
               Nothing is charged at this step.</p>
            <div className="bk-actions">
              <button type="button" className="btn-primary" onClick={() => { ref.current.close(); onViewRequests() }}>View my requests</button>
            </div>
          </div>
        ) : (
          <form onSubmit={submit} className="bk-form" noValidate>
            <p className="muted small">
              Confirm your request and our team will contact you to arrange it. Nothing is charged at this step.
            </p>

            {rates.length > 0 && (
              <fieldset className="bk-rates">
                <legend>Which charge would you like?</legend>
                <label className="bk-rate"><input type="radio" name="rate" checked={rateType === ''} onChange={() => setRateType('')} />
                  <span>Not sure yet, discuss with our team</span></label>
                {rates.map((r) => (
                  <label key={r.value} className="bk-rate">
                    <input type="radio" name="rate" checked={rateType === r.value} onChange={() => setRateType(r.value)} />
                    <span>{r.label}<strong>{formatMoney(r.amount)}</strong></span>
                  </label>
                ))}
              </fieldset>
            )}

            <Field label="Your phone number *" type="tel" autoComplete="tel" placeholder="e.g. +94 77 123 4567"
                   hint="Our team will call or message you on this number." value={phone} onChange={(e) => setPhone(e.target.value)} />

            <div className="field">
              <label htmlFor="bk-address">Address where care is needed *</label>
              <textarea id="bk-address" rows={2} maxLength={300} autoComplete="street-address" placeholder="House number, street, town"
                        value={address} onChange={(e) => setAddress(e.target.value)} />
              <span className="hint">Our team uses this to arrange the visit. Only they see it.</span>
            </div>

            <div className="field">
              <label htmlFor="bk-note">Anything we should know? (optional)</label>
              <textarea id="bk-note" rows={3} maxLength={500} placeholder="For example: mornings only, care for my mother, needs help walking"
                        value={note} onChange={(e) => setNote(e.target.value)} />
              <span className="hint">{note.length}/500</span>
            </div>

            {issues.length > 0 && (
              <div className="banner" role="alert"><Alert /><div><strong>{issues.length === 1 ? 'We could not send your request' : 'Please fix these problems'}</strong>
                <ul>{issues.map((m) => <li key={m}>{m}</li>)}</ul></div></div>
            )}
            <button className="btn-primary" disabled={busy}>{busy && <span className="spinner" />}{busy ? 'Sending…' : 'Confirm request'}</button>
          </form>
        )}
      </div>
    </dialog>
  )
}
