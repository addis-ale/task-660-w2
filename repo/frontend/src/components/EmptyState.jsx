export function EmptyState({ title, message }) {
  return (
    <div className="empty-state glass-card">
      <h3>{title}</h3>
      <p>{message}</p>
    </div>
  );
}
