import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AppComponent } from './app.component';
import { SolanaWalletService } from './shared/services/solana-wallet.service';
import { BehaviorSubject } from 'rxjs';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let walletServiceMock: jasmine.SpyObj<SolanaWalletService>;
  let connectedPublicKeySubject: BehaviorSubject<string | null>;

  beforeEach(async () => {
    connectedPublicKeySubject = new BehaviorSubject<string | null>(null);

    walletServiceMock = jasmine.createSpyObj<SolanaWalletService>(
      'SolanaWalletService',
      ['isPhantomInstalled', 'connectWallet', 'disconnectWallet', 'getConnectedPublicKey'],
      {
        connectedPublicKey$: connectedPublicKeySubject.asObservable(),
      }
    );

    walletServiceMock.isPhantomInstalled.and.returnValue(false);
    walletServiceMock.getConnectedPublicKey.and.returnValue(null);

    await TestBed.configureTestingModule({
      imports: [AppComponent, RouterTestingModule],
      providers: [{ provide: SolanaWalletService, useValue: walletServiceMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

  it('should have correct title', () => {
    expect(component.title).toBe('Solana RWA Enterprise Bridge');
  });

  it('should render navigation links', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const tokensLink = compiled.querySelector('a[href="/tokens"]');
    expect(tokensLink?.textContent).toContain('Asset Tokens');

    const investorsLink = compiled.querySelector('a[href="/investors"]');
    expect(investorsLink?.textContent).toContain('Investor KYC');

    const auditLogsLink = compiled.querySelector('a[href="/audit-logs"]');
    expect(auditLogsLink?.textContent).toContain('Audit Logs');
  });

  it('should show Install Phantom button when phantom is not installed', () => {
    walletServiceMock.isPhantomInstalled.and.returnValue(false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const installLink = compiled.querySelector('a[href="https://phantom.app/"]');
    expect(installLink).toBeTruthy();
    expect(installLink?.textContent).toContain('Install Phantom');
  });

  it('should show Connect Wallet button when phantom is installed but not connected', () => {
    walletServiceMock.isPhantomInstalled.and.returnValue(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const connectBtn = compiled.querySelector('button');
    expect(connectBtn?.textContent).toContain('Connect Wallet');
  });

  it('should display connected public key and disconnect button when wallet is connected', () => {
    walletServiceMock.isPhantomInstalled.and.returnValue(true);
    connectedPublicKeySubject.next('DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('DRpb');
    expect(compiled.textContent).toContain('Disconnect');
  });

  it('should call connectWallet on button click', () => {
    walletServiceMock.isPhantomInstalled.and.returnValue(true);
    walletServiceMock.connectWallet.and.resolveTo('FakePublicKey');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const connectBtn = compiled.querySelector('button');
    connectBtn?.click();

    expect(walletServiceMock.connectWallet).toHaveBeenCalled();
  });

  it('should call disconnectWallet on disconnect button click', () => {
    walletServiceMock.isPhantomInstalled.and.returnValue(true);
    connectedPublicKeySubject.next('DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ');
    walletServiceMock.disconnectWallet.and.resolveTo();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const disconnectBtn = compiled.querySelector('button');
    disconnectBtn?.click();

    expect(walletServiceMock.disconnectWallet).toHaveBeenCalled();
  });
});