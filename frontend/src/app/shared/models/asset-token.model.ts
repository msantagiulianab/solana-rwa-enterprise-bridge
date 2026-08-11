export interface AssetToken {
  id: number;
  mintAddress: string;
  assetName: string;
  symbol: string;
  totalSupply: number;
  decimals: number;
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