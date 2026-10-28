export default function StatusChip({ label, tone }) {
  return <span className={`status-chip ${tone}`}>{label}</span>
}
