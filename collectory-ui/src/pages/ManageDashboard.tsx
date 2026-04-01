import { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../auth/useAuth';
import { getConfig } from '../api/endpoints/config';

export default function ManageDashboard() {
  const auth = useAuth();
  const navigate = useNavigate();

  const { data: config } = useQuery({
    queryKey: ['appConfig'],
    queryFn: getConfig,
  });

  useEffect(() => {
    if (auth.isAuthenticated) {
      navigate('/manage/list', { replace: true });
    }
  }, [auth.isAuthenticated, navigate]);

  if (auth.isLoading) {
    return (
      <div className="d-flex justify-content-center p-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (auth.isAuthenticated) return <></>; // redirecting

  const supportEmail = config?.orgSupportEmail ?? 'support@ala.org.au';
  // Self-register: derive from userDetailsUrl if available, otherwise fall back to the
  // standard ALA registration page (the Grails app hard-coded the CAS emmet URL).
  const registerUrl = config?.userDetailsUrl
    ? config.userDetailsUrl.replace(/\/$/, '') + '/registration/createAccount'
    : 'https://auth.ala.org.au/userdetails/registration/createAccount';

  return (
    <div className="container mt-4">
      <div className="d-flex justify-content-between align-items-start mb-3">
        <h1>ALA Metadata Management</h1>
        <Link to="/" className="mainLink">View public site</Link>
      </div>

      <p>Metadata for collections, institutions and datasets can be managed here.</p>

      {/* Login section */}
      <div style={{ width: '400px' }}>
        <h2 className="mt-4">Please log in</h2>
        <button className="btn btn-primary" onClick={() => auth.login()}>
          <i className="fa fa-sign-in me-1" />
          &nbsp;Log in&nbsp;
        </button>
        <p className="mt-2">You must log in to manage metadata</p>
      </div>

      {/* About access accounts */}
      <div className="card card-body mt-4">
        <h3>About access accounts</h3>

        <h4>What do I need to edit my metadata?</h4>
        <p>You will need:</p>
        <ol>
          <li>a standard Atlas login</li>
          <li>the &lsquo;Collections Editor&rsquo; role</li>
          <li>to be listed as a contact with administrator rights for the collection, institution or dataset you want to edit</li>
        </ol>

        <h4>I don&rsquo;t have an Atlas account!</h4>
        <p>
          You can register{' '}
          <a href={registerUrl} target="_blank" rel="noopener noreferrer">here</a>.
        </p>
        <p>
          If you are already listed as a contact for the entity you want to edit, make sure you
          use the same email address as that contact.
        </p>

        <h4>How do I get the editor role?</h4>
        <p>
          Send an email to{' '}
          <a href={`mailto:${supportEmail}`}>support</a>
          {' '}and request the ROLE_EDITOR role.
        </p>

        <h4>What if I am not listed as a contact for the entity I want to edit?</h4>
        <p>
          You can be added as a contact by another user who has edit rights for the entity.
          Or you can send an email to{' '}
          <a href={`mailto:${supportEmail}`}>support</a>
          {' '}and ask to be added. You can choose whether your name and contact details
          should be displayed on the public page for the entity.
        </p>
      </div>
    </div>
  );
}
