import { useId, useState } from 'react'
import { Eye, EyeOff } from './Icons'

export default function Field({ label, hint, type = 'text', ...props }) {
  const id = useId()
  const [shown, setShown] = useState(false)
  const isPassword = type === 'password'

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="input-wrap">
        <input id={id} type={isPassword && shown ? 'text' : type} {...props} />
        {isPassword && (
          <button type="button" className="icon-btn" onClick={() => setShown(!shown)}
                  aria-label={shown ? 'Hide password' : 'Show password'}>
            {shown ? <EyeOff /> : <Eye />}
          </button>
        )}
      </div>
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}
