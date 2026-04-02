import { Link } from "react-router-dom";

export default function NotFoundPage() {
  return (
    <div className="page-center">
      <h1>404</h1>
      <p>Page not found.</p>
      <Link to="/">Return home</Link>
    </div>
  );
}
