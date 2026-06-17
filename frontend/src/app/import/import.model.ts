export interface ImportResponse {
  type: string;
  jobName: string | null;
  executionId: number | null;
  storedAs: string | null;
  message: string;
}

export interface JobSummary {
  executionId: number;
  status: string;
  rowsRead: number;
  rowsWritten: number;
  rowsSkipped: number;
  message: string;
}
