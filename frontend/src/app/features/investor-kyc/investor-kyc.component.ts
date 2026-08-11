import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { Investor, KycStatus, RegisterInvestorRequest, UpdateInvestorStatusRequest } from '../../shared/models/investor.model';

@Component({
  selector: 'app-investor-kyc',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './investor-kyc.component.html',
  styleUrls: ['./investor-kyc.component.css'],
})
export class InvestorKycComponent implements OnInit {
  investors: Investor[] = [];
  loading = true;
  error: string | null = null;

  /* Registration form bindings */
  fullName = '';
  email = '';
  solanaAddress = '';
  submitting = false;
  submitError: string | null = null;
  submitSuccess: string | null = null;

  /* Status update tracking */
  updatingInvestorId: string | null = null;
  statusUpdateError: string | null = null;

  constructor(private readonly api: BackendApiService) {}

  ngOnInit(): void {
    this.loadInvestors();
  }

  loadInvestors(): void {
    this.loading = true;
    this.error = null;
    this.api.getInvestors().subscribe({
      next: (investors) => {
        this.investors = investors;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load investors. Ensure the backend is running.';
        this.loading = false;
      },
    });
  }

  registerInvestor(): void {
    if (!this.fullName.trim() || !this.email.trim() || !this.solanaAddress.trim()) {
      this.submitError = 'All fields are required.';
      return;
    }

    this.submitting = true;
    this.submitError = null;
    this.submitSuccess = null;

    const payload: RegisterInvestorRequest = {
      fullName: this.fullName.trim(),
      email: this.email.trim(),
      solanaAddress: this.solanaAddress.trim(),
    };

    this.api.registerInvestor(payload).subscribe({
      next: (investor) => {
        this.investors = [investor, ...this.investors];
        this.fullName = '';
        this.email = '';
        this.solanaAddress = '';
        this.submitting = false;
        this.submitSuccess = `Investor "${investor.fullName}" registered successfully.`;
      },
      error: (err) => {
        this.submitError = err?.error?.message || 'Registration failed. Check the backend logs.';
        this.submitting = false;
      },
    });
  }

  updateInvestorStatus(investor: Investor, newStatus: KycStatus): void {
    this.updatingInvestorId = investor.id;
    this.statusUpdateError = null;

    const payload: UpdateInvestorStatusRequest = { kycStatus: newStatus };

    this.api.updateInvestorStatus(investor.id, payload).subscribe({
      next: (updated) => {
        const index = this.investors.findIndex((i) => i.id === updated.id);
        if (index !== -1) {
          this.investors[index] = updated;
          this.investors = [...this.investors];
        }
        this.updatingInvestorId = null;
      },
      error: (err) => {
        this.statusUpdateError =
          err?.error?.message || `Failed to update investor status to ${newStatus}.`;
        this.updatingInvestorId = null;
      },
    });
  }

  kycStatusColor(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'text-green-400 bg-green-400/10';
      case 'IN_REVIEW':
        return 'text-blue-400 bg-blue-400/10';
      case 'PENDING':
        return 'text-yellow-400 bg-yellow-400/10';
      case 'REJECTED':
        return 'text-red-400 bg-red-400/10';
      default:
        return 'text-gray-400 bg-gray-400/10';
    }
  }
}