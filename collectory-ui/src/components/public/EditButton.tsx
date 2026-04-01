import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';

interface EditButtonProps {
  entityType: string;
  uid: string;
}

export default function EditButton({ entityType, uid }: EditButtonProps) {
  const { t } = useTranslation();

  // Show edit button only if user has an auth token
  const token = sessionStorage.getItem('oidc_access_token');
  if (!token) return null;

  return (
    <div style={{ float: 'right' }}>
      <Link
        to={`/${entityType}/edit/${uid}`}
        className="btn btn-primary"
        title={t('common.editTooltip', 'Edit this entity')}
      >
        <span className="fa fa-pencil" aria-hidden="true" />&nbsp;&nbsp;{t('common.edit', 'Edit')}
      </Link>
    </div>
  );
}
