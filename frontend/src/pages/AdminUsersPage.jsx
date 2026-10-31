import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import AdminShell from '../components/AdminShell'
import AdminProfileEditorModal from '../components/AdminProfileEditorModal'
import { Alert } from '../components/Icons'
import StatusChip from '../components/StatusChip'
import { jobLabel } from '../constants/jobs'

const FILTERS = [
  { value: '', label: 'Everyone' },
  { value: 'CAREGIVER', label: 'Caregivers' },
  { value: 'CARE_SEEKER', label: 'Care seekers' },
]
const ROLE_LABEL = { CAREGIVER: 'Caregiver', CARE_SEEKER: 'Care seeker' }

const joined = (iso) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`

export default function AdminUsersPage() {
  const [role, setRole] = useState('')
  const [typed, setTyped] = useState('')
  const [q, setQ] = useState('')                       // what is searched for, a moment after typing stops
  const [state, setState] = useState({ status: 'loading', list: [], error: '' })
  const [removing, setRemoving] = useState(null)       // { user, preview, typed, busy, error }
  const [editingId, setEditingId] = useState(null)
  const [notice, setNotice] = useState('')
  const latest = useRef(0)

  useEffect(() => {
    const timer = setTimeout(() => setQ(typed.trim()), 300)
    return () => clearTimeout(timer)
  }, [typed])

  const load = useCallback(() => {
    const mine = ++latest.current                      // a slow answer to an older search must not replace a newer one
    const params = new URLSearchParams()
    if (role) params.set('role', role)
    if (q) params.set('q', q)
    return api(`/admin/users?${params}`)
      .then((list) => { if (mine === latest.current) setState({ status: 'done', list, error: '' }) })
      .catch((e) => { if (mine === latest.current) setState({ status: 'error', list: [], error: e.message }) })
  }, [role, q])

  useEffect(() => { load() }, [load])

  async function startRemoving(user) {
    setNotice('')
    setRemoving({ user, preview: null, typed: '', busy: false, error: '' })
    try {
      const preview = await api(`/admin/users/${user.id}/deletion-preview`)
      setRemoving((r) => (r?.user.id === user.id ? { ...r, preview } : r))
    } catch (e) {
      setRemoving((r) => (r?.user.id === user.id ? { ...r, error: e.message } : r))
    }
  }

  async function confirmRemoving() {
    const { user } = removing
    setRemoving((r) => ({ ...r, busy: true, error: '' }))
    try {
      await api(`/admin/users/${user.id}`, { method: 'DELETE' })
      setState((s) => ({ ...s, list: s.list.filter((u) => u.id !== user.id) }))
      setNotice(`${user.name} was deleted.`)
      setRemoving(null)
    } catch (e) {
      setRemoving((r) => ({ ...r, busy: false, error: e.message }))
    }
  }

  return (
    <AdminShell title="Accounts" subtitle="Find a caregiver or care seeker. Deleting an account removes it, and everything connected to it, for good.">
      <div className="admin-bar">
        <div className="filters" role="tablist">
          {FILTERS.map((f) => (
            <button key={f.value} type="button" role="tab" aria-selected={role === f.value}
                    className={role === f.value ? 'filter on' : 'filter'} onClick={() => setRole(f.value)}>{f.label}</button>
          ))}
        </div>
        <input className="search-input" type="search" placeholder="Search by name or email" aria-label="Search by name or email"
               value={typed} onChange={(e) => setTyped(e.target.value)} />
      </div>

      {notice && <p className="saved" role="status">{notice}</p>}
      {state.status === 'error' && <div className="alert" role="alert"><Alert />{state.error}</div>}
      {state.status === 'loading' && <div className="admin-list"><div className="block skeleton" style={{ minHeight: 150 }} /></div>}
      {state.status === 'done' && state.list.length === 0 && (
        <div className="empty"><strong>No accounts found</strong><span>{q ? 'Try a different name or email.' : 'Accounts appear here when people sign up.'}</span></div>
      )}

      {state.list.length > 0 && (
        <ul className="doc-list">
          {state.list.map((u) => {
            const open = removing?.user.id === u.id
            const editing = editingId === u.id
            return (
              <li key={u.id} className="doc-row" style={{ flexWrap: 'wrap' }}>
                <div className="doc-main">
                  <strong>{u.name} {u.admin && <span className="admin-tag">Admin</span>}</strong>
                  <span className="muted small">{u.email} · joined {joined(u.createdAt)}</span>
                  {u.address && <span className="muted small">Address: {u.address}</span>}
                </div>
                <div className="doc-side">
                  <span className="status-chip off">{ROLE_LABEL[u.role]}</span>
                  {u.role === 'CAREGIVER' && (
                    <>
                      {u.job && <span className="status-chip off">{jobLabel(u.job)}</span>}
                      <StatusChip label={u.listed ? 'Visible to families' : 'Hidden from families'} tone={u.listed ? 'ok' : 'wait'} />
                    </>
                  )}
                  <button
                    type="button"
                    className="btn-ghost small-btn"
                    onClick={() => {
                      setNotice('')
                      setEditingId(editing ? null : u.id)
                    }}
                  >
                    {editing ? 'Close editor' : 'Edit / Manage'}
                  </button>
                  {u.admin ? (
                    <span className="muted small" title="Remove the admin rights first (make-admin --remove), then it can be deleted">Admin account</span>
                  ) : (
                    <button type="button" className="btn-ghost danger small-btn" disabled={open} onClick={() => startRemoving(u)}>Delete</button>
                  )}
                </div>

                {editing && (
                  <div style={{ width: '100%' }}>
                    <AdminProfileEditorModal
                      userId={u.id}
                      onClose={() => setEditingId(null)}
                      onSaved={(updated) => {
                        setNotice(`Saved changes for ${updated.fullName}.`)
                        load()
                      }}
                    />
                  </div>
                )}

                {open && (
                  <div className="delete-panel" role="alertdialog" aria-label={`Delete ${u.name}`}>
                    <strong>Delete {u.name} for good?</strong>
                    {!removing.preview && !removing.error && <span className="muted small">Checking what is connected to this account…</span>}
                    {removing.preview && <Consequences preview={removing.preview} />}
                    <label>
                      To confirm, type their email: <b>{u.email}</b>
                      <input value={removing.typed} autoComplete="off" autoFocus
                             onChange={(e) => setRemoving({ ...removing, typed: e.target.value })} />
                    </label>
                    {removing.error && <span className="error-text small" role="alert">{removing.error}</span>}
                    <span className="doc-reject-actions">
                      <button type="button" className="btn-primary small-btn danger-solid" onClick={confirmRemoving}
                              disabled={removing.busy || !removing.preview || removing.typed.trim().toLowerCase() !== u.email.toLowerCase()}>
                        {removing.busy ? 'Deleting…' : 'Delete permanently'}
                      </button>
                      <button type="button" className="link" disabled={removing.busy} onClick={() => setRemoving(null)}>Cancel</button>
                    </span>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </AdminShell>
  )
}

/** The plain list of what disappears with the account, so the admin never deletes blind. */
function Consequences({ preview: p }) {
  const items = [
    p.role === 'CAREGIVER' && 'Their profile, charges and photo',
    p.documents > 0 && `${plural(p.documents, 'uploaded document')} (the files are deleted too)`,
    p.requests > 0 && `${plural(p.requests, 'booking request')}${p.openRequests > 0 ? `, ${p.openRequests} still open` : ''}`,
    p.reviewsReceived > 0 && `${plural(p.reviewsReceived, 'review')} about them`,
    p.reviewsWritten > 0 && `${plural(p.reviewsWritten, 'review')} they wrote`,
  ].filter(Boolean)
  return (
    <>
      <span className="small">This permanently removes their account and sign-in{items.length > 0 ? ', and with it:' : '.'}</span>
      {items.length > 0 && <ul className="delete-list">{items.map((i) => <li key={i}>{i}</li>)}</ul>}
      <span className="small">It cannot be undone. They are signed out at once and could sign up again later as a new account.</span>
    </>
  )
}
