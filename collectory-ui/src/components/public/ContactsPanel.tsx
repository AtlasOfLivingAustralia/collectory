import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { contactsApi } from '../../api/endpoints/contacts';
import type { ContactFor } from '../../api/types';

interface ContactsPanelProps {
  entityType: string;
  entityUid: string;
}

function buildName(cf: ContactFor): string {
  const c = cf.contact;
  const parts = [c.title, c.firstName, c.lastName].filter(Boolean);
  if (parts.length > 0) return parts.join(' ');
  if (c.organizationName) return c.organizationName;
  if (c.positionName) return c.positionName;
  return c.email ?? '';
}

export default function ContactsPanel({ entityType, entityUid }: ContactsPanelProps) {
  const { t } = useTranslation();
  const { data: contacts, isLoading } = useQuery({
    queryKey: ['contacts', entityType, entityUid],
    queryFn: () => contactsApi.forEntity(entityType, entityUid),
    enabled: !!entityUid,
  });

  if (isLoading) {
    return <div className="text-muted small">{t('common.loading', 'Loading...')}</div>;
  }

  if (!contacts || contacts.length === 0) return null;

  const sorted = [...contacts].sort((a, b) => {
    if (a.primaryContact && !b.primaryContact) return -1;
    if (!a.primaryContact && b.primaryContact) return 1;
    return 0;
  });

  return (
    <section className="public-metadata">
      <h4>{t('contacts.title', 'Contact')}</h4>
      {sorted.map((cf) => (
        <div key={cf.id} className="contact">
          <p>
            {cf.contact.publish && (
              <>
                <span className="contact-name contact-name-highlight">{buildName(cf)}</span><br />
                {cf.role && <><span className="contact-role">{cf.role}</span><br /></>}
                {cf.contact.positionName && cf.contact.positionName !== buildName(cf) && (
                  <><span className="contact-position">{cf.contact.positionName}</span><br /></>
                )}
                {cf.contact.organizationName && cf.contact.organizationName !== buildName(cf) && (
                  <><span className="contact-organization">{cf.contact.organizationName}</span><br /></>
                )}
                {cf.contact.phone && (
                  <><span className="contact-phone">{t('contacts.phone', 'Phone')}: {cf.contact.phone}</span><br /></>
                )}
                {cf.contact.fax && (
                  <>{t('contacts.fax', 'Fax')}: {cf.contact.fax}<br /></>
                )}
                {cf.contact.userId && (
                  <><span className="contact-id">ID: <a href={cf.contact.userId} className="user_id_link" target="_blank" rel="noopener noreferrer">{cf.contact.userId}</a></span><br /></>
                )}
                {cf.contact.email && (
                  <a href={`mailto:${cf.contact.email}`}>{t('contacts.emailContact', 'email this contact')}</a>
                )}
              </>
            )}
          </p>
        </div>
      ))}
    </section>
  );
}
