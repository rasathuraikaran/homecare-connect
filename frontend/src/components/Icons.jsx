const base = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true }

export const HeartHouse = (p) => (
  <svg {...base} {...p}><path d="M3 11.5 12 4l9 7.5" /><path d="M5 10v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-9" /><path d="M12 17c-2.2-1.4-3.2-2.5-3.2-3.8a1.8 1.8 0 0 1 3.2-1.1 1.8 1.8 0 0 1 3.2 1.1c0 1.3-1 2.4-3.2 3.8Z" /></svg>
)
export const Search = (p) => (
  <svg {...base} {...p}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
)
export const Stethoscope = (p) => (
  <svg {...base} {...p}><path d="M6 3v6a4 4 0 0 0 8 0V3" /><path d="M10 13v2a5 5 0 0 0 10 0v-2" /><circle cx="20" cy="11" r="2" /></svg>
)
export const Shield = (p) => (
  <svg {...base} {...p}><path d="M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></svg>
)
export const Eye = (p) => (
  <svg {...base} {...p}><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
)
export const EyeOff = (p) => (
  <svg {...base} {...p}><path d="M3 3l18 18" /><path d="M10.6 5.1A9.7 9.7 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.6 6.6A16.6 16.6 0 0 0 2 12s3.5 7 10 7c1.7 0 3.2-.4 4.4-1" /><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" /></svg>
)
export const Alert = (p) => (
  <svg {...base} {...p}><circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16.5v.01" /></svg>
)
