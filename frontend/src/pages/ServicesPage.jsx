import { Link } from 'react-router-dom'
import { HeartHouse } from '../components/Icons'
import { JOB_ICONS } from '../components/jobIcons'
import { JOBS } from '../constants/jobs'
import { GOOD_TO_KNOW, SERVICES, STEPS } from '../constants/services'

/** For care seekers: what each kind of care is, how asking works, and what is good to know. */
export default function ServicesPage() {
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        <Link className="btn-ghost" to="/">Find a caregiver</Link>
      </header>

      <main className="content wide services">
        <div className="page-head">
          <h1>What we provide</h1>
          <p className="subtitle">Three kinds of care, at home. Read what each one covers, then choose the one that fits.</p>
        </div>

        <div className="service-grid">
          {JOBS.map(({ value }) => {
            const s = SERVICES[value]
            const Icon = JOB_ICONS[value]
            return (
              <article key={value} className="block service-card">
                <span className="care-icon"><Icon /></span>
                <h2>{s.title}</h2>
                <p className="service-headline">{s.headline}</p>
                <p>{s.summary}</p>
                <h3>What it can include</h3>
                <ul>{s.includes.map((t) => <li key={t}>{t}</li>)}</ul>
                <h3>A good choice for</h3>
                <p>{s.goodFor}</p>
                <Link className="btn-primary link-btn" to={`/?job=${value}`}>Find a {s.title.toLowerCase()}</Link>
              </article>
            )
          })}
        </div>

        <section className="block how" aria-labelledby="how-title">
          <h2 id="how-title">How it works</h2>
          <ol className="steps">
            {STEPS.map((s, i) => (
              <li key={s.title}>
                <span className="step-no">{i + 1}</span>
                <div><strong>{s.title}</strong><p>{s.text}</p></div>
              </li>
            ))}
          </ol>
        </section>

        <section className="block" aria-labelledby="know-title">
          <h2 id="know-title">Good to know</h2>
          <ul className="know">{GOOD_TO_KNOW.map((t) => <li key={t}>{t}</li>)}</ul>
        </section>
      </main>
    </div>
  )
}
