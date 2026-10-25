import Stars from './Stars'

const fmt = (iso) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

export function RatingSummary({ average, count }) {
  if (!count) return <span className="muted">No reviews yet</span>
  return (
    <span className="rating-summary">
      <strong>{average.toFixed(1)}</strong><Stars value={average} />
      <span className="muted">{count} {count === 1 ? 'review' : 'reviews'}</span>
    </span>
  )
}

export default function ReviewList({ reviews, emptyText = 'No reviews yet.' }) {
  if (!reviews.length) return <p className="muted">{emptyText}</p>
  return (
    <ul className="reviews">
      {reviews.map((r) => (
        <li key={r.id}>
          <div className="review-head">
            <strong>{r.reviewerName}</strong>
            <Stars value={r.rating} size={14} />
            <span className="muted small">{fmt(r.createdAt)}</span>
          </div>
          {r.comment && <p>{r.comment}</p>}
        </li>
      ))}
    </ul>
  )
}
