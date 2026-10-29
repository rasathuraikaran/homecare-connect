// Keep in sync with the backend BookingStatus and NotificationStatus enums
export const STATUS_LABEL = {
  REQUESTED: 'Waiting for our team',
  CONFIRMED: 'Confirmed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
}

export const STATUS_TONE = { REQUESTED: 'wait', CONFIRMED: 'ok', COMPLETED: 'done', CANCELLED: 'off' }

// Keep in sync with the backend AlertChannel enum
const CHANNEL_NAME = { EMAIL: 'Email', WHATSAPP: 'WhatsApp' }
const ALERT_STATE = {
  PENDING: 'is being sent',
  SENT: 'sent',
  DRY_RUN: 'not sent (test mode)',
  FAILED: 'failed',
}

/** "Email alert sent", "WhatsApp alert failed", ... for the alert object the admin API returns */
export const alertLabel = (alert) => `${CHANNEL_NAME[alert.channel] ?? 'Admin'} alert ${ALERT_STATE[alert.status]}`

export const ALERT_TONE = { PENDING: 'wait', SENT: 'ok', DRY_RUN: 'off', FAILED: 'bad' }

// Keep in sync with the backend PaymentInfo (state) and PaymentStatus (how the last try ended)
/** Shown to the care seeker under the fee while it is unpaid */
export const PAYMENT_HELP = {
  PENDING: 'If you have just paid, it can take a few minutes to show here.',
  FAILED: 'The last payment did not go through. You can try again.',
  CANCELED: 'The last payment was cancelled. You can pay whenever you are ready.',
  CHARGEDBACK: 'Your bank reversed the last payment. Please pay again, or contact our team.',
}

/** Shown to the admin beside an unpaid fee */
export const ATTEMPT_TEXT = {
  PENDING: 'A payment was started but PayHere has not confirmed it',
  FAILED: 'The last try failed',
  CANCELED: 'The last try was cancelled',
  CHARGEDBACK: 'The last payment was charged back',
}
