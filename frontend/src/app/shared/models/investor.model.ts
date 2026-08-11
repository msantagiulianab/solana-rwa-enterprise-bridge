export interface Investor {
  id: string;
  fullName: string;
  email: string;
  walletAddress: string;
  kycStatus: KycStatus;
  country: string | null;
  createdAt: string;
  updatedAt: string;
}

export type KycStatus = 'PENDING' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED';

export interface RegisterInvestorRequest {
  fullName: string;
  email: string;
  solanaAddress: string;
}

export interface UpdateInvestorStatusRequest {
  kycStatus: KycStatus;
}