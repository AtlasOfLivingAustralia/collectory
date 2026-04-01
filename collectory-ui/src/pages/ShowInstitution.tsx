import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import apiClient from '../api/client';
import { useInstitution } from '../hooks/useInstitutions';
import { useConfig } from '../hooks/useConfig';
import EntityImage from '../components/public/EntityImage';
import ContactsPanel from '../components/public/ContactsPanel';
import DataAccessPanel from '../components/public/DataAccessPanel';
import UsageStats from '../components/public/UsageStats';
import BiocacheCharts from '../components/public/BiocacheCharts';
import ExternalIds from '../components/public/ExternalIds';
import LocationPanel from '../components/public/LocationPanel';
import EditButton from '../components/public/EditButton';
import NetworkMembership from '../components/public/NetworkMembership';
import Breadcrumb from '../components/public/Breadcrumb';
import FormattedText from '../components/common/FormattedText';

export default function ShowInstitution() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: institution, isLoading, error } = useInstitution(id ?? '');
  const { data: config } = useConfig();
  const [activeTab, setActiveTab] = useState('metadata');

  const { data: biocacheCount } = useQuery({
    queryKey: ['biocacheCount', institution?.uid, 'institution_uid'],
    queryFn: async () => {
      if (!institution?.uid) return 0;
      const { data } = await apiClient.get<{ totalRecords: number }>(
        `/proxy/biocache/occurrences/search`,
        { params: { q: `institution_uid:${institution.uid}`, pageSize: 0 } },
      );
      return data.totalRecords;
    },
    enabled: !!institution?.uid,
    staleTime: 60_000,
  });

  if (isLoading) {
    return (
      <div className="container mt-4">
        <div className="d-flex justify-content-center">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">{t('common.loading', 'Loading...')}</span>
          </div>
        </div>
      </div>
    );
  }

  if (error || !institution) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  const count = biocacheCount ?? 0;
  const showLoggerTab = !config?.disableLoggerLinks;
  const orgNameLong = config?.orgNameLong ?? 'Atlas of Living Australia';

  return (
    <>
    <Breadcrumb
      parent={{ url: '/', label: t('breadcrumb.collections', 'Collections') }}
      current={institution.name}
    />
    <div className="row">
      <div className="col-md-9">
        <h1>{institution.name}</h1>
        <EditButton entityType="institution" uid={institution.uid} />

        {/* Parent institutions (listParents) — shown below title, matching showInstitution.gsp */}
        {institution.parentInstitutions && institution.parentInstitutions.length > 0 && (
          <>
            {institution.parentInstitutions.map((p) => (
              <h2 key={p.uid}>
                <Link to={`/public/show/${p.uid}`}>{p.name}</Link>
              </h2>
            ))}
          </>
        )}

        <div className="tabbable">
          <ul className="nav nav-tabs" id="home-tabs">
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'metadata' ? 'active' : ''}`}
                href="#basic-metadata"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('metadata'); }}
              >
                {t('show.tab.metadata', 'Metadata')}
              </a>
            </li>
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'collections' ? 'active' : ''}`}
                href="#collections"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('collections'); }}
              >
                {t('show.tab.collections', 'Collections')}
              </a>
            </li>
            {showLoggerTab && (
              <li className="nav-item">
                <a
                  className={`nav-link ${activeTab === 'usage' ? 'active' : ''}`}
                  href="#usage-stats"
                  data-bs-toggle="tab"
                  onClick={(e) => { e.preventDefault(); setActiveTab('usage'); }}
                >
                  {t('show.tab.usage.stats', 'Usage Stats')}
                </a>
              </li>
            )}
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'metrics' ? 'active' : ''}`}
                href="#metrics"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('metrics'); }}
              >
                {t('show.tab.metrics', 'Metrics')}
              </a>
            </li>
          </ul>
        </div>

        <div className="tab-content">
          {/* Metadata tab */}
          <div id="basic-metadata" className={`tab-pane ${activeTab === 'metadata' ? 'show active' : ''}`}>
            {institution.pubDescription && (
              <>
                <h2>{t('public.des', 'Description')}</h2>
                <FormattedText text={institution.pubDescription} />
                {institution.techDescription && <FormattedText text={institution.techDescription} />}
              </>
            )}
            {institution.focus && (
              <>
                <h2>{t('public.si.content.label02.param', `Contribution to ${config?.orgNameShort ?? 'ALA'}`)}</h2>
                <FormattedText text={institution.focus} />
              </>
            )}

            <p>
              <span id="numBiocacheRecords">{count.toLocaleString()} records</span>{' '}
              can be accessed through the {orgNameLong}.
            </p>
            <p>
              <a
                href={`${config?.biocacheUiUrl ?? ''}/occurrences/search?q=institution_uid:${institution.uid}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                Click to view records for the {institution.name}.
              </a>
            </p>

            <p className="text-muted small">
              Metadata last updated on {institution.lastUpdated}
            </p>
          </div>

          {/* Collections tab */}
          <div id="collections" className={`tab-pane ${activeTab === 'collections' ? 'show active' : ''}`}>
            <h2>{t('public.si.content.label03', 'Collections')}</h2>
            {institution.collections && institution.collections.length > 0 ? (
              <ol>
                {[...institution.collections]
                  .sort((a, b) => a.name.localeCompare(b.name))
                  .map((col) => (
                    <li key={col.uid}>
                      <Link to={`/public/show/${col.uid}`}>{col.name}</Link>
                      {col.abstract && <> {col.abstract}</>}
                    </li>
                  ))}
              </ol>
            ) : (
              <p className="text-muted">
                {t('institution.noCollections', 'No collections associated with this institution.')}
              </p>
            )}
          </div>

          {/* Usage Stats tab */}
          {showLoggerTab && (
            <div id="usage-stats" className={`tab-pane ${activeTab === 'usage' ? 'show active' : ''}`}>
              <UsageStats
                entityUid={institution.uid}
                eventId="1002"
                loggerUrl={config?.loggerUrl}
                disableLoggerLinks={config?.disableLoggerLinks}
              />
            </div>
          )}

          {/* Metrics tab */}
          <div id="metrics" className={`tab-pane ${activeTab === 'metrics' ? 'show active' : ''}`}>
            <h2>{t('records.digitisedRecords', 'Digitised records')}</h2>
            <div id="recordsBreakdown" className="section vertical-charts">
              <BiocacheCharts
                entityUid={institution.uid}
                facetField="institution_uid"
                biocacheServicesUrl={config?.biocacheServicesUrl}
                biocacheUiUrl={config?.biocacheUiUrl}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Sidebar */}
      <section className="col-md-3">
        {/* Logo */}
        {(institution.logoRef?.uri ?? institution.logoRef?.file) && (
          <section className="public-metadata">
            <img className="institutionImage" src={institution.logoRef!.uri ?? `/data/institution/${institution.logoRef!.file}`} alt={institution.name} />
          </section>
        )}

        <EntityImage
          imageRef={institution.imageRef}
          logoRef={undefined}
          entityName={institution.name}
        />

        <DataAccessPanel
          entityUid={institution.uid}
          facetField="institution_uid"
          biocacheUiUrl={config?.biocacheUiUrl}
          biocacheServicesUrl={config?.biocacheServicesUrl}
          loggerUrl={config?.loggerUrl}
          alertsUrl={config?.alertsUrl}
          recordCount={count}
          disableLoggerLinks={config?.disableLoggerLinks}
          disableAlertLinks={config?.disableAlertLinks}
        />

        <LocationPanel
          address={institution.address}
          phone={institution.phone}
          email={institution.email}
        />

        <ContactsPanel entityType="institution" entityUid={institution.uid} />

        {institution.websiteUrl && (
          <section className="public-metadata">
            <h4>{t('public.website', 'Website')}</h4>
            <div className="webSite">
              <a className="external" target="_blank" rel="noopener noreferrer" href={institution.websiteUrl}>
                Visit institution website
              </a>
            </div>
          </section>
        )}

        <NetworkMembership networkMembership={institution.networkMembership} entityType="institution" />

        <ExternalIds entityType="institution" entityUid={institution.uid} />
      </section>
    </div>
    </>
  );
}
