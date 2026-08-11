import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BackendApiService } from '../../shared/services/backend-api.service';
import { AssetToken } from '../../shared/models/asset-token.model';

@Component({
  selector: 'app-asset-tokenization',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './asset-tokenization.component.html',
  styleUrls: ['./asset-tokenization.component.css'],
})
export class AssetTokenizationComponent implements OnInit {
  tokens: AssetToken[] = [];
  loading = true;
  error: string | null = null;

  constructor(private readonly api: BackendApiService) {}

  ngOnInit(): void {
    this.api.getAssetTokens().subscribe({
      next: (tokens) => {
        this.tokens = tokens;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load asset tokens. Ensure the backend is running.';
        this.loading = false;
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