import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BatchReprocessItem, BatchReprocessResult, SkippedRecordView } from './skips.model';

@Injectable({ providedIn: 'root' })
export class SkipsService {
  private http = inject(HttpClient);

  getSkips(loadId: string): Observable<SkippedRecordView[]> {
    return this.http.get<SkippedRecordView[]>(`/api/skips/${encodeURIComponent(loadId)}`);
  }

  reprocessBatch(loadId: string, items: BatchReprocessItem[]): Observable<BatchReprocessResult[]> {
    return this.http.post<BatchReprocessResult[]>(
      `/api/skips/${encodeURIComponent(loadId)}/reprocess`,
      items,
    );
  }
}
