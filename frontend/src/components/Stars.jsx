import { Star } from './Icons'

/** Read-only star row. `value` may be fractional; it is rounded to the nearest whole star. */
export default function Stars({ value, size = 16 }) {
  const filled = Math.round(value ?? 0)
  return (
    <span className="stars" role="img" aria-label={`${value ?? 0} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((i) => <Star key={i} filled={i <= filled} width={size} height={size} />)}
    </span>
  )
}

/** Clickable 1 to 5 star picker. */
export function StarInput({ value, onChange }) {
  return (
    <div className="star-input" role="radiogroup" aria-label="Your rating">
      {[1, 2, 3, 4, 5].map((i) => (
        <button key={i} type="button" role="radio" aria-checked={value === i} aria-label={`${i} star${i > 1 ? 's' : ''}`}
                className={i <= value ? 'on' : ''} onClick={() => onChange(i)}>
          <Star filled={i <= value} width={28} height={28} />
        </button>
      ))}
    </div>
  )
}
