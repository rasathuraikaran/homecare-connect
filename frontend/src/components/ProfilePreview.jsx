import { jobLabel } from '../constants/jobs'
import { formatMoney, ratesList } from '../constants/rates'
import { Stethoscope } from './Icons'

/** Live card showing roughly how a caregiver will appear to families. */
export default function ProfilePreview({ name, age, years, qualification, photo, jobType, rates }) {
  const initials = (name || '?').split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || '?'
  const tags = qualification.split(',').map((t) => t.trim()).filter(Boolean)
  const facts = [age && `${age} yrs old`, years !== '' && `${years} ${Number(years) === 1 ? 'year' : 'years'} experience`].filter(Boolean)

  const priced = ratesList(Object.fromEntries(Object.entries(rates ?? {}).filter(([, v]) => Number(v) > 0).map(([k, v]) => [k, Number(v)])))

  return (
    <aside className="preview" aria-label="Profile preview">
      <span className="preview-label">How families will see you</span>
      <div className="preview-card">
        {photo ? <img className="preview-avatar" src={photo} alt="" /> : <span className="preview-avatar initials">{initials}</span>}
        <h3 className={name ? '' : 'ghost'}>{name || 'Your name'}</h3>
        <span className="pt-chip"><Stethoscope />{jobLabel(jobType) ?? 'Caregiver'}</span>
        <p className={facts.length ? 'facts' : 'facts ghost'}>{facts.length ? facts.join(' · ') : 'Age and experience appear here'}</p>
        {priced.length > 0 && (
          <ul className="price-list">
            {priced.map((r) => <li key={r.value}><span>{r.label}</span><strong>{formatMoney(r.amount)}</strong></li>)}
          </ul>
        )}
        {tags.length > 0
          ? <div className="tags">{tags.slice(0, 6).map((t) => <span key={t} className="tag">{t}</span>)}</div>
          : <p className="facts ghost">Qualifications appear here</p>}
      </div>
    </aside>
  )
}
