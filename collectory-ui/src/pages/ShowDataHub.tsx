import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import apiClient from '../api/client';
import type { DataHub } from '../api/types';
import { useConfig } from '../hooks/useConfig';
import EntityImage from '../components/public/EntityImage';
import ContactsPanel from '../components/public/ContactsPanel';
import DataAccessPanel from '../components/public/DataAccessPanel';
import BiocacheCharts from '../components/public/BiocacheCharts';
import ExternalIds from '../components/public/ExternalIds';
import LocationPanel from '../components/public/LocationPanel';
import EditButton from '../components/public/EditButton';
import NetworkMembership from '../components/public/NetworkMembership';
import Breadcrumb from '../components/public/Breadcrumb';
import FormattedText from '../components/common/FormattedText';

export default function ShowDataHub() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const { data: hub, isLoading, error } = useQuery<DataHub>({
    queryKey: ['dataHub', id],
    queryFn: () => apiClient.get<DataHub>(`/dataHub/${id}`).then((r) => r.data),
    enabled: !!id,
  });
  const { data: config } = useConfig();

  const { data: biocacheCount } = useQuery({
    queryKey: ['biocacheCount', hub?.uid, 'data_hub_uid'],
    queryFn: async () => {
      if (!hub) return 0;
      const { data } = await apiClient.get<{ totalRecords: number }>(
        `/proxy/biocache/occurrences/search`,
        { params: { q: `data_hub_uid:${hub.uid}`, pageSize: 0 } },
      );
      return data.totalRecords;
    },
    enabled: !!hub,
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

  if (error || !hub) {
    return (
      <div className="container mt-4">
        <div className="alert alert-danger">
          {t('error.notFound', 'Entity not found or an error occurred.')}
        </div>
      </div>
    );
  }

  const orgNameLong = config?.orgNameLong ?? 'Atlas of Living Australia';
  const count = biocacheCount ?? 0;
  const [lsidVisible, setLsidVisible] = useState(false);

  return (
    <>
    <Breadcrumb current={hub.name} />
    <div id="content">
      <div className="row">
        <div className="col-md-8">
          <h1>{hub.name}</h1>
          <EditButton entityType="dataHub" uid={hub.uid} />
          {hub.acronym && (
            <span className="acronym">Acronym: {hub.acronym}</span>
          )}
          {hub.guid?.startsWith('urn:lsid:') && (
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
                    <a href={hub.guid} target="_blank" rel="noopener noreferrer">{hub.guid}</a>
                  </p>
                  <p style={{ fontSize: 12 }}>
                    {t('public.lsidtext.des', 'This is the Life Science Identifier for this record')}
                  </p>
                </div>
              )}
            </span>
          )}
        </div>
        <div className="col-md-4">
          {hub.logoRef?.file && (
            <section className="public-metadata">
              <img className="institutionImage" src={`/data/dataHub/${hub.logoRef.file}`} alt={hub.name} />
            </section>
          )}
        </div>
      </div>

      <div className="row">
        <div id="column-one" className="col-md-8">
          <div className="section">
            {hub.pubDescription && (
              <>
                <h2>{t('public.des', 'Description')}</h2>
                <FormattedText text={hub.pubDescription} />
                {hub.techDescription && <FormattedText text={hub.techDescription} />}
              </>
            )}
            {hub.focus && (
              <>
                <h2>{t('public.sdh.co.label02.param', `Contribution to ${config?.orgNameShort ?? 'ALA'}`)}</h2>
                <FormattedText text={hub.focus} />
              </>
            )}

            <h2>{t('public.sdh.co.label03', 'Records')}</h2>
            <p>
              <span id="numBiocacheRecords">{count.toLocaleString()}</span>{' '}
              records are available from {orgNameLong}.
              {' '}
              <a
                href={`${config?.biocacheUiUrl ?? ''}/occurrences/search?q=data_hub_uid:${hub.uid}`}
                className="btn btn-outline-dark"
                target="_blank"
                rel="noopener noreferrer"
              >
                {t('public.sdh.co.allrecords', 'View all records')}
              </a>
            </p>

            <div id="charts" className="section vertical-charts">
              <BiocacheCharts
                entityUid={hub.uid}
                facetField="data_hub_uid"
                biocacheServicesUrl={config?.biocacheServicesUrl}
                biocacheUiUrl={config?.biocacheUiUrl}
              />
            </div>

            <p className="text-muted small">
              {t('common.lastUpdated', 'Last updated')}: {new Date(hub.lastUpdated).toLocaleDateString()}
              {hub.userLastModified && <span> by {hub.userLastModified}</span>}
            </p>
          </div>
        </div>

        <div id="column-two" className="col-md-4">
          <section className="section sidebar">
            <EntityImage
              imageRef={hub.imageRef}
              logoRef={undefined}
              entityName={hub.name}
            />

            <DataAccessPanel
              entityUid={hub.uid}
              facetField="data_hub_uid"
              biocacheUiUrl={config?.biocacheUiUrl}
              biocacheServicesUrl={config?.biocacheServicesUrl}
              loggerUrl={config?.loggerUrl}
              alertsUrl={config?.alertsUrl}
              recordCount={count}
              disableLoggerLinks={config?.disableLoggerLinks}
              disableAlertLinks={config?.disableAlertLinks}
            />

            <LocationPanel
              address={hub.address}
              phone={hub.phone}
              email={hub.email}
            />

            <ContactsPanel entityType="dataHub" entityUid={hub.uid} />

            {hub.websiteUrl && (
              <section className="public-metadata">
                <h4>{t('public.website', 'Website')}</h4>
                <div className="webSite">
                  <a className="external_icon" target="_blank" rel="noopener noreferrer" href={hub.websiteUrl}>
                    Visit website
                  </a>
                </div>
              </section>
            )}

            <NetworkMembership networkMembership={hub.networkMembership} entityType="dataHub" />

            <ExternalIds entityType="dataHub" entityUid={hub.uid} />
          </section>
        </div>
      </div>
    </div>
    </>
  );
}
