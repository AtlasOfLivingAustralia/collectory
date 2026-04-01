import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export default function NotFound() {
  const { t } = useTranslation();

  return (
    <div className="container text-center py-5">
      <h1 className="display-4">404</h1>
      <p className="lead text-muted">
        {t('error.notFound', 'The page you are looking for does not exist.')}
      </p>
      <Link to="/" className="btn btn-primary">
        {t('common.returnHome', 'Return to Home')}
      </Link>
    </div>
  );
}
