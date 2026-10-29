const PAYHERE_HOSTS = ['www.payhere.lk', 'sandbox.payhere.lk']

/** PayHere takes the customer by an ordinary form post, so build that form and send it. This leaves our page for PayHere's. */
export function postToPayHere(actionUrl, fields) {
  // the address comes from our own server, but never post a card form anywhere but PayHere
  if (!PAYHERE_HOSTS.includes(new URL(actionUrl).hostname)) throw new Error('The payment address was not recognised. Please contact our team.')

  const form = document.createElement('form')
  form.method = 'POST'
  form.action = actionUrl
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    form.appendChild(input)
  }
  document.body.appendChild(form)
  form.submit()
}
