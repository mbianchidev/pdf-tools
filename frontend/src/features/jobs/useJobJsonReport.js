import { useEffect, useState } from 'react';
import { jobService } from '../../services/jobService';

export const useJobJsonReport = (
  job,
  validateReport,
  reportLabel,
) => {
  const [state, setState] = useState({
    outputId: null,
    report: null,
    error: '',
  });
  const output = job?.status === 'COMPLETED'
    ? job.outputs?.find((candidate) => (
      candidate.mediaType === 'application/json'
    ))
    : null;

  useEffect(() => {
    if (!output) {
      return undefined;
    }
    let active = true;
    const load = async () => {
      try {
        const blob = await jobService.download(output);
        const report = JSON.parse(await blob.text());
        validateReport(report);
        if (active) {
          setState({ outputId: output.id, report, error: '' });
        }
      } catch (error) {
        console.error(`${reportLabel} report error:`, error);
        if (active) {
          setState({
            outputId: output.id,
            report: null,
            error: error.message || `${reportLabel} report could not be read.`,
          });
        }
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [output, reportLabel, validateReport]);

  return {
    output,
    loading: Boolean(output && state.outputId !== output.id),
    report: state.outputId === output?.id ? state.report : null,
    error: state.outputId === output?.id ? state.error : '',
  };
};
