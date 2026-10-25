import { HeartHouse, Search, Shield, Stethoscope } from './Icons'

const POINTS = [
  { icon: Search, text: 'Find caregivers by skills, location and availability' },
  { icon: Stethoscope, text: 'Caregivers showcase their experience and set their own rates' },
  { icon: Shield, text: 'Secure accounts, so families and caregivers connect with confidence' },
]

export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <div className="auth">
      <aside className="auth-hero">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        <div>
          <h2>Care you can trust,<br />right at home.</h2>
          <ul>
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text}><span className="pt-icon"><Icon /></span>{text}</li>
            ))}
          </ul>
        </div>
        <small>© {new Date().getFullYear()} HomeCare Connect</small>
      </aside>

      <main className="auth-panel">
        <div className="auth-box">
          <div className="brand brand-mobile"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
          <h1>{title}</h1>
          <p className="subtitle">{subtitle}</p>
          {children}
          <p className="auth-footer">{footer}</p>
        </div>
      </main>
    </div>
  )
}
