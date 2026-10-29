import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { jobLabel } from '../constants/jobs'
import { Alert } from './Icons'

const JOBS = ['CARETAKER', 'NURSING', 'PHYSIOTHERAPIST']
const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`

/**
 * The admin decides which documents are mandatory for which job. Everything is optional until it is switched on.
 * Before a change that would hide or show caregivers, the panel says how many and asks to confirm.
 */
export default function DocumentRulesPanel({ onChanged }) {
  const [rules, setRules] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [asking, setAsking] = useState(null)   // { rule, next, impact }
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api('/document-requirements').then(setRules).catch((e) => setError(e.message))
  }, [])

  async function toggle(rule) {
    setNotice('')
    setError('')
    const next = !rule.required
    try {
      const impact = await api(`/admin/document-requirements/impact?job=${rule.job}&type=${rule.type}&required=${next}`)
      if (impact.willHide > 0 || impact.willShow > 0) setAsking({ rule, next, impact })
      else await apply(rule, next)
    } catch (e) {
      setError(e.message)
    }
  }

  async function apply(rule, next) {
    setBusy(true)
    setError('')
    try {
      const result = await api('/admin/document-requirements', { method: 'PUT', body: { job: rule.job, type: rule.type, required: next } })
      setRules(result.requirements)
      setAsking(null)
      const effects = [
        result.hidden > 0 && `${plural(result.hidden, 'caregiver')} hidden from families until approved`,
        result.shown > 0 && `${plural(result.shown, 'caregiver')} now visible to families`,
      ].filter(Boolean)
      setNotice(`${rule.label} is now ${next ? 'mandatory' : 'optional'} for ${jobLabel(rule.job)}.${effects.length ? ` ${effects.join(', ')}.` : ''}`)
      onChanged?.()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="block rules" aria-labelledby="rules-title">
      <div>
        <h2 id="rules-title">Document rules</h2>
        <p className="muted small">
          Documents are optional by default: caregivers are visible to families without them, and an approved document earns a verified mark.
          Switch a document on to make it mandatory for that job. Caregivers who do not have it approved are then hidden from families.
        </p>
      </div>
      {error && <div className="alert" role="alert"><Alert />{error}</div>}
      {notice && <p className="saved" role="status">{notice}</p>}
      {!rules && !error && <div className="skeleton" style={{ minHeight: 90 }} />}
      {rules && (
        <div className="rules-grid">
          {JOBS.map((job) => (
            <div key={job} className="rules-job">
              <h3>{jobLabel(job)}</h3>
              {rules.filter((r) => r.job === job).map((r) => (
                <div key={r.type} style={{ display: 'contents' }}>
                  <div className="rule-row">
                    <div className="doc-main">
                      <strong>{r.label}</strong>
                      <span className={r.required ? 'error-text small' : 'muted small'}>{r.required ? 'Mandatory' : 'Optional'}</span>
                    </div>
                    <button type="button" role="switch" aria-checked={r.required} aria-label={`${r.label} is mandatory for ${jobLabel(job)}`}
                            className="switch" disabled={busy} onClick={() => toggle(r)}><span /></button>
                  </div>
                  {asking?.rule.job === r.job && asking.rule.type === r.type && (
                    <div className="rule-confirm" role="alertdialog" aria-label="Confirm the change">
                      <span>
                        {asking.next
                          ? `${plural(asking.impact.willHide, 'caregiver')} that families can see now will be hidden until this document is approved.`
                          : `${plural(asking.impact.willShow, 'hidden caregiver')} will become visible to families.`}
                      </span>
                      <span className="doc-reject-actions">
                        <button type="button" className="btn-primary small-btn" disabled={busy} onClick={() => apply(asking.rule, asking.next)}>
                          {asking.next ? 'Make mandatory' : 'Make optional'}
                        </button>
                        <button type="button" className="link" disabled={busy} onClick={() => setAsking(null)}>Cancel</button>
                      </span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
