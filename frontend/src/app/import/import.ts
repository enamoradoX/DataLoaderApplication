import { Component, inject, signal } from '@angular/core';
import { ImportService } from './import.service';
import { ImportResponse } from './import.model';

type ImportType = 'departments' | 'employees';

@Component({
  selector: 'app-import',
  imports: [],
  templateUrl: './import.html',
  styleUrl: './import.css',
})
export class Import {
  private service = inject(ImportService);

  protected readonly type = signal<ImportType>('departments');
  protected readonly file = signal<File | null>(null);
  protected readonly dragging = signal(false);
  protected readonly uploading = signal(false);
  protected readonly result = signal<ImportResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<string | null>(null);

  protected setType(t: ImportType): void {
    this.type.set(t);
  }

  protected onDragOver(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(false);
  }

  protected onDrop(e: DragEvent): void {
    e.preventDefault();
    this.dragging.set(false);
    const f = e.dataTransfer?.files?.[0];
    if (f) {
      this.file.set(f);
    }
  }

  protected onPick(e: Event): void {
    const input = e.target as HTMLInputElement;
    if (input.files?.length) {
      this.file.set(input.files[0]);
    }
  }

  protected upload(): void {
    const f = this.file();
    if (!f) {
      return;
    }
    this.uploading.set(true);
    this.result.set(null);
    this.error.set(null);
    this.summary.set(null);

    this.service.upload(this.type(), f).subscribe({
      next: (res) => {
        this.result.set(res);
        this.uploading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? err?.message ?? 'Upload failed');
        this.uploading.set(false);
      },
    });
  }

  protected checkStatus(): void {
    const id = this.result()?.executionId;
    if (id == null) {
      return;
    }
    this.service.getSummary(id).subscribe({
      next: (text) => this.summary.set(text),
      error: (err) => this.summary.set('Failed to fetch status: ' + (err?.message ?? 'unknown')),
    });
  }
}
