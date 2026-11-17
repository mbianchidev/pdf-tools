import { useMemo, useState } from 'react';

export const usePrintSettings = () => {
  const [pageSize, setPageSize] = useState('a4');
  const [orientation, setOrientation] = useState('portrait');
  const [printBackground, setPrintBackground] = useState(true);
  const [marginMm, setMarginMm] = useState('10');
  const parsedMargin = Number(marginMm);
  const validationError = !Number.isInteger(parsedMargin)
    || parsedMargin < 0
    || parsedMargin > 50
    ? 'Margin must be a whole number from 0 to 50 mm.'
    : null;
  const options = useMemo(() => ({
    pageSize,
    orientation,
    printBackground,
    marginMm: parsedMargin,
  }), [orientation, pageSize, parsedMargin, printBackground]);

  return {
    options,
    validationError,
    settings: {
      pageSize,
      setPageSize,
      orientation,
      setOrientation,
      printBackground,
      setPrintBackground,
      marginMm,
      setMarginMm,
    },
  };
};
