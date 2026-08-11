export interface AuditLog {
  id: number;
  eventType: string;
  description: string;
  status: AuditLogStatus;
  timestamp: string;
  investorId?: number;
  assetTokenId?: number;
  onchainTxHash?: string;
}

export type AuditLogStatus = 'SUCCESS' | 'REJECTED' | 'BLOCKED_BY_COMPLIANCE' | 'RPC_ERROR';