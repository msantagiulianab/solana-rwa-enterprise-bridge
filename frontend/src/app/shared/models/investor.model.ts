export interface Investor {
  id: number;
  fullName: string;
  email: string;
  solanaAddress: string;
  kycStatus: KycStatus;
  createdAt: string;
  updatedAt: string;
}

export type KycStatus = 'PENDING' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED';