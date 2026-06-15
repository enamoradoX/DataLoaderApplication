import { EmployeeRecordData } from '../reprocess/reprocess.model';

export interface SkippedRecordView {
  skipId: number;
  recordId: string;
  phase: string;
  errorMessage: string;
  status: string;
  data: EmployeeRecordData | null;
}

export interface BatchReprocessItem {
  skipId: number;
  data: EmployeeRecordData;
}

export interface BatchReprocessResult {
  skipId: number;
  success: boolean;
  savedId: number | null;
  errors: string[];
}
