// Keep in sync with the backend VerificationStatus and DocumentStatus enums.
// What each job must upload comes from the server, so it is not repeated here.
export const OVERALL_LABEL = {
  INCOMPLETE: 'Documents needed',
  IN_REVIEW: 'Waiting for review',
  ACTION_NEEDED: 'Action needed',
  VERIFIED: 'Verified',
}
export const OVERALL_TONE = { INCOMPLETE: 'wait', IN_REVIEW: 'done', ACTION_NEEDED: 'bad', VERIFIED: 'ok' }

export const DOC_LABEL = { PENDING: 'Waiting for review', APPROVED: 'Approved', REJECTED: 'Rejected' }
export const DOC_TONE = { PENDING: 'wait', APPROVED: 'ok', REJECTED: 'bad' }

// What the caregiver is told at the top of the card
export const HEADLINE = {
  INCOMPLETE: ['Add your documents to appear to families', 'Upload the documents marked required. Our team checks them before families can see you.'],
  IN_REVIEW: ['Waiting for review', 'Our team is checking your documents. Families can see you as soon as they are approved.'],
  ACTION_NEEDED: ['A document needs your attention', 'Read the reason under the rejected document, then upload it again.'],
  VERIFIED: ['You are verified', 'Your documents are approved, so families can find you when they search.'],
}

export const MAX_DOCUMENT = 5 * 1024 * 1024
export const DOCUMENT_FILE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
