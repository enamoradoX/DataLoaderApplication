import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ImportResponse, JobSummary } from './import.model';

@Injectable({ providedIn: 'root' })
export class ImportService {
  private http = inject(HttpClient);

  upload(type: string, file: File): Observable<ImportResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResponse>(`/api/imports/${encodeURIComponent(type)}`, form);
  }

  getSummary(executionId: number): Observable<JobSummary> {
    return this.http.get<JobSummary>(`/api/batch/summary/${executionId}`);
  }
}
