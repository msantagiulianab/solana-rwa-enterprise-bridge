import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { AuditLog } from '../../shared/models/audit-log.model';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit-log.component.html',
  styleUrls: ['./audit-log.component.css'],
})
export class AuditLogComponent implements OnInit {
  logs: AuditLog[] = [];
  loading = true;
  error: string | null = null;

  constructor(private readonly api: BackendApiService) {}

  ngOnInit(): void {
    this.api.getAuditLogs().subscribe({
      next: (logs) => {
        this.logs = logs.sort(
          (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load audit logs. Ensure the backend is running.';
        this.loading = false;
      },
    });
  }

  statusBadge(status: string): string {
    switch (status) {
      case 'SUCCESS':
        return 'bg-green-400/10 text-green-400 border-green-400/30';
      case 'BLOCKED_BY_COMPLIANCE':
        return 'bg-yellow-400/10 text-yellow-400 border-yellow-400/30';
      case 'REJECTED':
        return 'bg-red-400/10 text-red-400 border-red-400/30';
      case 'RPC_ERROR':
        return 'bg-orange-400/10 text-orange-400 border-orange-400/30';
      default:
        return 'bg-gray-400/10 text-gray-400 border-gray-400/30';
    }
  }
}