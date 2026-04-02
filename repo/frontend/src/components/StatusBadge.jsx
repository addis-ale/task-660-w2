const statusClassMap = {
  RESERVED: "badge-yellow",
  CONFIRMED: "badge-blue",
  FULFILLED: "badge-green",
  CANCELLED: "badge-red",
  OPEN: "badge-amber",
  ACKNOWLEDGED: "badge-blue",
  IN_PROGRESS: "badge-teal",
  ESCALATED: "badge-red",
  RESOLVED: "badge-green",
  CLOSED: "badge-slate",
  APPROVED: "badge-green",
  DENIED: "badge-red",
  SUBMITTED: "badge-amber",
  UNDER_REVIEW: "badge-blue",
  ESCALATED_TO_ADMIN: "badge-red",
};

export function StatusBadge({ value }) {
  if (!value) {
    return <span className="status-badge badge-slate">UNKNOWN</span>;
  }

  const cssClass = statusClassMap[value] || "badge-slate";
  return (
    <span className={`status-badge ${cssClass}`}>
      {value.replaceAll("_", " ")}
    </span>
  );
}
