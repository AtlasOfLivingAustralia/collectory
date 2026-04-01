import { useTranslation } from 'react-i18next';
import type { NetworkMembershipEntry } from '../../api/types';

type EntityType = 'collection' | 'institution' | 'dataResource' | 'dataProvider' | 'dataHub';

interface NetworkMembershipProps {
  networkMembership?: NetworkMembershipEntry[] | string;
  entityType?: EntityType;
}

function parseNetworks(raw: NetworkMembershipEntry[] | string | undefined): NetworkMembershipEntry[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed;
  } catch { /* ignore */ }
  return [];
}

export default function NetworkMembership({ networkMembership, entityType = 'collection' }: NetworkMembershipProps) {
  const { t } = useTranslation();

  const networks = parseNetworks(networkMembership);
  if (networks.length === 0) return null;

  const chafcImage = entityType === 'collection'
    ? '/data/network/chafc.png'
    : (entityType === 'institution' || entityType === 'dataResource')
      ? '/data/network/CHAFC_sm.jpg'
      : null;

  return (
    <section className="public-metadata">
      <h4>{t('public.network.membership.label', 'Membership')}</h4>
      {networks.map((network, i) => {
        const acronym = network.acronym?.toUpperCase();
        const label = network.name
          ? `Member of ${network.name}${acronym ? ` (${acronym})` : ''}`
          : acronym ?? '';

        if (acronym === 'CHAEC') {
          return (
            <div key={i}>
              <p>{label}</p>
              <img src="/data/network/chaec-logo.png" alt="CHAEC" />
            </div>
          );
        }
        if (acronym === 'CHAH') {
          return (
            <div key={i}>
              <p>{label}</p>
              <a target="_blank" rel="noopener noreferrer" href="http://www.chah.gov.au">
                <img src="/data/network/CHAH_logo_col_70px_white.gif" alt="CHAH" />
              </a>
            </div>
          );
        }
        if (acronym === 'CHAFC') {
          return (
            <div key={i}>
              <p>{label}</p>
              {chafcImage && <img src={chafcImage} alt="CHAFC" />}
            </div>
          );
        }
        if (acronym === 'CHACM') {
          return (
            <div key={i}>
              <p>{label}</p>
              <img src="/data/network/chacm.png" alt="CHACM" />
            </div>
          );
        }
        // Generic network — just show the label
        return <p key={i}>{label}</p>;
      })}
    </section>
  );
}
