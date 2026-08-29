import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { SolanaWalletService } from '../../shared/services/solana-wallet.service';
import { AssetToken, CreateAssetTokenRequest } from '../../shared/models/asset-token.model';

const BASE58_PATTERN = /^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}$/;

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
  issuerWalletAddress: string | null = null;
  submitting = false;
  submitError: string | null = null;
  submitSuccess: string | null = null;

  constructor(
    private readonly api: BackendApiService,
    private readonly walletService: SolanaWalletService
  ) {}

  ngOnInit(): void {
    this.loadTokens();
    this.walletService.connectedPublicKey$.subscribe((key) => {
      this.issuerWalletAddress = key;
    });
  }

  /**
   * Returns true when the mint address is a syntactically valid base58 Solana
   * public key (32-44 chars, canonical alphabet) rather than a pending/absent
   * placeholder value.
   */
  isValidMintAddress(mintAddress: string | null | undefined): boolean {
    return typeof mintAddress === 'string' && BASE58_PATTERN.test(mintAddress);
  }

  /**
   * Builds the Solana Devnet explorer link for a mint address.
   */
  explorerUrl(mintAddress: string): string {
    return `https://explorer.solana.com/address/${mintAddress}?cluster=devnet`;
  }

  /**
   * Truncates the mint address for compact display.
   */
  truncateMintAddress(mintAddress: string): string {
    if (mintAddress.length <= 12) {
      return mintAddress;
    }
    return `${mintAddress.slice(0, 6)}...${mintAddress.slice(-6)}`;
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
    if (!this.issuerWalletAddress) {
      this.submitError = 'Please connect your wallet to tokenize an asset.';
      return;
    }

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
      issuerWalletAddress: this.issuerWalletAddress,
      idempotencyKey: crypto.randomUUID(),
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