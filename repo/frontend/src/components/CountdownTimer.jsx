import { useCountdown } from "../hooks/useCountdown";

export function CountdownTimer({ targetDate, label = "Time left" }) {
  const { expired, ratio, label: remaining } = useCountdown(targetDate);

  let tone = "timer-green";
  if (ratio < 0.4) tone = "timer-yellow";
  if (ratio < 0.15 || expired) tone = "timer-red";

  return (
    <div className={`countdown ${tone}`}>
      <span className="countdown-label">{label}</span>
      <strong className="countdown-value">
        {expired ? "Expired" : remaining}
      </strong>
    </div>
  );
}
