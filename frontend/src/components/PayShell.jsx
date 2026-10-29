import { Link } from 'react-router-dom'
import { HeartHouse } from './Icons'

/** The frame of the pages a care seeker lands on while paying: a top bar and one centred box. */
export default function PayShell({ children }) {
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand"><span className="brand-mark"><HeartHouse /></span>HomeCare Connect</div>
        <Link className="btn-ghost" to="/">My requests</Link>
      </header>
      <main className="content pay-page">
        <div className="block pay-box">{children}</div>
      </main>
    </div>
  )
}
