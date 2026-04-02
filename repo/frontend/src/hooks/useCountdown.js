import { useEffect, useMemo, useState } from "react";

export function useCountdown(targetDate) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, []);

  return useMemo(() => {
    if (!targetDate) {
      return { expired: false, label: "--:--:--", ratio: 0 };
    }

    const end = new Date(targetDate).getTime();
    const diff = Math.max(0, end - now);
    const expired = diff <= 0;
    const hours = Math.floor(diff / 3600000);
    const minutes = Math.floor((diff % 3600000) / 60000);
    const seconds = Math.floor((diff % 60000) / 1000);

    const totalWindow = 24 * 3600000;
    const ratio = Math.max(0, Math.min(1, diff / totalWindow));
    const label = `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(
      seconds,
    ).padStart(2, "0")}`;

    return { expired, label, ratio };
  }, [targetDate, now]);
}
