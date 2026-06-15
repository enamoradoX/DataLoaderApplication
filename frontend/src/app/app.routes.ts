import { Routes } from '@angular/router';
import { Reprocess } from './reprocess/reprocess';
import { Skips } from './skips/skips';

export const routes: Routes = [
  { path: 'reprocess', component: Reprocess },
  { path: 'skips/:loadId', component: Skips },
];
