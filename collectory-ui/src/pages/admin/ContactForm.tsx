import { useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { getContact } from '../../api/endpoints/contacts';
import apiClient from '../../api/client';
import Breadcrumb from '../../components/public/Breadcrumb';

interface ContactFormData {
  title: string;
  firstName: string;
  lastName: string;
  phone: string;
  mobile: string;
  email: string;
  fax: string;
  organizationName: string;
  positionName: string;
  userId: string;
  notes: string;
  publish: boolean;
}

export default function ContactForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const isEdit = !!id;

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<ContactFormData>({
    defaultValues: { publish: true },
  });

  const { data: contact } = useQuery({
    queryKey: ['contact', id],
    queryFn: () => getContact(Number(id)),
    enabled: isEdit,
  });

  useEffect(() => {
    if (contact) {
      reset({
        title: contact.title ?? '',
        firstName: contact.firstName ?? '',
        lastName: contact.lastName ?? '',
        phone: contact.phone ?? '',
        mobile: contact.mobile ?? '',
        email: contact.email ?? '',
        fax: contact.fax ?? '',
        organizationName: contact.organizationName ?? '',
        positionName: contact.positionName ?? '',
        userId: contact.userId ?? '',
        notes: contact.notes ?? '',
        publish: contact.publish ?? true,
      });
    }
  }, [contact, reset]);

  const saveMutation = useMutation({
    mutationFn: async (data: ContactFormData) => {
      if (isEdit) {
        const res = await apiClient.put(`/contact/${id}`, data);
        return res.data;
      } else {
        const res = await apiClient.post('/contact', data);
        return res.data;
      }
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['contacts'] });
      queryClient.invalidateQueries({ queryKey: ['contact', id] });
      const returnTo = searchParams.get('returnTo');
      navigate(returnTo || `/contact/show/${saved.id ?? id}`);
    },
  });

  const onSubmit = (data: ContactFormData) => {
    saveMutation.mutate(data);
  };

  return (
    <div className="container mt-4">
      <Breadcrumb
        parent={{ url: '/manage/list', label: 'Metadata management' }}
        extra={[{ url: '/contact/list', label: 'Contacts' }]}
        current={isEdit ? t('contact.edit', 'Edit Contact') : t('contact.create', 'New Contact')}
      />
      <h1>{isEdit ? t('contact.edit', 'Edit Contact') : t('contact.create', 'New Contact')}</h1>

      <form onSubmit={handleSubmit(onSubmit)}>
        <div className="card mb-3">
          <div className="card-body">
            <div className="row mb-3">
              <div className="col-md-2">
                <label className="form-label">{t('contact.title', 'Title')}</label>
                <select className="form-select" {...register('title')}>
                  {['', 'Mr', 'Mrs', 'Ms', 'Dr', 'Prof', 'Assoc Prof', 'Hon'].map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              </div>
              <div className="col-md-5">
                <label className="form-label">{t('contact.firstName', 'First name')}</label>
                <input type="text" className="form-control" {...register('firstName')} />
              </div>
              <div className="col-md-5">
                <label className="form-label">{t('contact.lastName', 'Last name')}</label>
                <input type="text" className="form-control" {...register('lastName')} />
              </div>
            </div>

            <div className="row mb-3">
              <div className="col-md-6">
                <label className="form-label">{t('contact.email', 'Email')}</label>
                <input type="email" className="form-control" {...register('email')} />
                {errors.email && <div className="text-danger small">{errors.email.message}</div>}
              </div>
              <div className="col-md-6">
                <label className="form-label">{t('contact.userId', 'User ID')}</label>
                <input type="text" className="form-control" {...register('userId')} />
              </div>
            </div>

            <div className="row mb-3">
              <div className="col-md-4">
                <label className="form-label">{t('contact.phone', 'Phone')}</label>
                <input type="text" className="form-control" {...register('phone')} />
              </div>
              <div className="col-md-4">
                <label className="form-label">{t('contact.mobile', 'Mobile')}</label>
                <input type="text" className="form-control" {...register('mobile')} />
              </div>
              <div className="col-md-4">
                <label className="form-label">{t('contact.fax', 'Fax')}</label>
                <input type="text" className="form-control" {...register('fax')} />
              </div>
            </div>

            <div className="row mb-3">
              <div className="col-md-6">
                <label className="form-label">{t('contact.organizationName', 'Organisation')}</label>
                <input type="text" className="form-control" {...register('organizationName')} />
              </div>
              <div className="col-md-6">
                <label className="form-label">{t('contact.positionName', 'Position')}</label>
                <input type="text" className="form-control" {...register('positionName')} />
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label">{t('contact.notes', 'Notes')}</label>
              <textarea className="form-control" rows={3} {...register('notes')} />
            </div>

            <div className="form-check mb-3">
              <input type="checkbox" className="form-check-input" id="publish" {...register('publish')} />
              <label className="form-check-label" htmlFor="publish">
                {t('contact.publish', 'Publish contact details')}
              </label>
            </div>
          </div>
        </div>

        {saveMutation.isError && (
          <div className="alert alert-danger">
            {t('common.saveError', 'An error occurred while saving.')}
          </div>
        )}

        <div className="d-flex gap-2">
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            <i className="fa fa-save me-1" />
            {t('common.save', 'Save')}
          </button>
          <button type="button" className="btn btn-outline-secondary" onClick={() => navigate(-1)}>
            {t('common.cancel', 'Cancel')}
          </button>
        </div>
      </form>
    </div>
  );
}
