import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Check } from './Icons'
import StatusChip from './StatusChip'
import { DOCUMENT_FILE_TYPES, DOC_LABEL, DOC_TONE, HEADLINE, MAX_DOCUMENT, isListed } from '../constants/verification'

/** The caregiver's checklist of documents. onStatus(true/false) tells the page whether families can see them yet. */
export default function VerificationCard({ onStatus }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(null)               // the document type being uploaded or removed
  const [rowError, setRowError] = useState({})
  const [confirmRemove, setConfirmRemove] = useState(null)
  const fileRef = useRef(null)
  const target = useRef(null)

  // the server decides whether families can see the caregiver, so tell the page whenever it answers
  const apply = (v) => { setView(v); onStatus?.(isListed(v.status)) }
  const fail = (type, message) => setRowError((r) => ({ ...r, [type]: message }))

  useEffect(() => {
    api('/caregivers/me/verification').then(apply).catch((e) => setError(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function pick(type) {
    target.current = type
    fileRef.current.click()
  }

  async function onFile(e) {
    const file = e.target.files[0]
    e.target.value = ''
    const type = target.current
    if (!file || !type) return
    if (!DOCUMENT_FILE_TYPES.includes(file.type)) return fail(type, 'Choose a JPG, PNG, WebP or PDF file')
    if (file.size > MAX_DOCUMENT) return fail(type, 'The file must be 5 MB or smaller')
    setBusy(type)
    fail(type, '')
    try {
      const form = new FormData()
      form.append('file', file)
      apply(await api(`/caregivers/me/documents/${type}`, { method: 'POST', form }))
    } catch (err) {
      fail(type, err.message)
    } finally {
      setBusy(null)
    }
  }

  async function remove(type) {
    setConfirmRemove(null)
    setBusy(type)
    fail(type, '')
    try {
      apply(await api(`/caregivers/me/documents/${type}`, { method: 'DELETE' }))
    } catch (err) {
      fail(type, err.message)
    } finally {
      setBusy(null)
    }
  }

  if (error) return <section className="block"><h2>Verification</h2><p className="error-text" role="alert">{error}</p></section>
  if (!view) return <section className="block skeleton" style={{ minHeight: 160 }} />

  const [headline, detail] = view.job
    ? HEADLINE[view.status]
    : ['Choose your job first', 'The documents you need depend on your job. Choose it in your profile, then come back here.']

  return (
    <section className={view.status === 'VERIFIED' ? 'block availability on' : 'block'} aria-labelledby="verification-title">
      <div className="block-head">
        <h2 id="verification-title">{view.status === 'VERIFIED' && <span className="tick"><Check /></span>} {headline}</h2>
      </div>
      <p className="muted small">{detail}</p>
      {!view.job && <Link to="/profile?focus=job" className="btn-primary link-btn">Choose your job</Link>}

      {view.documents.length > 0 && (
        <ul className="doc-list">
          {view.documents.map((d) => (
            <li key={d.type} className="doc-row">
              <div className="doc-main">
                <strong>{d.label} {d.required ? <span className="required-tag">Required</span> : <span className="optional">Optional</span>}</strong>
                <span className="muted small">{d.hint}</span>
                {d.status === 'REJECTED' && d.rejectReason && <span className="error-text small">Rejected: {d.rejectReason}</span>}
                {rowError[d.type] && <span className="error-text small" role="alert">{rowError[d.type]}</span>}
              </div>
              <div className="doc-side">
                <StatusChip label={d.status ? DOC_LABEL[d.status] : 'Not uploaded'} tone={d.status ? DOC_TONE[d.status] : 'off'} />
                <button type="button" className="btn-ghost small-btn" disabled={busy === d.type} onClick={() => pick(d.type)}>
                  {busy === d.type ? 'Working…' : d.status ? 'Replace' : 'Upload'}
                </button>
                {d.status && (confirmRemove === d.type ? (
                  <button type="button" className="btn-ghost danger small-btn" disabled={busy === d.type} onClick={() => remove(d.type)}>Yes, remove</button>
                ) : (
                  <button type="button" className="link small" disabled={busy === d.type} onClick={() => setConfirmRemove(d.type)}>Remove</button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="muted small">JPG, PNG, WebP or PDF, up to 5 MB. Only our verification team can open your documents. They are never shown to families.</p>
      <input ref={fileRef} type="file" accept={DOCUMENT_FILE_TYPES.join(',')} hidden onChange={onFile} />
    </section>
  )
}
