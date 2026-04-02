export function Card({ className = "", children }) {
  return (
    <section className={`glass-card ${className}`.trim()}>{children}</section>
  );
}
