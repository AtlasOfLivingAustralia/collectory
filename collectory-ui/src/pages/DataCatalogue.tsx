import { useTranslation } from 'react-i18next';

export default function DataCatalogue() {
  const { t } = useTranslation();

  return (
    <div className="container mt-4">
      <h1>{t('data.catalogue.title01', 'Collectory web services')}</h1>
      <p>
        {t(
          'data.catalogue.des01',
          'Documentation for the web services provided by the Collectory.',
        )}
      </p>

      <h2>{t('data.catalogue.title02', 'Core entities')}</h2>
      <p>{t('data.catalogue.des05', 'The Collectory manages metadata for these entities')}:</p>
      <ul>
        <li>
          <strong>{t('data.catalogue.ws0024.collections', 'Collections')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.collections01', 'natural history collections')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.institutions', 'Institutions')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.institutions01', 'organisations that hold collections')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.dps', 'Data providers')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.dps01', 'organisations that supply data')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.drs', 'Data resources')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.drs01', 'datasets')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.tdrs', 'Temporary data resources')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.tdrs01', 'user-uploaded datasets')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.dhs', 'Data hubs')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.dhs01', 'aggregations of data resources')}
        </li>
        <li>
          <strong>{t('data.catalogue.ws0024.contacts', 'Contacts')}</strong> &ndash;{' '}
          {t('data.catalogue.ws0024.contacts01', 'people associated with entities')}
        </li>
      </ul>

      <h3>{t('data.catalogue.title03', 'Entity endpoints')}</h3>
      <table className="table table-bordered">
        <thead>
          <tr>
            <th>{t('common.description', 'Description')}</th>
            <th>{t('common.url', 'URL')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('data.catalogue.listAll', 'List all entities')}</td>
            <td>
              <code>/ws/collection</code>, <code>/ws/institution</code>,{' '}
              <code>/ws/dataResource</code>, <code>/ws/dataProvider</code>,{' '}
              <code>/ws/dataHub</code>, <code>/ws/tempDataResource</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.getEntity', 'Get a single entity')}</td>
            <td>
              <code>{'/ws/{entity}/{uid}'}</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.contacts', 'Get contacts for an entity')}</td>
            <td>
              <code>{'/ws/{entity}/{uid}/contacts'}</code>
            </td>
          </tr>
        </tbody>
      </table>

      <h2>{t('data.catalogue.lookupTitle', 'Lookup services')}</h2>
      <table className="table table-bordered">
        <thead>
          <tr>
            <th>{t('common.description', 'Description')}</th>
            <th>{t('common.url', 'URL')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('data.catalogue.lookupSummary', 'Summary from UID')}</td>
            <td>
              <code>/ws/lookup/summary/{'{uid}'}</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.lookupName', 'Name from UID')}</td>
            <td>
              <code>/ws/lookup/name/{'{uid}'}</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.lookupCollection', 'Collection from codes')}</td>
            <td>
              <code>/ws/lookup/collection?instCode=X&collCode=Y</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.lookupTaxonHints', 'Taxonomic coverage hints')}</td>
            <td>
              <code>/ws/lookup/taxonomyCoverageHints</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.lookupCitations', 'Citations')}</td>
            <td>
              <code>/ws/lookup/citations</code>
            </td>
          </tr>
        </tbody>
      </table>

      <h2>{t('data.catalogue.feedsTitle', 'Data feeds')}</h2>
      <table className="table table-bordered">
        <thead>
          <tr>
            <th>{t('common.description', 'Description')}</th>
            <th>{t('common.url', 'URL')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('data.catalogue.emlMetadata', 'EML metadata')}</td>
            <td>
              <code>/eml/{'{uid}'}</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.rifcs', 'RIF-CS feed')}</td>
            <td>
              <code>/rif-cs</code>
            </td>
          </tr>
          <tr>
            <td>{t('data.catalogue.sitemap', 'Sitemap')}</td>
            <td>
              <code>/sitemap.xml</code>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
