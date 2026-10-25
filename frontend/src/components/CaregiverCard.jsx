import { jobLabel } from '../constants/jobs'
import { Stethoscope } from './Icons'

export default function CaregiverCard({ caregiver: c }) {
  const initials = c.fullName.split(' ').filter(Boolean).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
  const tags = (c.qualification ?? '').split(',').map((t) => t.trim()).filter(Boolean).slice(0, 4)
  const facts = [
    c.experienceYears != null && `${c.experienceYears} ${c.experienceYears === 1 ? 'year' : 'years'} experience`,
    c.age != null && `${c.age} yrs old`,
  ].filter(Boolean)

  return (
    <article className="cg-card">
      {c.photoUrl ? <img className="cg-avatar" src={c.photoUrl} alt="" /> : <span className="cg-avatar initials">{initials}</span>}
      <h3>{c.fullName}</h3>
      <span className="pt-chip"><Stethoscope />{jobLabel(c.jobType)}</span>
      <p className={facts.length ? 'facts' : 'facts ghost'}>{facts.length ? facts.join(' · ') : 'Experience not shared yet'}</p>
      {tags.length > 0 && <div className="tags">{tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>}
    </article>
  )
}
