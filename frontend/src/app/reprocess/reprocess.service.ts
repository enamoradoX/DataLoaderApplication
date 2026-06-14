import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, of, throwError } from 'rxjs';
import { EmployeeRecordData, ReprocessResult } from './reprocess.model';

@Injectable({ providedIn: 'root' })
export class ReprocessService {
  private http = inject(HttpClient);

  reprocess(data: EmployeeRecordData): Observable<ReprocessResult> {
    return this.http.post<ReprocessResult>('/api/reprocess', data).pipe(
      catchError((err: HttpErrorResponse) => {
        const body = err.error;
        if (body && typeof body === 'object' && 'success' in body) {
          return of(body as ReprocessResult); // 422 validation result = normal outcome
        }
        return throwError(() => err);          // genuine failure
      })
    );
  }
}
