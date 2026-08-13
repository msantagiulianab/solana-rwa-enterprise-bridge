import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { AuditLog, AuditLogStatus } from '../../shared/models/audit-log.model';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.css'],
})
export class AuditLogComponent implements OnInit {
  logs: AuditLog[] = [];
  filteredLogs: AuditLog[] = [];
  loading = true;
  error: string | null = null;

  /* Search / filter bindings */
  searchAction = '';
  filterStatus: AuditLogStatus | '' = '';

  constructor(private readonly api: BackendApiService) {}

  ngOnInit(): void {
    this.api.getAuditLogs().subscribe({
      next: (logs) => {
        this.logs = logs.sort(
          (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load audit logs. Ensure the backend is running.';
        this.loading = false;
      },
    });
  }

  applyFilters(): void {
    let result = [...this.logs];

    if (this.searchAction.trim()) {
      const query = this.searchAction.trim().toLowerCase();
      result = result.filter(
        (log) =>
          log.action.toLowerCase().includes(query) ||
          (log.reason && log.reason.toLowerCase().includes(query)) ||
          log.walletAddress.toLowerCase().includes(query)
      );
    }

    if (this.filterStatus) {
      result = result.filter((log) => log.status === this.filterStatus);
    }

    this.filteredLogs = result;
  }

  clearFilters(): void {
    this.searchAction = '';
    this.filterStatus = '';
    this.applyFilters();
  }

  statusBadge(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'bg-green-400/10 text-green-400 border-green-400/30';
      case 'BLOCKED':
        return 'bg-yellow-400/10 text-yellow-400 border-yellow-400/30';
      default:
        return 'bg-gray-400/10 text-gray-400 border-gray-400/30';
    }
  }

  actionLabel(action: string): string {
    switch (action) {
      case 'TOKENIZE_ASSET':
        return 'Tokenize Asset';
      case 'KYC_VERIFIED':
        return 'KYC Verified';
      case 'CHECK_ELIGIBILITY':
        return 'Check Eligibility';
      case 'INVESTOR_REGISTERED':
        return 'Investor Registered';
      case 'MINT_ATTEMPT':
        return 'Mint Attempt';
      case 'RPC_CALL':
        return 'RPC Call';
      default:
        return action
          .split('_')
          .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
          .join(' ');
    }
  }

  actionBadge(action: string): string {
    switch (action) {
      case 'TOKENIZE_ASSET':
        return 'bg-solana-purple/10 text-solana-purple border-solana-purple/30';
      case 'KYC_VERIFIED':
        return 'bg-blue-400/10 text-blue-400 border-blue-400/30';
      case 'CHECK_ELIGIBILITY':
        return 'bg-cyan-400/10 text-cyan-400 border-cyan-400/30';
      default:
        return 'bg-gray-400/10 text-gray-300 border-gray-400/30';
    }
  }
}