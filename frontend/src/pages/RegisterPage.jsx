import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import Field from '../components/Field'
import { Alert } from '../components/Icons'
import GoogleButton from '../components/GoogleButton'
import { RequireRole, RoleBadge, useChosenRole } from '../components/RoleBadge'

export default function RegisterPage() {
  const role = useChosenRole()
  return <RequireRole role={role}><RegisterForm role={role} /></RequireRole>
}

function RegisterForm({ role }) {
  const { register, googleLogin } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await register({ ...form, phone: form.phone || null, role })
      navigate('/')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function google(idToken) {
    setError('')
    try {
      await googleLogin({ idToken, role })
      navigate('/')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle={role === 'CAREGIVER' ? 'Join as a caregiver and get found by families.' : 'Join as a care seeker and find trusted caregivers.'}
      footer={<>Already have an account? <Link to={`/login?role=${role}`}>Sign in</Link></>}
    >
      <RoleBadge role={role} />
      <GoogleButton text="signup_with" onCredential={google} />
      <div className="divider"><span>or sign up with email</span></div>
      <form onSubmit={submit}>
        <Field label="Full name" required autoComplete="name" placeholder="Jane Doe"
               value={form.fullName} onChange={set('fullName')} />
        <div className="row">
          <Field label="Email address" type="email" required autoComplete="email" placeholder="you@example.com"
                 value={form.email} onChange={set('email')} />
          <Field label="Phone" type="tel" autoComplete="tel" placeholder="Optional"
                 value={form.phone} onChange={set('phone')} />
        </div>
        <Field label="Password" type="password" required minLength={8} autoComplete="new-password"
               placeholder="At least 8 characters" value={form.password} onChange={set('password')} />
        {error && <div className="alert" role="alert"><Alert />{error}</div>}
        <button className="btn-primary" disabled={busy}>{busy && <span className="spinner" />}{busy ? 'Creating account…' : 'Create account'}</button>
      </form>
    </AuthLayout>
  )
}
