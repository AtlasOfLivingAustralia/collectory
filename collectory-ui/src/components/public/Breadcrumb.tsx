import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useConfig } from '../../hooks/useConfig';
import { useEffect, useState } from 'react';

export interface BreadcrumbItem {
  /** URL to link to. Use absolute URLs for external links, relative for SPA routes */
  url: string;
  /** Display label */
  label: string;
}

interface BreadcrumbProps {
  /** Parent breadcrumb (e.g., Collections → /public/map) */
  parent?: BreadcrumbItem;
  /** Additional intermediate breadcrumbs */
  extra?: BreadcrumbItem[];
  /** Current page title (shown as active/final item) */
  current: string;
}

/**
 * Renders an ALA-style breadcrumb bar matching the ala-main.gsp layout.
 *
 * Uses a portal to render into #breadcrumb-slot (placed between ALA header
 * and main content in AlaLayout), so the breadcrumb sits outside the content
 * container — matching the GSP layout where the breadcrumb section spans
 * full width between header and <div id="main">.
 *
 * Structure:
 *   Home > [Parent] > [Extra items...] > Current Page
 */
export default function Breadcrumb({ parent, extra, current }: BreadcrumbProps) {
  const { t } = useTranslation();
  const { data: config } = useConfig();
  const [portalTarget, setPortalTarget] = useState<HTMLElement | null>(null);

  useEffect(() => {
    const el = document.getElementById('breadcrumb-slot');
    setPortalTarget(el);
  }, []);

  const homeUrl = config?.homeUrl ?? 'https://www.ala.org.au';

  const breadcrumbContent = (
    <section id="breadcrumb">
      <nav aria-label="Breadcrumb">
        <ol className="breadcrumb-list">
          <li>
            <a href={homeUrl}>{t('breadcrumb.home', 'Home')}</a>
          </li>
          {parent && (
            <li>
              {parent.url.startsWith('/') ? (
                <Link to={parent.url}>{parent.label}</Link>
              ) : (
                <a href={parent.url}>{parent.label}</a>
              )}
            </li>
          )}
          {extra?.map((item, i) => (
            <li key={i}>
              {item.url.startsWith('/') ? (
                <Link to={item.url}>{item.label}</Link>
              ) : (
                <a href={item.url}>{item.label}</a>
              )}
            </li>
          ))}
          <li aria-current="page" className="breadcrumb-item active">
            {current}
          </li>
        </ol>
      </nav>
    </section>
  );

  // Portal into the slot if available, otherwise render inline (fallback)
  if (portalTarget) {
    return createPortal(breadcrumbContent, portalTarget);
  }

  return breadcrumbContent;
}
