export interface AuditLog {
  id: string;
  walletAddress: string;
  action: string;
  status: AuditLogStatus;
  reason: string | null;
  timestamp: string;
}

export type AuditLogStatus = 'APPROVED' | 'BLOCKED';

export interface TransferHookAuditLog {
  id: string;
  mintAddress: string;
  sourceWallet: string;
  destinationWallet: string;
  amount: number;
  complianceStatus: 'CLEARED' | 'BLOCKED';
  reasonCode?: string | null;
  transactionSignature?: string | null;
  createdAt: string;
}
