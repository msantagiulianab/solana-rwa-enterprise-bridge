export interface AssetToken {
  id: string;
  assetName: string;
  valuationUsd: number;
  mintAddress: string | null;
  complianceStatus: AssetTokenComplianceStatus;
  createdAt: string;
  updatedAt: string;
}

export type AssetTokenComplianceStatus =
  | 'PENDING_KYC_APPROVAL'
  | 'KYC_APPROVED'
  | 'MINTED'
  | 'FROZEN'
  | 'BURNED'
  | 'REJECTED';

export interface CreateAssetTokenRequest {
  assetName: string;
  valuationUsd: number;
  issuerWalletAddress: string;
  idempotencyKey: string;
}
