export interface ImportResponse {
  type: string;
  jobName: string | null;
  executionId: number | null;
  storedAs: string | null;
  message: string;
}
