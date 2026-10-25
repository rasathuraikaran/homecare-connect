import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { jobLabel } from '../constants/jobs'
import { Alert } from './Icons'
import ReviewList, { RatingSummary } from './ReviewList'
import { StarInput } from './Stars'

/** Modal showing a caregiver's reviews, where a seeker can also write or edit their own. */
export default function ReviewsDialog({ caregiver, onClose, onChanged }) {
  const ref = useRef(null)
  const [data, setData] = useState(null)
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [issues, setIssues] = useState([])
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog.open) dialog.showModal()
    api(`/caregivers/${caregiver.id}/reviews`)
      .then((d) => {
        setData(d)
        setRating(d.myReview?.rating ?? 0)
        setComment(d.myReview?.comment ?? '')
      })
      .catch((e) => setIssues([e.message]))
  }, [caregiver.id])

  async function submit(e) {
    e.preventDefault()
    setSaved(false)
    if (!rating) return setIssues(['Choose a rating from 1 to 5 stars'])
    setIssues([])
    setBusy(true)
    try {
      const d = await api(`/caregivers/${caregiver.id}/reviews`, { method: 'PUT', body: { rating, comment } })
      setData(d)
      setSaved(true)
      onChanged()
    } catch (err) {
      setIssues(err.errors?.length ? err.errors : [err.message])
    } finally {
      setBusy(false)
    }
  }

  return (
    <dialog ref={ref} className="dialog" onClose={onClose}
            onClick={(e) => { if (e.target === ref.current) ref.current.close() }}>
      <div className="dialog-body">
        <header className="dialog-head">
          <div>
            <h2>{caregiver.fullName}</h2>
            <span className="muted">{jobLabel(caregiver.jobType)}</span>
          </div>
          <button type="button" className="btn-ghost" onClick={() => ref.current.close()}>Close</button>
        </header>

        {data && <RatingSummary average={data.average} count={data.count} />}

        <form onSubmit={submit} className="review-form">
          <h3>{data?.myReview ? 'Your review' : 'Write a review'}</h3>
          <StarInput value={rating} onChange={(v) => { setRating(v); setSaved(false) }} />
          <textarea rows={3} maxLength={1000} placeholder="Share your experience (optional)"
                    value={comment} onChange={(e) => { setComment(e.target.value); setSaved(false) }} />
          <div className="review-form-foot">
            <span className="hint">{comment.length}/1000</span>
            <button className="btn-primary" disabled={busy || !data}>
              {busy && <span className="spinner" />}{busy ? 'Saving…' : data?.myReview ? 'Update review' : 'Post review'}
            </button>
          </div>
          {issues.length > 0 && <div className="alert" role="alert"><Alert />{issues.join('. ')}</div>}
          {saved && <p className="saved" role="status">Thank you! Your review is saved.</p>}
        </form>

        <h3>Recent reviews</h3>
        {data ? <ReviewList reviews={data.reviews} emptyText="Be the first to review this caregiver." /> : <p className="muted">Loading…</p>}
      </div>
    </dialog>
  )
}
