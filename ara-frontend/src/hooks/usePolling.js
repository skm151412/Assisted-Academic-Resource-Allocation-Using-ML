import { useEffect, useRef } from "react";

export default function usePolling(callback, intervalMs = 30000, options = {}) {
  const { immediate = true } = options;
  const savedCallback = useRef(callback);
  const inFlightRef = useRef(false);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    let timerId;
    let cancelled = false;

    const run = async () => {
      if (inFlightRef.current || cancelled) {
        return;
      }

      inFlightRef.current = true;
      try {
        await savedCallback.current();
      } finally {
        inFlightRef.current = false;
      }
    };

    if (immediate) {
      run();
    }

    timerId = setInterval(() => {
      run();
    }, intervalMs);

    return () => {
      cancelled = true;
      clearInterval(timerId);
    };
  }, [intervalMs, immediate]);
}
