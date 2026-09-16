import { TestBed } from '@angular/core/testing';
import { SolanaWalletService } from './solana-wallet.service';

describe('SolanaWalletService', () => {
  let service: SolanaWalletService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SolanaWalletService);
  });

  afterEach(() => {
    delete (window as unknown as { solana?: unknown }).solana;
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

  it('should auto-connect to an already-trusted wallet without prompting', async () => {
    const connectSpy = jasmine.createSpy('connect').and.resolveTo({
      publicKey: { toString: () => 'AutoConnectedKey' },
    });
    const onSpy = jasmine.createSpy('on');

    (window as unknown as { solana: unknown }).solana = {
      isPhantom: true,
      connect: connectSpy,
      disconnect: jasmine.createSpy('disconnect'),
      on: onSpy,
      removeListener: jasmine.createSpy('removeListener'),
    };

    const key = await service.autoConnect();

    expect(connectSpy).toHaveBeenCalledWith({ onlyIfTrusted: true });
    expect(key).toBe('AutoConnectedKey');
    expect(service.getConnectedPublicKey()).toBe('AutoConnectedKey');
    expect(onSpy).toHaveBeenCalledWith('disconnect', jasmine.any(Function));
    expect(onSpy).toHaveBeenCalledWith('accountChanged', jasmine.any(Function));
  });

  it('should silently return null when no trusted wallet session exists', async () => {
    (window as unknown as { solana: unknown }).solana = {
      isPhantom: true,
      connect: jasmine.createSpy('connect').and.rejectWith(new Error('User rejected')),
      disconnect: jasmine.createSpy('disconnect'),
      on: jasmine.createSpy('on'),
      removeListener: jasmine.createSpy('removeListener'),
    };

    const key = await service.autoConnect();

    expect(key).toBeNull();
    expect(service.getConnectedPublicKey()).toBeNull();
  });

  it('should silently return null when no provider is detected', async () => {
    const key = await service.autoConnect();

    expect(key).toBeNull();
    expect(service.getConnectedPublicKey()).toBeNull();
  });
});