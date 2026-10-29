import { useCallback, useEffect, useState } from 'react'
import { api, apiBlob } from '../api/client'
import AdminShell from '../components/AdminShell'
import DocumentRulesPanel from '../components/DocumentRulesPanel'
import { Alert } from '../components/Icons'
import StatusChip from '../components/StatusChip'
import { jobLabel } from '../constants/jobs'
import { DOC_LABEL, DOC_TONE, OVERALL_LABEL, OVERALL_TONE } from '../constants/verification'

const FILTERS = [
  { value: true, label: 'Needs review' },
  { value: false, label: 'All caregivers with documents' },
]
const POLL_MS = 30_000

const when = (iso) => new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

export default function AdminVerificationPage() {
  const [needsReview, setNeedsReview] = useState(true)
  const [state, setState] = useState({ status: 'loading', list: [], error: '' })
  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState('')
  const [rejecting, setRejecting] = useState(null)   // { docId, reason }

  const load = useCallback((silent = false) => {
    if (!silent) setState((s) => ({ ...s, status: 'loading' }))
    return api(`/admin/verifications?needsReview=${needsReview}`)
      .then((list) => setState({ status: 'done', list, error: '' }))
      .catch((e) => { if (!silent) setState({ status: 'error', list: [], error: e.message }) })
  }, [needsReview])

  useEffect(() => {
    load()
    const timer = setInterval(() => load(true), POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  function applyUpdate(updated) {
    setState((s) => ({
      ...s,
      list: needsReview && !updated.documents.some((d) => d.status === 'PENDING')
        ? s.list.filter((c) => c.userId !== updated.userId)
        : s.list.map((c) => (c.userId === updated.userId ? updated : c)),
    }))
  }

  async function review(doc, status, reason) {
    setBusyId(doc.id)
    setActionError('')
    try {
      applyUpdate(await api(`/admin/documents/${doc.id}`, { method: 'PATCH', body: { status, reason: reason ?? null, revision: doc.revision } }))
      setRejecting(null)
    } catch (e) {
      setActionError(e.message)
      load(true)   // for example the caregiver uploaded a new file meanwhile: show it
    } finally {
      setBusyId(null)
    }
  }

  // Open the tab first, while the click still counts, then point it at the file once it has been fetched with the sign-in token
  async function view(doc) {
    const tab = window.open('about:blank', '_blank')
    if (!tab) {
      setActionError('Your browser blocked the new tab. Allow pop-ups for this site, then try again.')
      return
    }
    setBusyId(doc.id)
    setActionError('')
    try {
      const url = URL.createObjectURL(await apiBlob(`/admin/documents/${doc.id}/file`))
      tab.location.href = url
      setTimeout(() => URL.revokeObjectURL(url), 10 * 60_000)
    } catch (e) {
      tab.close()
      setActionError(e.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <AdminShell title="Caregiver verification" subtitle="Open each document, then approve it or say what is wrong. Decide below which documents are mandatory for each job.">
      <DocumentRulesPanel onChanged={() => load(true)} />

      <div className="admin-bar">
        <div className="filters" role="tablist">
          {FILTERS.map((f) => (
            <button key={String(f.value)} type="button" role="tab" aria-selected={needsReview === f.value}
                    className={needsReview === f.value ? 'filter on' : 'filter'} onClick={() => setNeedsReview(f.value)}>{f.label}</button>
          ))}
        </div>
        <button type="button" className="btn-ghost" onClick={() => load()}>Refresh</button>
      </div>

      {actionError && <div className="alert" role="alert"><Alert />{actionError}</div>}
      {state.status === 'error' && <div className="alert" role="alert"><Alert />{state.error}</div>}
      {state.status === 'loading' && <div className="admin-list">{[0, 1].map((i) => <div key={i} className="block skeleton" style={{ minHeight: 150 }} />)}</div>}
      {state.status === 'done' && state.list.length === 0 && (
        <div className="empty">
          <strong>{needsReview ? 'Nothing to review' : 'No documents uploaded yet'}</strong>
          <span>New uploads appear automatically.</span>
        </div>
      )}

      <div className="admin-list">
        {state.list.map((c) => (
          <article key={c.userId} className="block admin-card">
            <header className="admin-card-head">
              <strong>{c.name}</strong>
              <span className="muted small">{c.job ? jobLabel(c.job) : 'No job chosen yet'} · {c.email}</span>
              <StatusChip label={OVERALL_LABEL[c.status]} tone={OVERALL_TONE[c.status]} />
            </header>

            <ul className="doc-list">
              {c.documents.map((d) => {
                const busy = busyId === d.id
                return (
                  <li key={d.type} className="doc-row">
                    <div className="doc-main">
                      <strong>{d.label} {!d.required && <span className="optional">Optional</span>}</strong>
                      {d.uploadedAt && <span className="muted small">Uploaded {when(d.uploadedAt)}</span>}
                      {d.status === 'REJECTED' && d.rejectReason && <span className="error-text small">Rejected: {d.rejectReason}</span>}
                    </div>
                    <div className="doc-side">
                      <StatusChip label={d.status ? DOC_LABEL[d.status] : 'Not uploaded'} tone={d.status ? DOC_TONE[d.status] : 'off'} />
                      {d.id && <button type="button" className="btn-ghost small-btn" disabled={busy} onClick={() => view(d)}>View</button>}
                      {d.id && d.status !== 'APPROVED' && (
                        <button type="button" className="btn-primary small-btn" disabled={busy} onClick={() => review(d, 'APPROVED')}>Approve</button>
                      )}
                      {d.id && d.status !== 'REJECTED' && (
                        <button type="button" className="btn-ghost danger small-btn" disabled={busy} onClick={() => setRejecting({ docId: d.id, reason: '' })}>Reject</button>
                      )}
                    </div>

                    {rejecting?.docId === d.id && (
                      <form className="doc-reject" onSubmit={(e) => { e.preventDefault(); review(d, 'REJECTED', rejecting.reason.trim()) }}>
                        <label>
                          Why is it rejected? The caregiver will read this.
                          <input value={rejecting.reason} maxLength={300} required autoFocus
                                 onChange={(e) => setRejecting({ docId: d.id, reason: e.target.value })} />
                        </label>
                        <span className="doc-reject-actions">
                          <button className="btn-primary small-btn" disabled={busy || !rejecting.reason.trim()}>Reject document</button>
                          <button type="button" className="link" onClick={() => setRejecting(null)}>Cancel</button>
                        </span>
                      </form>
                    )}
                  </li>
                )
              })}
            </ul>
          </article>
        ))}
      </div>
    </AdminShell>
  )
}
