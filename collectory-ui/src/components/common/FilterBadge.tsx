interface FilterBadgeProps {
  label: string;
  value: string;
  onRemove: (value: string) => void;
}

export default function FilterBadge({ label, value, onRemove }: FilterBadgeProps) {
  return (
    <span className="badge bg-primary me-1 mb-1 d-inline-flex align-items-center">
      {label}
      <button
        type="button"
        className="btn-close btn-close-white ms-1"
        style={{ fontSize: '0.6em' }}
        aria-label={`Remove ${label}`}
        onClick={() => onRemove(value)}
      />
    </span>
  );
}
