import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SkipsService } from './skips.service';
import { BatchReprocessResult, SkippedRecordView } from './skips.model';

@Component({
  selector: 'app-skips',
  imports: [ReactiveFormsModule],
  templateUrl: './skips.html',
  styleUrl: './skips.css',
})
export class Skips {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private service = inject(SkipsService);

  protected readonly loadId: string;
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly views = signal<SkippedRecordView[]>([]);
  protected readonly results = signal<Record<number, BatchReprocessResult>>({});

  protected readonly formGroup = this.fb.group({
    rows: this.fb.array([] as FormGroup[]),
  });

  get rows(): FormArray {
    return this.formGroup.get('rows') as FormArray;
  }

  constructor() {
    this.loadId = this.route.snapshot.paramMap.get('loadId') ?? '';
    this.fetch();
  }

  protected refresh(): void {
    this.fetch();
  }

  protected resultFor(skipId: number): BatchReprocessResult | undefined {
    return this.results()[skipId];
  }

  protected submit(): void {
    this.submitting.set(true);
    const items = this.rows.controls.map((group) => ({
      skipId: group.value.skipId as number,
      data: {
        id: group.value.id ?? '',
        name: group.value.name ?? '',
        email: group.value.email ?? '',
        department: group.value.department ?? '',
        role: group.value.role ?? '',
        salary: group.value.salary ?? '',
      },
    }));

    this.service.reprocessBatch(this.loadId, items).subscribe({
      next: (res) => {
        const map: Record<number, BatchReprocessResult> = {};
        for (const r of res) {
          map[r.skipId] = r;
        }
        this.results.set(map);
        this.submitting.set(false);
      },
      error: (err) => {
        this.loadError.set(err?.message ?? 'Reprocess failed');
        this.submitting.set(false);
      },
    });
  }

  private fetch(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.results.set({});
    this.service.getSkips(this.loadId).subscribe({
      next: (views) => {
        this.views.set(views);
        this.rows.clear();
        for (const v of views) {
          this.rows.push(
            this.fb.group({
              skipId: [v.skipId],
              id: [v.data?.id ?? ''],
              name: [v.data?.name ?? ''],
              email: [v.data?.email ?? ''],
              department: [v.data?.department ?? ''],
              role: [v.data?.role ?? ''],
              salary: [v.data?.salary ?? ''],
            }),
          );
        }
        this.loading.set(false);
      },
      error: (err) => {
        this.loadError.set(err?.message ?? 'Failed to load skips');
        this.loading.set(false);
      },
    });
  }
}
