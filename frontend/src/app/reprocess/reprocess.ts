import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ReprocessService } from './reprocess.service';
import { ReprocessResult } from './reprocess.model';

@Component({
  selector: 'app-reprocess',
  imports: [ReactiveFormsModule],
  templateUrl: './reprocess.html',
  styleUrl: './reprocess.css',
})
export class Reprocess {
  private fb = inject(FormBuilder);
  private service = inject(ReprocessService);
  private route = inject(ActivatedRoute);

  protected readonly submitting = signal(false);
  protected readonly result = signal<ReprocessResult | null>(null);
  protected readonly requestError = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    id: '', name: '', email: '', department: '', role: '', salary: '',
  });

  constructor() {
    const qp = this.route.snapshot.queryParamMap;
    this.form.patchValue({
      id: qp.get('id') ?? '', name: qp.get('name') ?? '', email: qp.get('email') ?? '',
      department: qp.get('department') ?? '', role: qp.get('role') ?? '', salary: qp.get('salary') ?? '',
    });
  }

  submit() {
    this.submitting.set(true);
    this.result.set(null);
    this.requestError.set(null);
    this.service.reprocess(this.form.getRawValue()).subscribe({
      next: (res) => { this.result.set(res); this.submitting.set(false); },
      error: (err) => { this.requestError.set(err?.message ?? 'Request failed'); this.submitting.set(false); },
    });
  }
}
