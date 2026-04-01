import { useEffect } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import apiClient from '../../api/client';
import { ProtectedRoute } from '../../auth/ProtectedRoute';

interface ContactRelationship {
  entityUid: string;
  entityName: string;
  role?: string;
  notify?: boolean;
  notifyFrequency?: string;
}

interface ContactProfile {
  id?: number;
  email?: string;
  title?: string;
  firstName?: string;
  lastName?: string;
  phone?: string;
  mobile?: string;
  fax?: string;
  contactRelationships: ContactRelationship[];
}

interface ContactFormValues {
  title: string;
  firstName: string;
  lastName: string;
  phone: string;
  mobile: string;
  fax: string;
  contactRelationships: {
    entityUid: string;
    role: string;
    notify: boolean;
    notifyFrequency: string;
  }[];
}

const TITLE_OPTIONS = ['', 'Mr', 'Mrs', 'Ms', 'Dr', 'Prof'];

export default function ContactProfile() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const userEmail = searchParams.get('userEmail') || '';

  const { data: profile, isLoading, error } = useQuery({
    queryKey: ['contact', 'profile', userEmail],
    queryFn: async () => {
      const params = userEmail ? { userEmail } : {};
      const { data } = await apiClient.get<ContactProfile>('/contact/profile', { params });
      return data;
    },
  });

  const { register, handleSubmit, reset } = useForm<ContactFormValues>();

  useEffect(() => {
    if (profile) {
      reset({
        title: profile.title || '',
        firstName: profile.firstName || '',
        lastName: profile.lastName || '',
        phone: profile.phone || '',
        mobile: profile.mobile || '',
        fax: profile.fax || '',
        contactRelationships: profile.contactRelationships.map((cr) => ({
          entityUid: cr.entityUid,
          role: cr.role || '',
          notify: cr.notify || false,
          notifyFrequency: cr.notifyFrequency || 'each',
        })),
      });
    }
  }, [profile, reset]);

  const mutation = useMutation({
    mutationFn: async (values: ContactFormValues) => {
      await apiClient.put('/contact/profile', values);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['contact', 'profile', userEmail] });
    },
  });

  function onSubmit(values: ContactFormValues) {
    mutation.mutate(values);
  }

  return (
    <ProtectedRoute>
      <div className="container mt-4">
        <h1>{t('My profile')}</h1>
        {profile?.email && (
          <p className="text-muted">{profile.email}</p>
        )}

        {isLoading && (
          <div className="d-flex justify-content-center p-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">{t('Loading...')}</span>
            </div>
          </div>
        )}

        {error && (
          <div className="alert alert-danger">
            {t('Failed to load profile')}: {error instanceof Error ? error.message : t('Unknown error')}
          </div>
        )}

        {mutation.isSuccess && (
          <div className="alert alert-success">
            {t('Profile updated successfully.')}
          </div>
        )}

        {mutation.isError && (
          <div className="alert alert-danger">
            {t('Failed to update profile')}: {mutation.error instanceof Error ? mutation.error.message : t('Unknown error')}
          </div>
        )}

        {profile && (
          <form onSubmit={handleSubmit(onSubmit)}>
            <div className="row">
              <div className="col-md-6">
                <div className="mb-3">
                  <label htmlFor="title" className="form-label">{t('Title')}</label>
                  <select id="title" className="form-select" {...register('title')}>
                    {TITLE_OPTIONS.map((opt) => (
                      <option key={opt} value={opt}>{opt || t('-- Select --')}</option>
                    ))}
                  </select>
                </div>

                <div className="mb-3">
                  <label htmlFor="firstName" className="form-label">{t('First Name')}</label>
                  <input id="firstName" type="text" className="form-control" {...register('firstName')} />
                </div>

                <div className="mb-3">
                  <label htmlFor="lastName" className="form-label">{t('Last Name')}</label>
                  <input id="lastName" type="text" className="form-control" {...register('lastName')} />
                </div>

                <div className="mb-3">
                  <label htmlFor="phone" className="form-label">{t('Phone')}</label>
                  <input id="phone" type="text" className="form-control" {...register('phone')} />
                </div>

                <div className="mb-3">
                  <label htmlFor="mobile" className="form-label">{t('Mobile')}</label>
                  <input id="mobile" type="text" className="form-control" {...register('mobile')} />
                </div>

                <div className="mb-3">
                  <label htmlFor="fax" className="form-label">{t('Fax')}</label>
                  <input id="fax" type="text" className="form-control" {...register('fax')} />
                </div>
              </div>
            </div>

            {profile.contactRelationships.length > 0 && (
              <>
                <h4 className="mt-4">{t('Contact relationships')}</h4>
                <table className="table table-striped">
                  <thead>
                    <tr>
                      <th>{t('Entity')}</th>
                      <th>{t('Role')}</th>
                      <th>{t('Notify')}</th>
                      <th>{t('Frequency')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {profile.contactRelationships.map((cr, i) => (
                      <tr key={cr.entityUid}>
                        <td>
                          <Link to={`/public/show/${cr.entityUid}`}>
                            {cr.entityName} ({cr.entityUid})
                          </Link>
                        </td>
                        <td>
                          <input
                            type="text"
                            className="form-control form-control-sm"
                            {...register(`contactRelationships.${i}.role`)}
                          />
                          <input type="hidden" {...register(`contactRelationships.${i}.entityUid`)} />
                        </td>
                        <td>
                          <div className="form-check">
                            <input
                              type="checkbox"
                              className="form-check-input"
                              {...register(`contactRelationships.${i}.notify`)}
                            />
                          </div>
                        </td>
                        <td>
                          {['each', 'daily', 'weekly'].map((freq) => (
                            <div className="form-check form-check-inline" key={freq}>
                              <input
                                type="radio"
                                className="form-check-input"
                                value={freq}
                                {...register(`contactRelationships.${i}.notifyFrequency`)}
                              />
                              <label className="form-check-label">{t(freq)}</label>
                            </div>
                          ))}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}

            <div className="d-flex gap-2 mt-3">
              <button type="submit" className="btn btn-primary" disabled={mutation.isPending}>
                {mutation.isPending ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-1" role="status" />
                    {t('Updating...')}
                  </>
                ) : (
                  <>
                    <i className="fa fa-save me-1" />
                    {t('Update')}
                  </>
                )}
              </button>
              <button
                type="button"
                className="btn btn-outline-dark"
                onClick={() => window.history.back()}
              >
                {t('Cancel')}
              </button>
            </div>
          </form>
        )}
      </div>
    </ProtectedRoute>
  );
}
