// Keep in sync with the backend RateType enum
export const CURRENCY = 'LKR'

export const RATE_TYPES = [
  { value: 'HOURLY', label: 'Per hour', unit: '/hour', desc: 'Charged for each hour of care' },
  { value: 'SESSION', label: 'Per session', unit: '/session', desc: 'One visit or appointment' },
  { value: 'DAILY', label: 'Per day', unit: '/day', desc: 'Daytime care' },
  { value: 'FULL_DAY_NIGHT', label: 'Full day & night', unit: '/24 hours', desc: 'Day and night, around the clock' },
]

const nf = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })
export const formatMoney = (n) => `${CURRENCY} ${nf.format(n)}`

/** [{ ...rateType, amount }] for the rates present, in display order. */
export const ratesList = (rates = {}) =>
  RATE_TYPES.filter((r) => rates[r.value] != null).map((r) => ({ ...r, amount: rates[r.value] }))
