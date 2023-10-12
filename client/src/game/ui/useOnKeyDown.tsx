import {useCallback, useEffect} from "react";

export function useOnKeyDown(key: String, handleClose: () => void) {
  const handleEscKey = useCallback((event: any) => {
    if (event.key === key) {
      handleClose()
      event.stopPropagation()
    }
  }, [handleClose]);

  useEffect(() => {
    document.addEventListener('keydown', handleEscKey, false);

    return () => {
      document.removeEventListener('keydown', handleEscKey, false);
    };
  }, [handleEscKey]);
}