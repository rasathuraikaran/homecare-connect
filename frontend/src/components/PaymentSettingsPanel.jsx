import { useState } from 'react'
import { api } from '../api/client'
import { Alert } from './Icons'

const WAYS = [
  { key: 'payhere', field: 'payhereEnabled', title: 'Card payments through PayHere',
    setup: 'Not set up on this server. Add PAYHERE_MODE and the merchant keys to .env.local, then restart.' },
  { key: 'bank', field: 'bankEnabled', title: 'Bank transfer',
    setup: 'Not set up on this server. Add BANK_NAME, BANK_ACCOUNT_NAME and BANK_ACCOUNT_NUMBER to .env.local, then restart.' },
]
const MODE_TEXT = { SIMULATED: 'Pretend PayHere: nothing is charged', SANDBOX: 'PayHere test mode: no real money', LIVE: 'Real card payments' }

/** The admin's two switches for how care seekers may pay. With both off, completing a visit asks for no fee. */
export default function PaymentSettingsPanel({ settings, onChange }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  if (!settings) return null

  async function toggle(way, on) {
    setBusy(true)
    setError('')
    try {
      onChange(await api('/admin/payment-settings', { method: 'PUT', body: { [way.field]: on } }))
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const none = !settings.payhere.on && !settings.bank.on
  return (
    <section className="block pay-settings" aria-labelledby="pay-settings-title">
      <h2 id="pay-settings-title">How care seekers pay</h2>
      {error && <div className="alert" role="alert"><Alert />{error}</div>}
      <ul className="pay-ways">
        {WAYS.map((way) => {
          const { on, available } = settings[way.key]
          const detail = !available ? way.setup : way.key === 'payhere' ? MODE_TEXT[settings.payhereMode] : 'The seeker is shown your bank details and you mark it paid'
          return (
            <li key={way.key}>
              <label className={on ? 'switch-row' : 'switch-row off'}>
                <input type="checkbox" role="switch" checked={on} disabled={!available || busy} onChange={(e) => toggle(way, e.target.checked)} />
                <span><strong>{way.title}</strong><span className="muted small">{detail}</span></span>
              </label>
            </li>
          )
        })}
      </ul>
      <p className="pay-note">
        {none
          ? 'Both are off, so marking a visit completed asks for no fee.'
          : 'Marking a visit completed asks for the caregiver’s charge and our fee. The care seeker pays the total.'}
      </p>
    </section>
  )
}
