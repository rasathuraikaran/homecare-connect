import { jobLabel } from '../constants/jobs'
import { formatMoney, ratesList } from '../constants/rates'
import { Check, Stethoscope } from './Icons'
import { RatingSummary } from './ReviewList'

export default function CaregiverCard({ caregiver: c, onOpenReviews, onRequest }) {
  const initials = c.fullName.split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
  const tags = (c.qualification ?? '').split(',').map((t) => t.trim()).filter(Boolean).slice(0, 4)
  const facts = [
    c.experienceYears != null && `${c.experienceYears} ${c.experienceYears === 1 ? 'year' : 'years'} experience`,
    c.age != null && `${c.age} yrs old`,
  ].filter(Boolean)

  const priced = ratesList(c.rates)

  return (
    <article className="cg-card">
      {c.photoUrl ? <img className="cg-avatar" src={c.photoUrl} alt="" /> : <span className="cg-avatar initials">{initials}</span>}
      <h3>{c.fullName}</h3>
      <button type="button" className="rating-btn" onClick={() => onOpenReviews(c)} aria-label={`See reviews for ${c.fullName}`}>
        <RatingSummary average={c.rating} count={c.reviewCount} />
      </button>
      <span className="pt-chip"><Stethoscope />{jobLabel(c.jobType)}</span>
      <span className="verified-chip"><Check />Documents verified</span>
      <p className={facts.length ? 'facts' : 'facts ghost'}>{facts.length ? facts.join(' · ') : 'Experience not shared yet'}</p>
      {tags.length > 0 && <div className="tags">{tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>}
      {priced.length > 0 ? (
        <ul className="price-list">
          {priced.map((r) => <li key={r.value}><span>{r.label}</span><strong>{formatMoney(r.amount)}</strong></li>)}
        </ul>
      ) : (
        <p className="facts ghost">Charges not listed</p>
      )}
      <button type="button" className="btn-primary card-cta" onClick={() => onRequest(c)}>Request {c.fullName.split(' ')[0]}</button>
    </article>
  )
}
