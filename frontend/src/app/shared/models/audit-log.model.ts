export interface AuditLog {
  id: string;
  walletAddress: string;
  action: string;
  status: AuditLogStatus;
  reason: string | null;
  timestamp: string;
}

export type AuditLogStatus = 'SUCCESS' | 'REJECTED' | 'BLOCKED_BY_COMPLIANCE' | 'RPC_ERROR';