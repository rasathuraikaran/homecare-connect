import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Field from '../components/Field'
import { Alert, HeartHouse } from '../components/Icons'

const MAX_PHOTO = 2 * 1024 * 1024
const TYPES = ['image/jpeg', 'image/png', 'image/webp']

export default function ProfilePage() {
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef(null)
  const firstTime = !user.profileCompleted

  const [form, setForm] = useState({ fullName: user.fullName, age: '', experienceYears: '', qualification: '' })
  const [photoUrl, setPhotoUrl] = useState(null)   // saved photo
  const [newPhoto, setNewPhoto] = useState(null)   // { file, preview }
  const [removePhoto, setRemovePhoto] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(firstTime)

  useEffect(() => {
    if (firstTime) return
    api('/caregivers/me/profile').then((p) => {
      setForm({
        fullName: p.fullName,
        age: p.age ?? '',
        experienceYears: p.experienceYears ?? '',
        qualification: p.qualification ?? '',
      })
      setPhotoUrl(p.photoUrl)
      setLoaded(true)
    }).catch((e) => { setError(e.message); setLoaded(true) })
  }, [firstTime])

  useEffect(() => () => newPhoto && URL.revokeObjectURL(newPhoto.preview), [newPhoto])

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const num = (v) => (v === '' ? null : Number(v))

  function pickPhoto(e) {
    const file = e.target.files[0]
    e.target.value = ''
    if (!file) return
    if (!TYPES.includes(file.type)) return setError('Photo must be a JPG, PNG or WebP image')
    if (file.size > MAX_PHOTO) return setError('Photo must be 2 MB or smaller')
    setError('')
    setRemovePhoto(false)
    setNewPhoto({ file, preview: URL.createObjectURL(file) })
  }

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api('/caregivers/me/profile', {
        method: 'PUT',
        body: {
          fullName: form.fullName,
          age: num(form.age),
          experienceYears: num(form.experienceYears),
          qualification: form.qualification,
        },
      })
      if (newPhoto) {
        const fd = new FormData()
        fd.append('file', newPhoto.file)
        await api('/caregivers/me/photo', { method: 'POST', form: fd })
      } else if (removePhoto && photoUrl) {
        await api('/caregivers/me/photo', { method: 'DELETE' })
      }
      await refresh()
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const shownPhoto = newPhoto?.preview ?? (removePhoto ? null : photoUrl)
  const initials = (form.fullName || '?').split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()

  if (!loaded) return <p className="center">Loading…</p>

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        {firstTime
          ? <button className="btn-ghost" onClick={logout}>Sign out</button>
          : <Link className="btn-ghost" to="/">Back</Link>}
      </header>

      <main className="content narrow">
        <h1>{firstTime ? 'Tell us about yourself' : 'Edit your profile'}</h1>
        <p className="subtitle">
          {firstTime
            ? 'Only your name is required. Everything else is optional, and you can add it later.'
            : 'Keep your details up to date so families can find you.'}
        </p>

        <form onSubmit={submit} className="panel">
          <div className="photo-row">
            {shownPhoto
              ? <img className="photo-preview" src={shownPhoto} alt="Profile preview" />
              : <span className="photo-preview placeholder">{initials}</span>}
            <div className="photo-actions">
              <strong>Profile photo <span className="optional">(optional)</span></strong>
              <span className="hint">JPG, PNG or WebP, up to 2 MB.</span>
              <div className="photo-buttons">
                <button type="button" className="btn-ghost" onClick={() => fileRef.current.click()}>
                  {shownPhoto ? 'Change photo' : 'Upload photo'}
                </button>
                {shownPhoto && (
                  <button type="button" className="btn-ghost" onClick={() => { setNewPhoto(null); setRemovePhoto(true) }}>Remove</button>
                )}
              </div>
              <input ref={fileRef} type="file" accept={TYPES.join(',')} hidden onChange={pickPhoto} />
            </div>
          </div>

          <Field label="Full name *" required maxLength={150} autoComplete="name"
                 value={form.fullName} onChange={set('fullName')} />
          <div className="row">
            <Field label="Age (optional)" type="number" min={18} max={100} inputMode="numeric"
                   value={form.age} onChange={set('age')} />
            <Field label="Experience in years (optional)" type="number" min={0} max={60} inputMode="numeric"
                   value={form.experienceYears} onChange={set('experienceYears')} />
          </div>
          <Field label="Qualification (optional)" maxLength={255} placeholder="e.g. Diploma in Nursing, First Aid certified"
                 value={form.qualification} onChange={set('qualification')} />

          {error && <div className="alert" role="alert"><Alert />{error}</div>}
          <button className="btn-primary" disabled={busy || !form.fullName.trim()}>
            {busy && <span className="spinner" />}{busy ? 'Saving…' : firstTime ? 'Save and continue' : 'Save changes'}
          </button>
        </form>
      </main>
    </div>
  )
}
