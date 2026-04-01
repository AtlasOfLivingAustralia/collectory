import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { reportsApi } from '../../api/endpoints/reports';
import ReportLayout from './ReportLayout';

export default function CollectionSpecimenDataReport() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['reports', 'collectionSpecimenData'],
    queryFn: reportsApi.collectionSpecimenData,
  });

  return (
    <ReportLayout title="Collection Specimen Data" isLoading={isLoading} error={error as Error}>
      {data && (
        <table className="table table-striped table-bordered">
          <thead className="table-light">
            <tr>
              <th>Collection</th>
              <th>Acronym</th>
              <th>Num Records</th>
              <th>Num Digitised</th>
              <th>Biocache Records</th>
            </tr>
          </thead>
          <tbody>
            {data.statistics.map(s => (
              <tr key={s.uid}>
                <td><Link to={`/public/show/${s.uid}`}>{s.name}</Link></td>
                <td>{s.acronym}</td>
                <td>{s.numRecords != null && s.numRecords !== -1 ? s.numRecords.toLocaleString() : ''}</td>
                <td>{s.numRecordsDigitised != null && s.numRecordsDigitised !== -1 ? s.numRecordsDigitised.toLocaleString() : ''}</td>
                <td>{s.numBiocacheRecords != null && s.numBiocacheRecords !== -1 ? s.numBiocacheRecords.toLocaleString() : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </ReportLayout>
  );
}
