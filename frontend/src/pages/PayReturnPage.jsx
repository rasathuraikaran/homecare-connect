import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import PayShell from '../components/PayShell'
import { formatMoney } from '../constants/rates'

const CHECK_EVERY_MS = 3_000
const GIVE_UP_AFTER = 10   // checks, so about 30 seconds

/**
 * Where PayHere sends the family back to. The address says nothing about whether they paid, so this page only asks our own
 * server, which hears from PayHere separately, and waits a little for that message to arrive.
 */
export default function PayReturnPage() {
  const [params] = useSearchParams()
  const id = Number(params.get('booking'))
  const [payment, setPayment] = useState(null)
  const [checks, setChecks] = useState(0)

  const paid = payment?.state === 'PAID'
  const failed = payment?.state === 'DUE' && (payment.lastAttempt === 'FAILED' || payment.lastAttempt === 'CANCELED')

  useEffect(() => {
    if (!id || paid || failed || checks >= GIVE_UP_AFTER) return undefined
    let live = true
    const timer = setTimeout(async () => {
      const mine = await api('/bookings/mine').catch(() => null)
      if (!live) return
      if (mine) setPayment(mine.find((b) => b.id === id)?.payment ?? null)
      setChecks((n) => n + 1)
    }, checks === 0 ? 0 : CHECK_EVERY_MS)
    return () => { live = false; clearTimeout(timer) }
  }, [id, paid, failed, checks])

  return (
    <PayShell>
      {paid && (
        <>
          <h1>Thank you, payment received</h1>
          <p>We received {formatMoney(payment.total)} for request #{id}. A receipt is on its way to your email.</p>
        </>
      )}
      {failed && (
        <>
          <h1>The payment did not go through</h1>
          <p>Nothing was charged for request #{id}. You can try again from Your requests.</p>
        </>
      )}
      {!paid && !failed && checks < GIVE_UP_AFTER && (
        <>
          <h1>Confirming your payment</h1>
          <p role="status">We are waiting for PayHere to confirm request #{id}. This usually takes a few seconds, please keep this page open.</p>
        </>
      )}
      {!paid && !failed && checks >= GIVE_UP_AFTER && (
        <>
          <h1>We have not heard from PayHere yet</h1>
          <p>If you finished the payment, it will show under Your requests within a few minutes and we will email you a receipt. If it does not, please contact our team and mention request #{id}.</p>
        </>
      )}
      <Link className="btn-primary link-btn" to="/">Back to my requests</Link>
    </PayShell>
  )
}
