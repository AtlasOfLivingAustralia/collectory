import { useTranslation } from 'react-i18next';
import type { Address } from '../../api/types';

interface LocationPanelProps {
  address?: Address;
  phone?: string;
  email?: string;
}

export default function LocationPanel({ address, phone, email }: LocationPanelProps) {
  const { t } = useTranslation();

  if (!address && !phone && !email) return null;

  return (
    <section className="public-metadata">
      <h4>{t('location.title', 'Location')}</h4>
      {address && (
        <p>
          {address.street && <>{address.street}<br /></>}
          {address.city && <>{address.city}<br /></>}
          {address.state && <>{address.state} </>}
          {address.postcode && <>{address.postcode}<br /></>}
          {address.country && <>{address.country}<br /></>}
        </p>
      )}
      {email && (
        <><a href={`mailto:${email}`}>{t('contacts.emailContact', 'email this contact')}</a><br /></>
      )}
      {phone && <>{phone}</>}
    </section>
  );
}
