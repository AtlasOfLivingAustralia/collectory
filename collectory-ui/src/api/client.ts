import axios from 'axios';

let _accessToken: string | undefined;

export function setAccessToken(token: string | undefined) {
  _accessToken = token;
}

const apiClient = axios.create({
  baseURL: `${import.meta.env.VITE_APP_API_URL ?? ''}/ws`,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

// JWT interceptor: attach token from session auth
apiClient.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

// Response interceptor: handle common errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      console.warn('Unauthorized - token may have expired');
    }
    return Promise.reject(error);
  },
);

export default apiClient;
