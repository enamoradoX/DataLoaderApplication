export interface EmployeeRecordData {
  id: string; name: string; email: string; role: string; salary: string;
}
export interface ReprocessResult {
  success: boolean;
  savedId: number | null;
  errors: string[];
}
