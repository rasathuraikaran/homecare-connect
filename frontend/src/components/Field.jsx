import { useId, useState } from 'react'
import { Eye, EyeOff } from './Icons'

export default function Field({ label, hint, error, suffix, type = 'text', ...props }) {
  const id = useId()
  const [shown, setShown] = useState(false)
  const isPassword = type === 'password'
  const describedBy = error ? `${id}-err` : hint ? `${id}-hint` : undefined

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className={`input-wrap${error ? ' invalid' : ''}${suffix ? ' has-suffix' : ''}`}>
        <input id={id} type={isPassword && shown ? 'text' : type} aria-invalid={!!error} aria-describedby={describedBy} {...props} />
        {suffix && <span className="suffix">{suffix}</span>}
        {isPassword && (
          <button type="button" className="icon-btn" onClick={() => setShown(!shown)}
                  aria-label={shown ? 'Hide password' : 'Show password'}>
            {shown ? <EyeOff /> : <Eye />}
          </button>
        )}
      </div>
      {error
        ? <span id={`${id}-err`} className="hint error-text">{error}</span>
        : hint && <span id={`${id}-hint`} className="hint">{hint}</span>}
    </div>
  )
}
