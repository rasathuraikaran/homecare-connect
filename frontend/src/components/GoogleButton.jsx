import { useEffect, useRef, useState } from 'react'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID
const SRC = 'https://accounts.google.com/gsi/client'

let scriptPromise
function loadGsi() {
  scriptPromise ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SRC
    s.async = true
    s.onload = resolve
    s.onerror = () => { scriptPromise = null; reject(new Error('Could not load Google sign-in')) }
    document.head.appendChild(s)
  })
  return scriptPromise
}

/** Renders Google's official button; calls onCredential(idToken) after the user picks an account. */
export default function GoogleButton({ onCredential, text = 'continue_with' }) {
  const ref = useRef(null)
  const cb = useRef(onCredential)
  cb.current = onCredential
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    if (!CLIENT_ID) return
    let cancelled = false
    loadGsi().then(() => {
      if (cancelled || !ref.current) return
      window.google.accounts.id.initialize({
        client_id: CLIENT_ID,
        callback: (res) => cb.current(res.credential),
      })
      window.google.accounts.id.renderButton(ref.current, {
        theme: 'outline', size: 'large', text, shape: 'rectangular', width: ref.current.offsetWidth,
      })
    }).catch(() => setFailed(true))
    return () => { cancelled = true }
  }, [text])

  if (!CLIENT_ID) {
    return (
      <button type="button" className="btn-google" disabled title="Set VITE_GOOGLE_CLIENT_ID to enable">
        Continue with Google (not configured)
      </button>
    )
  }
  if (failed) return <p className="hint">Google sign-in is unavailable right now.</p>
  return <div ref={ref} className="google-slot" />
}
