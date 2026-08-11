import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';

declare global {
  interface Window {
    solana?: {
      isPhantom?: boolean;
      connect(): Promise<{ publicKey: { toString(): string } }>;
      disconnect(): Promise<void>;
      on(event: string, callback: () => void): void;
      removeListener(event: string, callback: () => void): void;
    };
    phantom?: {
      solana?: {
        isPhantom?: boolean;
        connect(): Promise<{ publicKey: { toString(): string } }>;
        disconnect(): Promise<void>;
        on(event: string, callback: () => void): void;
        removeListener(event: string, callback: () => void): void;
      };
    };
  }
}

@Injectable({
  providedIn: 'root',
})
export class SolanaWalletService {
  private readonly connectedPublicKeySubject = new BehaviorSubject<string | null>(null);
  public readonly connectedPublicKey$: Observable<string | null> =
    this.connectedPublicKeySubject.asObservable();

  private readonly isBrowser: boolean;

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  /**
   * Returns true if a Phantom wallet object is detected in the browser.
   */
  isPhantomInstalled(): boolean {
    if (!this.isBrowser) {
      return false;
    }
    const provider = window.solana || window.phantom?.solana;
    return !!(provider && provider.isPhantom);
  }

  /**
   * Connects to the Phantom / Solana wallet provider.
   * On success the connected public key is emitted via connectedPublicKey$.
   */
  async connectWallet(): Promise<string> {
    if (!this.isBrowser) {
      throw new Error('Wallet connection is only available in a browser environment');
    }

    const provider = window.solana || window.phantom?.solana;

    if (!provider) {
      throw new Error('No Solana wallet provider detected. Please install Phantom wallet.');
    }

    if (!provider.isPhantom) {
      throw new Error('Only Phantom wallet is supported at this time.');
    }

    try {
      const response = await provider.connect();
      const publicKey = response.publicKey.toString();
      this.connectedPublicKeySubject.next(publicKey);

      provider.on('disconnect', this.handleDisconnect);
      provider.on('accountChanged', this.handleAccountChanged);

      return publicKey;
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : 'Wallet connection rejected or failed';
      throw new Error(message);
    }
  }

  /**
   * Disconnects from the wallet provider.
   */
  async disconnectWallet(): Promise<void> {
    if (!this.isBrowser) {
      return;
    }

    const provider = window.solana || window.phantom?.solana;
    if (provider) {
      try {
        await provider.disconnect();
      } catch {
        // Provider may already be disconnected
      }

      provider.removeListener('disconnect', this.handleDisconnect);
      provider.removeListener('accountChanged', this.handleAccountChanged);
    }

    this.connectedPublicKeySubject.next(null);
  }

  /**
   * Returns the current connected public key value synchronously.
   */
  getConnectedPublicKey(): string | null {
    return this.connectedPublicKeySubject.getValue();
  }

  private readonly handleDisconnect = (): void => {
    this.connectedPublicKeySubject.next(null);
    this.removeProviderListeners();
  };

  private readonly handleAccountChanged = (): void => {
    this.connectedPublicKeySubject.next(null);
    this.removeProviderListeners();
  };

  private removeProviderListeners(): void {
    if (!this.isBrowser) {
      return;
    }
    const provider = window.solana || window.phantom?.solana;
    if (provider) {
      provider.removeListener('disconnect', this.handleDisconnect);
      provider.removeListener('accountChanged', this.handleAccountChanged);
    }
  }
}