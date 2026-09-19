import { TestBed } from '@angular/core/testing';
import { SolanaWalletService } from './solana-wallet.service';

const WALLET_NAME_STORAGE_KEY = 'walletName';

describe('SolanaWalletService', () => {
  let service: SolanaWalletService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(SolanaWalletService);
  });

  afterEach(() => {
    delete (window as unknown as { solana?: unknown }).solana;
    localStorage.clear();
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
    localStorage.setItem(WALLET_NAME_STORAGE_KEY, 'phantom');

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
    localStorage.setItem(WALLET_NAME_STORAGE_KEY, 'phantom');

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

  it('should not attempt to reconnect after an explicit disconnect', async () => {
    const connectSpy = jasmine.createSpy('connect').and.resolveTo({
      publicKey: { toString: () => 'ShouldNotConnect' },
    });

    (window as unknown as { solana: unknown }).solana = {
      isPhantom: true,
      connect: connectSpy,
      disconnect: jasmine.createSpy('disconnect'),
      on: jasmine.createSpy('on'),
      removeListener: jasmine.createSpy('removeListener'),
    };

    const key = await service.autoConnect();

    expect(connectSpy).not.toHaveBeenCalled();
    expect(key).toBeNull();
    expect(service.getConnectedPublicKey()).toBeNull();
  });

  it('should persist the auto-connect flag after a successful connection', async () => {
    (window as unknown as { solana: unknown }).solana = {
      isPhantom: true,
      connect: jasmine.createSpy('connect').and.resolveTo({
        publicKey: { toString: () => 'ConnectedKey' },
      }),
      disconnect: jasmine.createSpy('disconnect'),
      on: jasmine.createSpy('on'),
      removeListener: jasmine.createSpy('removeListener'),
    };

    const key = await service.connectWallet();

    expect(key).toBe('ConnectedKey');
    expect(localStorage.getItem(WALLET_NAME_STORAGE_KEY)).toBe('phantom');
  });

  it('should disconnect the provider, clear the auto-connect flag, and reset state', async () => {
    localStorage.setItem(WALLET_NAME_STORAGE_KEY, 'phantom');

    const disconnectSpy = jasmine.createSpy('disconnect').and.resolveTo();
    const removeListenerSpy = jasmine.createSpy('removeListener');

    (window as unknown as { solana: unknown }).solana = {
      isPhantom: true,
      connect: jasmine.createSpy('connect').and.resolveTo({
        publicKey: { toString: () => 'ConnectedKey' },
      }),
      disconnect: disconnectSpy,
      on: jasmine.createSpy('on'),
      removeListener: removeListenerSpy,
    };

    await service.connectWallet();
    await service.disconnectWallet();

    expect(disconnectSpy).toHaveBeenCalled();
    expect(removeListenerSpy).toHaveBeenCalledWith('disconnect', jasmine.any(Function));
    expect(removeListenerSpy).toHaveBeenCalledWith('accountChanged', jasmine.any(Function));
    expect(localStorage.getItem(WALLET_NAME_STORAGE_KEY)).toBeNull();
    expect(service.getConnectedPublicKey()).toBeNull();
  });
});