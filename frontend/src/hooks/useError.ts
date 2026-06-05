import { useState, useCallback } from 'react';
import type { ErrorSeverity } from '../components/common/ErrorCard';

export interface ErrorState {
  isVisible: boolean;
  severity: ErrorSeverity;
  title: string;
  description?: string;
  details?: string;
}

export interface UseErrorReturn {
  error: ErrorState;
  showError: (config: Omit<ErrorState, 'isVisible'>) => void;
  showErrorWithDetails: (title: string, description: string, details: string) => void;
  showWarning: (title: string, description?: string) => void;
  showInfo: (title: string, description?: string) => void;
  showSuccess: (title: string, description?: string) => void;
  hideError: () => void;
  clearError: () => void;
}

export function useError(initialState?: Partial<ErrorState>): UseErrorReturn {
  const [error, setError] = useState<ErrorState>({
    isVisible: false,
    severity: 'error',
    title: '',
    description: undefined,
    details: undefined,
    ...initialState,
  });

  const showError = useCallback((config: Omit<ErrorState, 'isVisible'>) => {
    setError({
      ...config,
      isVisible: true,
    });
  }, []);

  const showErrorWithDetails = useCallback((title: string, description: string, details: string) => {
    setError({
      isVisible: true,
      severity: 'error',
      title,
      description,
      details,
    });
  }, []);

  const showWarning = useCallback((title: string, description?: string) => {
    setError({
      isVisible: true,
      severity: 'warning',
      title,
      description,
      details: undefined,
    });
  }, []);

  const showInfo = useCallback((title: string, description?: string) => {
    setError({
      isVisible: true,
      severity: 'info',
      title,
      description,
      details: undefined,
    });
  }, []);

  const showSuccess = useCallback((title: string, description?: string) => {
    setError({
      isVisible: true,
      severity: 'success',
      title,
      description,
      details: undefined,
    });
  }, []);

  const hideError = useCallback(() => {
    setError(prev => ({
      ...prev,
      isVisible: false,
    }));
  }, []);

  const clearError = useCallback(() => {
    setError({
      isVisible: false,
      severity: 'error',
      title: '',
      description: undefined,
      details: undefined,
    });
  }, []);

  return {
    error,
    showError,
    showErrorWithDetails,
    showWarning,
    showInfo,
    showSuccess,
    hideError,
    clearError,
  };
}

export default useError;