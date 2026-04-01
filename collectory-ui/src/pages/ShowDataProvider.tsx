import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';
import { dataProvidersApi } from '../api/endpoints/dataProviders';
import { useConfig } from '../hooks/useConfig';
import type { DataProvider } from '../api/types';
import EntityImage from '../components/public/EntityImage';
import ContactsPanel from '../components/public/ContactsPanel';
import DataAccessPanel from '../components/public/DataAccessPanel';
import ExternalIds from '../components/public/ExternalIds';
import LocationPanel from '../components/public/LocationPanel';
import UsageStats from '../components/public/UsageStats';
import BiocacheCharts from '../components/public/BiocacheCharts';
import EditButton from '../components/public/EditButton';
import NetworkMembership from '../components/public/NetworkMembership';
import Breadcrumb from '../components/public/Breadcrumb';
import FormattedText from '../components/common/FormattedText';

export default function ShowDataProvider() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: provider, isLoading, error } = useQuery<DataProvider>({
    queryKey: ['dataProvider', id],
    queryFn: () => dataProvidersApi.get(id ?? ''),
    enabled: !!id,
  });
  const { data: config } = useConfig();
  const [activeTab, setActiveTab] = useState('overview');
  const [lsidVisible, setLsidVisible] = useState(false);

  // hasRecords: at least one resource has resourceType == 'records'
  const hasRecords = (provider?.resources ?? []).some((r) => r.resourceType === 'records');

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

  if (error || !provider) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  // Fetch biocache count for DataAccessPanel
  const { data: biocacheCount } = useQuery({
    queryKey: ['biocacheCount', provider?.uid, 'data_provider_uid'],
    queryFn: async () => {
      try {
        const { data } = await apiClient.get<{ totalRecords: number }>(
          `/proxy/biocache/occurrences/search`,
          { params: { q: `data_provider_uid:${provider!.uid}`, pageSize: 0 } },
        );
        return data.totalRecords ?? 0;
      } catch {
        return 0;
      }
    },
    enabled: !!provider?.uid,
    staleTime: 60_000,
  });

  const showLoggerTab = hasRecords && !config?.disableLoggerLinks;

  return (
    <>
    <Breadcrumb
      parent={{ url: '/public/datasets', label: t('breadcrumb.dataproviders', 'Data partners') }}
      current={provider.name}
    />
    <div className="row">
      <div className="col-md-9">
        <h1>{provider.name}</h1>
        <EditButton entityType="dataProvider" uid={provider.uid} />
        {provider.acronym && (
          <span className="acronym">Acronym: {provider.acronym}</span>
        )}
        {provider.guid?.startsWith('urn:lsid:') && (
          <span className="lsid">
            {' '}
            <a
              href="#lsidText"
              className="local"
              title="Life Science Identifier (pop-up)"
              onClick={(e) => { e.preventDefault(); setLsidVisible((v) => !v); }}
            >
              {t('public.lsid', 'LSID')}
            </a>
            {lsidVisible && (
              <div id="lsidText" style={{ textAlign: 'left', marginTop: 8 }}>
                <b>
                  <a
                    className="external_icon"
                    href="https://wayback.archive.org/web/20100515104710/http://lsids.sourceforge.net:80/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {t('public.lsidtext.link', 'Life Science Identifiers')}:
                  </a>
                </b>
                <p style={{ margin: '10px 0' }}>
                  <a href={provider.guid} target="_blank" rel="noopener noreferrer">
                    {provider.guid}
                  </a>
                </p>
                <p style={{ fontSize: 12 }}>
                  {t('public.lsidtext.des', 'This is the Life Science Identifier for this record')}
                </p>
              </div>
            )}
          </span>
        )}

        <div className="tabbable">
          <ul className="nav nav-tabs" id="home-tabs">
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'overview' ? 'active' : ''}`}
                href="#basic-metadata"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('overview'); }}
              >
                {t('show.tab.metadata', 'Metadata')}
              </a>
            </li>
            <li className="nav-item">
              <a
                className={`nav-link ${activeTab === 'resources' ? 'active' : ''}`}
                href="#data-resources"
                data-bs-toggle="tab"
                onClick={(e) => { e.preventDefault(); setActiveTab('resources'); }}
              >
                {t('show.tab.data.resources', 'Data resources')}
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
          <div id="basic-metadata" className={`tab-pane ${activeTab === 'overview' ? 'show active' : ''}`}>
            {provider.pubDescription && (
              <>
                <h2>{t('public.des', 'Description')}</h2>
                <FormattedText text={provider.pubDescription} />
                {provider.techDescription && <FormattedText text={provider.techDescription} />}
              </>
            )}
            {provider.focus && (
              <>
                <h2>{t('public.sdp.content.label02.param', `Contribution to ${config?.orgNameShort ?? 'ALA'}`)}</h2>
                <FormattedText text={provider.focus} />
              </>
            )}
            <p className="text-muted small">
              {t('common.lastUpdated', 'Last updated')}: {new Date(provider.lastUpdated).toLocaleDateString()}
              {provider.userLastModified && <span> by {provider.userLastModified}</span>}
            </p>
          </div>

          {/* Data resources tab */}
          <div id="data-resources" className={`tab-pane ${activeTab === 'resources' ? 'show active' : ''}`}>
            <h2>{t('public.sdp.content.label03', 'Data resources')}</h2>
            {(() => {
              const publicResources = (provider.resources ?? [])
                .filter((r) => !r.isPrivate)
                .sort((a, b) => a.name.localeCompare(b.name));
              return publicResources.length > 0 ? (
                <ol>
                  {publicResources.map((res) => (
                    <li key={res.uid}>
                      <Link to={`/public/show/${res.uid}`}>{res.name}</Link>
                      <br />
                      {res.pubDescription && (
                        <span style={{ color: '#555' }}>{res.pubDescription.substring(0, 400)}</span>
                      )}
                    </li>
                  ))}
                </ol>
              ) : (
                <p>{t('public.sdp.content.noresources', 'No data resources.')}</p>
              );
            })()}
          </div>

          {/* Usage Stats tab */}
          {showLoggerTab && (
            <div id="usage-stats" className={`tab-pane ${activeTab === 'usage' ? 'show active' : ''}`}>
              <h2>{t('public.sdp.usagestats.label', 'Usage statistics')}</h2>
              <UsageStats
                entityUid={provider.uid}
                eventId="1002"
                loggerUrl={config?.loggerUrl}
                disableLoggerLinks={config?.disableLoggerLinks}
              />
            </div>
          )}

          {/* Metrics tab */}
          <div id="metrics" className={`tab-pane ${activeTab === 'metrics' ? 'show active' : ''}`}>
            <BiocacheCharts
              entityUid={provider.uid}
              facetField="data_provider_uid"
              biocacheServicesUrl={config?.biocacheServicesUrl}
              biocacheUiUrl={config?.biocacheUiUrl}
            />
          </div>
        </div>
      </div>

      {/* Sidebar */}
      <div className="col-md-3">
        {/* Logo */}
        {provider.logoRef?.file && (
          <section className="public-metadata">
            <img className="institutionImage" src={`/data/dataProvider/${provider.logoRef.file}`} alt={provider.name} />
          </section>
        )}

        <DataAccessPanel
          entityUid={provider.uid}
          facetField="data_provider_uid"
          biocacheUiUrl={config?.biocacheUiUrl}
          biocacheServicesUrl={config?.biocacheServicesUrl}
          loggerUrl={config?.loggerUrl}
          alertsUrl={config?.alertsUrl}
          recordCount={biocacheCount ?? 0}
          disableLoggerLinks={config?.disableLoggerLinks}
          disableAlertLinks={config?.disableAlertLinks}
        />

        <EntityImage
          imageRef={provider.imageRef}
          logoRef={undefined}
          entityName={provider.name}
        />

        <LocationPanel
          address={provider.address}
          phone={provider.phone}
          email={provider.email}
        />

        <ContactsPanel entityType="dataProvider" entityUid={provider.uid} />

        {provider.websiteUrl && (
          <section className="public-metadata">
            <h4>{t('public.website', 'Website')}</h4>
            <div className="webSite">
              <a className="external_icon" target="_blank" rel="noopener noreferrer" href={provider.websiteUrl}>
                Visit provider website
              </a>
            </div>
          </section>
        )}

        <NetworkMembership networkMembership={provider.networkMembership} entityType="dataProvider" />

        <ExternalIds entityType="dataProvider" entityUid={provider.uid} />
      </div>
    </div>
    </>
  );
}
