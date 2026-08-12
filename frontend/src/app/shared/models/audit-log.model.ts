export interface AuditLog {
  id: string;
  walletAddress: string;
  action: string;
  status: AuditLogStatus;
  reason: string | null;
  timestamp: string;
}

export type AuditLogStatus = 'APPROVED' | 'BLOCKED';
