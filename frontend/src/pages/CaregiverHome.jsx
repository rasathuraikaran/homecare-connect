import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { Alert, Arrow, Bulb, Check } from '../components/Icons'
import ProfilePreview from '../components/ProfilePreview'
import ReviewList, { RatingSummary } from '../components/ReviewList'
import { COMPLETENESS_ITEMS, GENERIC_TIP, TIPS, completeness } from '../constants/completeness'

export default function CaregiverHome({ firstName }) {
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    setError('')
    api('/caregivers/me/profile').then(setProfile).catch((e) => setError(e.message))
  }, [attempt])

  return (
    <section className="content wide">
      <div className="page-head">
        <h1>Welcome, {firstName} 👋</h1>
        <p className="subtitle">Here is how your profile looks to families, and what to add next.</p>
      </div>

      {error && (
        <div className="alert" role="alert"><Alert />{error}
          <button type="button" className="link" onClick={() => setAttempt(attempt + 1)}>Try again</button>
        </div>
      )}

      {!profile && !error && (
        <div className="cg-home">
          <div className="block skeleton" style={{ minHeight: 320 }} />
          <div className="block skeleton" style={{ minHeight: 320 }} />
        </div>
      )}

      {profile && <Loaded profile={profile} />}
    </section>
  )
}

function Loaded({ profile }) {
  const [available, setAvailable] = useState(profile.available)
  const [toggleError, setToggleError] = useState('')
  const [toggling, setToggling] = useState(false)
  const [reviews, setReviews] = useState(null)

  useEffect(() => {
    api('/caregivers/me/reviews').then(setReviews).catch(() => setReviews({ average: null, count: 0, reviews: [], failed: true }))
  }, [])

  async function toggle() {
    const next = !available
    setAvailable(next)            // optimistic: flip now, undo if the server says no
    setToggleError('')
    setToggling(true)
    try {
      await api('/caregivers/me/availability', { method: 'PUT', body: { available: next } })
    } catch (e) {
      setAvailable(!next)
      setToggleError(e.message)
    } finally {
      setToggling(false)
    }
  }

  const { items, percent, missing } = completeness(profile)
  const complete = missing.length === 0
  const tips = [...new Set(missing.map((m) => TIPS[m.key]).filter(Boolean))].slice(0, 3)

  return (
    <div className="cg-home">
      <div className="cg-home-main">
        <section className={available ? 'block availability on' : 'block availability'}>
          <div>
            <h2>{available ? 'You are available for work' : 'You are not available right now'}</h2>
            <p className="muted small">
              {available ? 'Families can find you when they search.' : 'You are hidden from search until you switch this back on.'}
            </p>
            {toggleError && <p className="error-text small" role="alert">{toggleError}</p>}
          </div>
          <button type="button" role="switch" aria-checked={available} aria-label="Available for work"
                  className="switch" disabled={toggling} onClick={toggle}><span /></button>
        </section>

        <section className="block">
          <div className="block-head">
            <h2>{complete ? 'Your profile is complete 🎉' : `Your profile is ${percent}% complete`}</h2>
            {!complete && <span className="muted">{items.length - missing.length} of {items.length} done</span>}
          </div>
          <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-label="Profile completeness">
            <span style={{ width: `${percent}%` }} />
          </div>
          <p className="muted small">
            {complete ? 'Families can see everything about you.' : 'A complete profile makes it easier for families to choose you.'}
          </p>

          <ul className="todo">
            {COMPLETENESS_ITEMS.map((def) => {
              const item = items.find((i) => i.key === def.key)
              return item.isDone ? (
                <li key={item.key} className="done"><span className="tick"><Check /></span>{item.label}</li>
              ) : (
                <li key={item.key}>
                  <span className="tick open" />
                  <Link to={`/profile?focus=${item.focus}`}>{item.action}<Arrow /></Link>
                </li>
              )
            })}
          </ul>
        </section>

        <section className="block">
          <div className="block-head">
            <h2>Reviews</h2>
            {reviews && <RatingSummary average={reviews.average} count={reviews.count} />}
          </div>
          {!reviews && <p className="muted">Loading…</p>}
          {reviews?.failed && <p className="muted">Could not load your reviews right now.</p>}
          {reviews && !reviews.failed && (
            <ReviewList reviews={reviews.reviews.slice(0, 5)} emptyText="No reviews yet. They appear here when families rate you." />
          )}
        </section>

        <section className="block tips">
          <div className="block-head"><h2><Bulb /> Tips</h2></div>
          <ul>
            {(tips.length ? tips : [GENERIC_TIP]).map((t) => <li key={t}>{t}</li>)}
          </ul>
        </section>
      </div>

      <div className="side">
        <ProfilePreview name={profile.fullName} age={profile.age ?? ''} years={profile.experienceYears ?? ''}
                        qualification={profile.qualification ?? ''} photo={profile.photoUrl} jobType={profile.jobType}
                        rates={profile.rates} />
        <Link to="/profile" className="btn-primary link-btn">Edit profile</Link>
      </div>
    </div>
  )
}
