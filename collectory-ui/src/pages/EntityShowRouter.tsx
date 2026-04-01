import { useParams, Navigate } from 'react-router-dom';
import { entityTypeFromUid } from '../api/types';

export default function EntityShowRouter() {
  const { id } = useParams<{ id: string }>();
  if (!id) return <Navigate to="/" replace />;

  const entityType = entityTypeFromUid(id);
  switch (entityType) {
    case 'collection':
      return <Navigate to={`/public/showCollection/${id}`} replace />;
    case 'institution':
      return <Navigate to={`/public/showInstitution/${id}`} replace />;
    case 'dataResource':
      return <Navigate to={`/public/showDataResource/${id}`} replace />;
    case 'dataProvider':
      return <Navigate to={`/public/showDataProvider/${id}`} replace />;
    case 'dataHub':
      return <Navigate to={`/public/showDataHub/${id}`} replace />;
    case 'tempDataResource':
      return <Navigate to={`/public/showTempDataResource/${id}`} replace />;
    default:
      return <Navigate to="/" replace />;
  }
}
