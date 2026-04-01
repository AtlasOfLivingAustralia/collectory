import { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

function entityTypeFromUid(uid: string): string {
  const prefix = uid.substring(0, 2);
  switch (prefix) {
    case 'co':
      return 'collection';
    case 'in':
      return 'institution';
    case 'dp':
      return 'dataProvider';
    case 'dr':
      return 'dataResource';
    case 'dh':
      return 'dataHub';
    default:
      return 'collection';
  }
}

export default function ManageShow() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (id) {
      const entityType = entityTypeFromUid(id);
      navigate(`/${entityType}/show/${id}`, { replace: true });
    }
  }, [id, navigate]);

  return (
    <div className="d-flex justify-content-center p-5">
      <div className="spinner-border" role="status">
        <span className="visually-hidden">Redirecting...</span>
      </div>
    </div>
  );
}
