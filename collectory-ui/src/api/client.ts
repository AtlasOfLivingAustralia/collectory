import axios from 'axios';

const apiClient = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL ?? ''}/ws`,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

// JWT interceptor: attach token from OIDC session
apiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('oidc_access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor: handle common errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Could trigger re-auth flow
      console.warn('Unauthorized - token may have expired');
    }
    return Promise.reject(error);
  },
);

export default apiClient;
