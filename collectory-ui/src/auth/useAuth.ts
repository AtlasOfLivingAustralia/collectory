import { handleLogin, handleLogout, useUser } from '@ala/common-ui';

export function useAuth() {
  const { userInfo } = useUser();

  return {
    isAuthenticated: userInfo?.authenticated ?? false,
    isLoading: userInfo === null,
    user: userInfo,
    roles: userInfo?.roles ?? [],
    isAdmin: userInfo?.roles?.includes('ROLE_ADMIN') ?? false,
    isEditor: userInfo?.roles?.some((r) => r === 'ROLE_EDITOR' || r === 'ROLE_ADMIN') ?? false,
    accessToken: userInfo?.accessToken,
    email: userInfo?.email,
    userId: userInfo?.userId,
    login: () => handleLogin(import.meta.env.VITE_APP_API_URL),
    logout: () => handleLogout(import.meta.env.VITE_APP_API_URL, import.meta.env.VITE_APP_BASE_URL),
  };
}
