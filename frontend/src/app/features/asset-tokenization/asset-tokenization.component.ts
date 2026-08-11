import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { AssetToken, CreateAssetTokenRequest } from '../../shared/models/asset-token.model';

@Component({
  selector: 'app-asset-tokenization',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './asset-tokenization.component.html',
  styleUrls: ['./asset-tokenization.component.css'],
})
export class AssetTokenizationComponent implements OnInit {
  tokens: AssetToken[] = [];
  loading = true;
  error: string | null = null;

  /* Tokenization form bindings */
  showTokenizeModal = false;
  assetName = '';
  valuationUsd: number | null = null;
  submitting = false;
  submitError: string | null = null;
  submitSuccess: string | null = null;

  constructor(private readonly api: BackendApiService) {}

  ngOnInit(): void {
    this.loadTokens();
  }

  loadTokens(): void {
    this.loading = true;
    this.error = null;
    this.api.getAssetTokens().subscribe({
      next: (tokens) => {
        this.tokens = tokens;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load asset tokens. Ensure the backend is running.';
        this.loading = false;
      },
    });
  }

  openTokenizeModal(): void {
    this.showTokenizeModal = true;
    this.assetName = '';
    this.valuationUsd = null;
    this.submitError = null;
    this.submitSuccess = null;
  }

  closeTokenizeModal(): void {
    this.showTokenizeModal = false;
  }

  createAssetToken(): void {
    if (!this.assetName.trim() || this.valuationUsd === null || this.valuationUsd <= 0) {
      this.submitError = 'All fields are required and valuation must be greater than 0.';
      return;
    }

    this.submitting = true;
    this.submitError = null;
    this.submitSuccess = null;

    const payload: CreateAssetTokenRequest = {
      assetName: this.assetName.trim(),
      valuationUsd: this.valuationUsd,
    };

    this.api.createAssetToken(payload).subscribe({
      next: (token) => {
        this.tokens = [token, ...this.tokens];
        this.submitting = false;
        this.submitSuccess = `Asset "${token.assetName}" tokenized successfully.`;
        this.assetName = '';
        this.valuationUsd = null;
      },
      error: (err) => {
        this.submitError = err?.error?.message || 'Tokenization failed. Check the backend logs.';
        this.submitting = false;
      },
    });
  }

  statusColor(status: string): string {
    switch (status) {
      case 'MINTED':
        return 'text-green-400';
      case 'KYC_APPROVED':
        return 'text-blue-400';
      case 'PENDING_KYC_APPROVAL':
        return 'text-yellow-400';
      case 'FROZEN':
        return 'text-orange-400';
      case 'BURNED':
        return 'text-gray-400';
      case 'REJECTED':
        return 'text-red-400';
      default:
        return 'text-gray-300';
    }
  }
}