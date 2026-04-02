import { checkLoginState, UserContext, type UserInfo } from '@ala/common-ui';
import { useEffect, useRef, useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { setAccessToken } from './api/client';
import { router } from './routes';

export function App() {
  const [userInfo, setUserInfo] = useState<UserInfo | null>(null);
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    checkLoginState(setUserInfo, refreshTimer, import.meta.env.VITE_APP_API_URL);

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkLoginState(setUserInfo, refreshTimer, import.meta.env.VITE_APP_API_URL);
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  useEffect(() => {
    setAccessToken(userInfo?.accessToken);
  }, [userInfo]);

  return (
    <UserContext.Provider value={{ userInfo, setUserInfo }}>
      <RouterProvider router={router} />
    </UserContext.Provider>
  );
}
