import { Link, useSearchParams } from 'react-router-dom'
import PayShell from '../components/PayShell'

export default function PayCancelPage() {
  const id = Number(useSearchParams()[0].get('booking'))
  return (
    <PayShell>
      <h1>Payment cancelled</h1>
      <p>You cancelled the payment{id ? ` for request #${id}` : ''}, so nothing was charged. You can pay whenever you are ready from Your requests.</p>
      <Link className="btn-primary link-btn" to="/">Back to my requests</Link>
    </PayShell>
  )
}
