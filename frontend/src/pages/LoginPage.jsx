import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import AuthLayout from '../components/AuthLayout'
import Field from '../components/Field'
import { Alert } from '../components/Icons'
import GoogleButton from '../components/GoogleButton'
import { RequireRole, RoleBadge, useChosenRole } from '../components/RoleBadge'

export default function LoginPage() {
  const role = useChosenRole()
  return <RequireRole role={role}><LoginForm role={role} /></RequireRole>
}

function LoginForm({ role }) {
  const { login, googleLogin } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login({ ...form, role })
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
      await googleLogin({ idToken, role, expectedRole: role })
      navigate('/')
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={role === 'CAREGIVER' ? 'Sign in to manage your caregiver profile.' : 'Sign in to find the right caregiver.'}
      footer={<>New to HomeCare Connect? <Link to={`/register?role=${role}`}>Create an account</Link></>}
    >
      <RoleBadge role={role} />
      <form onSubmit={submit} noValidate={false}>
        <Field label="Email address" type="email" required autoComplete="email" placeholder="you@example.com"
               value={form.email} onChange={set('email')} />
        <Field label="Password" type="password" required autoComplete="current-password" placeholder="Enter your password"
               value={form.password} onChange={set('password')} />
        {error && <div className="alert" role="alert"><Alert />{error}</div>}
        <button className="btn-primary" disabled={busy}>{busy && <span className="spinner" />}{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <div className="divider"><span>or</span></div>
      <GoogleButton text="signin_with" onCredential={google} />
    </AuthLayout>
  )
}
