import { useCallback, useRef, useState } from 'react';

interface StreamingState<T> {
  isStreaming: boolean;
  data: T[];
  error: Error | null;
}

export function useStreaming<T>() {
  const [state, setState] = useState<StreamingState<T>>({
    isStreaming: false,
    data: [],
    error: null,
  });
  
  const abortControllerRef = useRef<AbortController | null>(null);

  const startStreaming = useCallback(async (
    streamFn: (onData: (data: T) => void, onError?: (error: Error) => void) => Promise<void>,
    onData?: (data: T) => void
  ) => {
    setState(prev => ({ ...prev, isStreaming: true, error: null }));

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      await streamFn(
        (data) => {
          setState(prev => ({ ...prev, data: [...prev.data, data] }));
          onData?.(data);
        },
        (error) => {
          setState(prev => ({ ...prev, error, isStreaming: false }));
        }
      );
    } catch (error) {
      if (error instanceof Error && error.name !== 'AbortError') {
        setState(prev => ({ ...prev, error, isStreaming: false }));
      }
    } finally {
      setState(prev => ({ ...prev, isStreaming: false }));
      abortControllerRef.current = null;
    }
  }, []);

  const stopStreaming = useCallback(() => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
  }, []);

  const reset = useCallback(() => {
    stopStreaming();
    setState({ isStreaming: false, data: [], error: null });
  }, [stopStreaming]);

  return {
    ...state,
    startStreaming,
    stopStreaming,
    reset,
  };
}