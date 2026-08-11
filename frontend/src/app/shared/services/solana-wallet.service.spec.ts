import { TestBed } from '@angular/core/testing';
import { SolanaWalletService } from './solana-wallet.service';

describe('SolanaWalletService', () => {
  let service: SolanaWalletService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SolanaWalletService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should expose connectedPublicKey$ as an observable', (done) => {
    service.connectedPublicKey$.subscribe((key) => {
      expect(key).toBeNull();
      done();
    });
  });

  it('should return null for getConnectedPublicKey initially', () => {
    expect(service.getConnectedPublicKey()).toBeNull();
  });

  it('should report phantom as not installed in non-browser environment', () => {
    expect(service.isPhantomInstalled()).toBeFalse();
  });
});