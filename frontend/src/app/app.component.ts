import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SolanaWalletService } from './shared/services/solana-wallet.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css'],
})
export class AppComponent implements OnInit {
  title = 'Solana RWA Enterprise Bridge';

  walletPublicKey: string | null = null;
  walletConnecting = false;
  walletError: string | null = null;
  isMenuOpen = false;

  constructor(private readonly walletService: SolanaWalletService) {}

  ngOnInit(): void {
    this.walletService.connectedPublicKey$.subscribe((key) => {
      this.walletPublicKey = key;
    });
  }

  isPhantomInstalled(): boolean {
    return this.walletService.isPhantomInstalled();
  }

  isMobileDevice(): boolean {
    return this.walletService.isMobileDevice();
  }

  buildPhantomDeepLink(): string {
    return this.walletService.buildPhantomDeepLink();
  }

  async connectWallet(): Promise<void> {
    this.walletConnecting = true;
    this.walletError = null;

    try {
      await this.walletService.connectWallet();
    } catch (err: unknown) {
      this.walletError = err instanceof Error ? err.message : 'Wallet connection failed';
    } finally {
      this.walletConnecting = false;
    }
  }

  async disconnectWallet(): Promise<void> {
    try {
      await this.walletService.disconnectWallet();
    } catch {
      // ignore disconnect errors
    }
  }

  toggleMenu(): void {
    this.isMenuOpen = !this.isMenuOpen;
  }

  closeMenu(): void {
    this.isMenuOpen = false;
  }
}
