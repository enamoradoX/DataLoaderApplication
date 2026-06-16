import { Routes } from '@angular/router';
import { Reprocess } from './reprocess/reprocess';
import { Skips } from './skips/skips';
import { Import } from './import/import';

export const routes: Routes = [
  { path: 'import', component: Import },
  { path: 'reprocess', component: Reprocess },
  { path: 'skips/:loadId', component: Skips },
];
