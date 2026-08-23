import { Files } from 'lucide-react';
import { Link } from 'react-router-dom';

const Brand = ({ compact = false }) => (
  <Link
    className={`brand ${compact ? 'brand--compact' : ''}`}
    to="/"
    aria-label="PDF Tools home"
  >
    <span className="brand__mark" aria-hidden="true">
      <Files />
    </span>
    <span className="brand__name">
      pdf <strong>tools</strong>
    </span>
  </Link>
);

export default Brand;
