import { useRef } from "react";

export function Button({
  children,
  className = "",
  onClick,
  type = "button",
  variant = "primary",
  ...props
}) {
  const ref = useRef(null);

  const handleClick = (event) => {
    const button = ref.current;
    if (button) {
      const ripple = document.createElement("span");
      ripple.className = "ripple";
      const rect = button.getBoundingClientRect();
      ripple.style.left = `${event.clientX - rect.left}px`;
      ripple.style.top = `${event.clientY - rect.top}px`;
      button.appendChild(ripple);
      setTimeout(() => ripple.remove(), 500);
    }
    onClick?.(event);
  };

  return (
    <button
      ref={ref}
      type={type}
      onClick={handleClick}
      className={`btn btn-${variant} ${className}`.trim()}
      {...props}
    >
      {children}
    </button>
  );
}
