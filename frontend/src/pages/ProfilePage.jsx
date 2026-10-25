import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Field from '../components/Field'
import { Activity, Alert, Heart, HeartHouse, Stethoscope } from '../components/Icons'
import { JOBS } from '../constants/jobs'
import { CURRENCY, RATE_TYPES } from '../constants/rates'
import ProfilePreview from '../components/ProfilePreview'

const MAX_PHOTO = 2 * 1024 * 1024
const TYPES = ['image/jpeg', 'image/png', 'image/webp']
const SUGGESTIONS = ['Nursing diploma', 'First aid certified', 'Elderly care certificate', 'Dementia care', 'Physiotherapy assistant']

const JOB_ICONS = { CARETAKER: Heart, NURSING: Stethoscope, PHYSIOTHERAPIST: Activity }

const digits = (v, max) => v.replace(/\D/g, '').slice(0, max)

export default function ProfilePage() {
  const { user, refresh, logout } = useAuth()
  const navigate = useNavigate()
  const fileRef = useRef(null)
  const firstTime = !user.profileCompleted

  const [form, setForm] = useState({ fullName: user.fullName, age: '', experienceYears: '', qualification: '', jobType: '', rates: {} })
  const [photoUrl, setPhotoUrl] = useState(null)   // saved photo
  const [newPhoto, setNewPhoto] = useState(null)   // { file, preview }
  const [removePhoto, setRemovePhoto] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [loaded, setLoaded] = useState(firstTime)

  useEffect(() => {
    if (firstTime) return
    api('/caregivers/me/profile').then((p) => {
      setForm({ fullName: p.fullName, age: p.age ?? '', experienceYears: p.experienceYears ?? '', qualification: p.qualification ?? '', jobType: p.jobType ?? '',
        rates: Object.fromEntries(Object.entries(p.rates ?? {}).map(([k, v]) => [k, String(v)])) })
      setPhotoUrl(p.photoUrl)
    }).catch((e) => setError(e.message)).finally(() => setLoaded(true))
  }, [firstTime])

  useEffect(() => () => newPhoto && URL.revokeObjectURL(newPhoto.preview), [newPhoto])

  const set = (k, clean = (v) => v) => (e) => setForm({ ...form, [k]: clean(e.target.value) })
  const num = (v) => (v === '' ? null : Number(v))

  const ageError = form.age !== '' && (Number(form.age) < 18 || Number(form.age) > 100) ? 'Age must be between 18 and 100' : ''
  const expError = form.experienceYears !== '' && Number(form.experienceYears) > 60 ? 'Enter 60 or fewer years' : ''
  const rateErrors = Object.fromEntries(
    Object.entries(form.rates).map(([k, v]) => [k, v === '' || Number(v) <= 0 ? 'Enter an amount' : '']))
  const ratesInvalid = Object.values(rateErrors).some(Boolean)
  const invalid = !form.fullName.trim() || !!ageError || !!expError || ratesInvalid

  function toggleRate(type) {
    const rates = { ...form.rates }
    if (type in rates) delete rates[type]
    else rates[type] = ''
    setForm({ ...form, rates })
  }
  const setRate = (type) => (e) => setForm({ ...form, rates: { ...form.rates, [type]: digits(e.target.value, 7) } })

  function acceptPhoto(file) {
    if (!file) return
    if (!TYPES.includes(file.type)) return setError('Photo must be a JPG, PNG or WebP image')
    if (file.size > MAX_PHOTO) return setError('Photo must be 2 MB or smaller')
    setError('')
    setRemovePhoto(false)
    setNewPhoto({ file, preview: URL.createObjectURL(file) })
  }

  function addSuggestion(text) {
    const items = form.qualification.split(',').map((t) => t.trim()).filter(Boolean)
    if (items.some((t) => t.toLowerCase() === text.toLowerCase())) return
    setForm({ ...form, qualification: [...items, text].join(', ').slice(0, 255) })
  }

  async function submit(e) {
    e.preventDefault()
    if (invalid) return
    setError('')
    setBusy(true)
    try {
      await api('/caregivers/me/profile', {
        method: 'PUT',
        body: { fullName: form.fullName, age: num(form.age), experienceYears: num(form.experienceYears), qualification: form.qualification, jobType: form.jobType || null,
          rates: Object.fromEntries(Object.entries(form.rates).map(([k, v]) => [k, Number(v)])) },
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
  const initials = (form.fullName || '?').split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase()

  if (!loaded) return <p className="center">Loading…</p>

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        {firstTime
          ? <button className="btn-ghost" onClick={logout}>Sign out</button>
          : <Link className="btn-ghost" to="/">Back to dashboard</Link>}
      </header>

      <main className="content wide">
        <div className="page-head">
          <h1>{firstTime ? 'Set up your caregiver profile' : 'Edit your profile'}</h1>
          <p className="subtitle">
            {firstTime ? 'Only your name is required. Add the rest now or come back to it later.' : 'Keep your details up to date so families can find you.'}
          </p>
        </div>

        <div className="profile-grid">
          <form onSubmit={submit} className="panel" noValidate>
            <section className="section span-all">
              <h2>What do you do? <span className="optional">Optional</span></h2>
              <div className="jobs" role="radiogroup" aria-label="Job">
                {JOBS.map(({ value, label, desc }) => {
                  const Icon = JOB_ICONS[value]
                  const active = form.jobType === value
                  return (
                    <button type="button" key={value} role="radio" aria-checked={active}
                            className={active ? 'job active' : 'job'}
                            onClick={() => setForm({ ...form, jobType: active ? '' : value })}>
                      <span className="job-icon"><Icon /></span>
                      <span className="job-text"><strong>{label}</strong><em>{desc}</em></span>
                    </button>
                  )
                })}
              </div>
            </section>

            <section className="section">
              <h2>About you</h2>
              <div className="identity">
                <div
                  className={`photo-block${dragging ? ' dragging' : ''}`}
                  onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
                  onDragLeave={() => setDragging(false)}
                  onDrop={(e) => { e.preventDefault(); setDragging(false); acceptPhoto(e.dataTransfer.files[0]) }}
                >
                  <button type="button" className="photo-btn" onClick={() => fileRef.current.click()} aria-label="Choose a profile photo">
                    {shownPhoto ? <img src={shownPhoto} alt="" /> : <span className="initials">{initials || '?'}</span>}
                    <span className="camera">+</span>
                  </button>
                  {shownPhoto
                    ? <button type="button" className="link small" onClick={() => { setNewPhoto(null); setRemovePhoto(true) }}>Remove</button>
                    : <span className="photo-caption">Photo <span className="muted-inline">(optional)</span></span>}
                  <input ref={fileRef} type="file" accept={TYPES.join(',')} hidden onChange={(e) => { acceptPhoto(e.target.files[0]); e.target.value = '' }} />
                </div>

                <div className="identity-fields">
                  <Field label="Full name *" required maxLength={150} autoComplete="name"
                         value={form.fullName} onChange={set('fullName')} />
                  <div className="row">
                    <Field label="Age" inputMode="numeric" placeholder="e.g. 34" error={ageError}
                           value={form.age} onChange={set('age', (v) => digits(v, 3))} />
                    <Field label="Experience" inputMode="numeric" placeholder="e.g. 5" suffix="years" error={expError}
                           value={form.experienceYears} onChange={set('experienceYears', (v) => digits(v, 2))} />
                  </div>
                </div>
              </div>
              <span className="hint">Photo: drag onto the circle or click it. JPG, PNG or WebP, up to 2 MB.</span>
            </section>

            <section className="section">
              <h2>Qualifications <span className="optional">Optional</span></h2>
              <Field label="Qualifications and certificates" maxLength={255} placeholder="Separate with commas"
                     value={form.qualification} onChange={set('qualification')} />
              <div className="chips" aria-label="Suggestions">
                {SUGGESTIONS.map((s) => (
                  <button type="button" key={s} className="chip" onClick={() => addSuggestion(s)}>+ {s}</button>
                ))}
              </div>
            </section>

            <section className="section span-wide">
              <h2>Your charges <span className="optional">Optional</span></h2>
              <p className="hint">Tick the ways you charge and enter an amount ({CURRENCY}). You can offer more than one.</p>
              <div className="rates">
                {RATE_TYPES.map(({ value, label, unit, desc }) => {
                  const on = value in form.rates
                  return (
                    <div key={value} className={on ? 'rate on' : 'rate'}>
                      <label className="rate-head">
                        <input type="checkbox" checked={on} onChange={() => toggleRate(value)} />
                        <span><strong>{label}</strong><em>{desc}</em></span>
                      </label>
                      {on && (
                        <div className="rate-amount">
                          <div className={`input-wrap has-prefix has-suffix${form.rates[value] === '' ? ' invalid' : ''}`}>
                            <span className="prefix">{CURRENCY}</span>
                            <input inputMode="numeric" placeholder="0" aria-label={`${label} amount`}
                                   value={form.rates[value]} onChange={setRate(value)} autoFocus={form.rates[value] === ''} />
                            <span className="suffix">{unit}</span>
                          </div>
                          {form.rates[value] === '' && <span className="hint error-text">{rateErrors[value]}</span>}
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            </section>

            {error && <div className="alert" role="alert"><Alert />{error}</div>}
            <div className="actions">
              {!firstTime && <Link className="btn-ghost" to="/">Cancel</Link>}
              <button className="btn-primary" disabled={busy || invalid}>
                {busy && <span className="spinner" />}{busy ? 'Saving…' : firstTime ? 'Save and continue' : 'Save changes'}
              </button>
            </div>
          </form>

          <ProfilePreview name={form.fullName.trim()} age={form.age} years={form.experienceYears}
                          qualification={form.qualification} photo={shownPhoto} jobType={form.jobType} rates={form.rates} />
        </div>
      </main>
    </div>
  )
}
