import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { BehaviorSubject, Observable } from 'rxjs';
/**
 * localStorage key used to remember the last connected wallet so an
 * authorized session can be restored on refresh. Only this non-sensitive
 * wallet identifier is persisted — never the connected public key.
 */
const WALLET_NAME_STORAGE_KEY = 'walletName';

/** Wallet identifier persisted under {@link WALLET_NAME_STORAGE_KEY}. */
const WALLET_NAME_PHANTOM = 'phantom';

declare global {
  interface Window {
    solana?: {
      isPhantom?: boolean;
      connect(options?: {
        onlyIfTrusted?: boolean;
      }): Promise<{ publicKey: { toString(): string } }>;
      disconnect(): Promise<void>;
      on(event: string, callback: () => void): void;
      removeListener(event: string, callback: () => void): void;
    };
    phantom?: {
      solana?: {
        isPhantom?: boolean;
        connect(options?: {
          onlyIfTrusted?: boolean;
        }): Promise<{ publicKey: { toString(): string } }>;
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
    void this.autoConnect();
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
   * Returns true when the app is running on a mobile browser.
   */
  isMobileDevice(): boolean {
    if (!this.isBrowser) {
      return false;
    }
    return /Android|iPhone|iPad|iPod|Opera Mini|IEMobile|Mobile/i.test(
      window.navigator.userAgent
    );
  }

  /**
   * Builds Phantom's official browse universal link so mobile users are
   * redirected into Phantom's in-app browser tab with the dApp loaded.
   */
  buildPhantomDeepLink(): string {
    const currentUrl = window.location.href;
    const encodedUrl = encodeURIComponent(currentUrl);
    return `https://phantom.app/ul/browse/${encodedUrl}?ref=${encodedUrl}`;
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

      this.persistAutoConnectFlag();

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
   * Attempts to reconnect to an already-authorized wallet session without
   * prompting the user. Phantom's `connect({ onlyIfTrusted: true })` resolves
   * only when the dApp is already trusted; otherwise it rejects and we fall
   * back to the disconnected state silently.
   */
  async autoConnect(): Promise<string | null> {
    if (!this.isBrowser) {
      return null;
    }

    if (!this.hasAutoConnectFlag()) {
      return null;
    }

    const provider = window.solana || window.phantom?.solana;
    if (!provider || !provider.isPhantom) {
      return null;
    }

    try {
      const response = await provider.connect({ onlyIfTrusted: true });
      const publicKey = response.publicKey.toString();
      this.connectedPublicKeySubject.next(publicKey);

      provider.on('disconnect', this.handleDisconnect);
      provider.on('accountChanged', this.handleAccountChanged);

      return publicKey;
    } catch {
      // No trusted session exists (or authorization was revoked).
      return null;
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
    this.clearAutoConnectFlag();
  }

  /**
   * Returns the current connected public key value synchronously.
   */
  getConnectedPublicKey(): string | null {
    return this.connectedPublicKeySubject.getValue();
  }

  private persistAutoConnectFlag(): void {
    if (!this.isBrowser) {
      return;
    }
    localStorage.setItem(WALLET_NAME_STORAGE_KEY, WALLET_NAME_PHANTOM);
  }

  private clearAutoConnectFlag(): void {
    if (!this.isBrowser) {
      return;
    }
    localStorage.removeItem(WALLET_NAME_STORAGE_KEY);
  }

  private hasAutoConnectFlag(): boolean {
    if (!this.isBrowser) {
      return false;
    }
    return !!localStorage.getItem(WALLET_NAME_STORAGE_KEY);
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