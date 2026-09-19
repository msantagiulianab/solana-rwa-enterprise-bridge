import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'tokens',
    title: 'Asset Tokens',
    loadComponent: () =>
      import('./features/asset-tokenization/asset-tokenization.component').then(
        (m) => m.AssetTokenizationComponent
      ),
  },
  {
    path: 'investors',
    title: 'Investor KYC',
    loadComponent: () =>
      import('./features/investor-kyc/investor-kyc.component').then(
        (m) => m.InvestorKycComponent
      ),
  },
  {
    path: 'audit-logs',
    title: 'Audit Logs',
    loadComponent: () =>
      import('./features/audit-log/audit-log.component').then(
        (m) => m.AuditLogComponent
      ),
  },
  { path: '', redirectTo: '/tokens', pathMatch: 'full' },
  { path: '**', redirectTo: '/tokens' },
];