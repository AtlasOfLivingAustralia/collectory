import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

export default function GbifSyncAllResources() {
  const { t } = useTranslation();
  const [syncing, setSyncing] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);

  async function handleSync() {
    if (!window.confirm(t('Are you sure you want to sync all resources with GBIF? This may take several minutes.'))) {
      return;
    }

    setSyncing(true);
    setResult(null);
    try {
      await apiClient.post('/gbif/syncAllResources');
      setResult({
        success: true,
        message: t('Syncing has started for providers/organisations. This will continue in the background for several minutes.'),
      });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : t('User does not have sufficient privileges.');
      setResult({ success: false, message });
    } finally {
      setSyncing(false);
    }
  }

  return (
    <ProtectedRoute requiredRole="ROLE_ADMIN">
      <div className="container mt-4">
        <nav aria-label="breadcrumb" className="mb-3">
          <ol className="breadcrumb">
            <li className="breadcrumb-item"><Link to="/manage/list">Manage</Link></li>
            <li className="breadcrumb-item active">Sync All Resources</li>
          </ol>
        </nav>
        <h1>{t('GBIF Sync - sync results')}</h1>

        {result && (
          <div className={`alert ${result.success ? 'alert-success' : 'alert-warning'}`}>
            {result.success ? (
              <i className="fa fa-check me-1" />
            ) : (
              <i className="fa fa-exclamation-triangle me-1" />
            )}
            {result.message}
          </div>
        )}

        <button
          type="button"
          className="btn btn-warning"
          disabled={syncing}
          onClick={handleSync}
        >
          {syncing ? (
            <>
              <span className="spinner-border spinner-border-sm me-1" role="status" />
              {t('Syncing...')}
            </>
          ) : (
            <>
              <i className="fa fa-refresh me-1" />
              {t('Sync All Resources')}
            </>
          )}
        </button>
      </div>
    </ProtectedRoute>
  );
}
