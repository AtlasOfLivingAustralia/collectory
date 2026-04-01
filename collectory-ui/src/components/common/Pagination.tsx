import { useTranslation } from 'react-i18next';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export default function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  const { t } = useTranslation();

  if (totalPages <= 1) return null;

  // Build a window of page numbers around current page
  const pages: (number | 'ellipsis-start' | 'ellipsis-end')[] = [];
  const maxVisible = 7;

  if (totalPages <= maxVisible) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1);
    if (currentPage > 3) pages.push('ellipsis-start');

    const start = Math.max(2, currentPage - 1);
    const end = Math.min(totalPages - 1, currentPage + 1);
    for (let i = start; i <= end; i++) pages.push(i);

    if (currentPage < totalPages - 2) pages.push('ellipsis-end');
    pages.push(totalPages);
  }

  return (
    <nav aria-label={t('pagination.label', 'Page navigation')}>
      <ul className="pagination justify-content-center">
        <li className={`page-item ${currentPage === 1 ? 'disabled' : ''}`}>
          <button
            className="page-link"
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 1}
            aria-label={t('pagination.previous', 'Previous')}
          >
            &laquo; {t('pagination.prev', 'Prev')}
          </button>
        </li>

        {pages.map((page, idx) =>
          typeof page === 'string' ? (
            <li key={page} className="page-item disabled">
              <span className="page-link">&hellip;</span>
            </li>
          ) : (
            <li key={`page-${idx}`} className={`page-item ${page === currentPage ? 'active' : ''}`}>
              <button
                className="page-link"
                onClick={() => onPageChange(page)}
                aria-current={page === currentPage ? 'page' : undefined}
              >
                {page}
              </button>
            </li>
          ),
        )}

        <li className={`page-item ${currentPage === totalPages ? 'disabled' : ''}`}>
          <button
            className="page-link"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
            aria-label={t('pagination.next', 'Next')}
          >
            {t('pagination.next', 'Next')} &raquo;
          </button>
        </li>
      </ul>
    </nav>
  );
}
