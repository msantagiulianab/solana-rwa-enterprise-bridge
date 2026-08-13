import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { AssetTokenizationComponent } from './asset-tokenization.component';
import { environment } from '../../../environments/environment';
import { AssetToken } from '../../shared/models/asset-token.model';

describe('AssetTokenizationComponent', () => {
  let component: AssetTokenizationComponent;
  let fixture: ComponentFixture<AssetTokenizationComponent>;
  let httpMock: HttpTestingController;

  const mockTokens: AssetToken[] = [
    {
      id: 'token-uuid-1',
      assetName: 'Luxury Real Estate Token',
      valuationUsd: 1_000_000,
      mintAddress: null,
      complianceStatus: 'PENDING_KYC_APPROVAL',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    },
    {
      id: 'token-uuid-2',
      assetName: 'Gold Bond Token',
      valuationUsd: 500_000,
      mintAddress: 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      complianceStatus: 'MINTED',
      createdAt: '2026-08-02T00:00:00Z',
      updatedAt: '2026-08-02T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssetTokenizationComponent, HttpClientTestingModule, FormsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AssetTokenizationComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should display loading spinner initially', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    expect(req.request.method).toBe('GET');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.animate-spin')).toBeTruthy();
    expect(component.loading).toBeTrue();

    req.flush(mockTokens);
  });

  it('should load and display asset tokens', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTokens);

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.tokens.length).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = compiled.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Luxury Real Estate Token');
  });

  it('should render a clickable Devnet explorer link for a valid mint address', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    req.flush(mockTokens);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('tbody tr a[href]') as HTMLAnchorElement | null;

    expect(link).toBeTruthy();
    expect(link!.getAttribute('href')).toBe(
      'https://explorer.solana.com/address/TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA?cluster=devnet'
    );
    expect(link!.getAttribute('target')).toBe('_blank');
    expect(link!.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link!.textContent).toContain('...');
  });

  it('should display Pending for a missing or invalid mint address', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    req.flush(mockTokens);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rows = compiled.querySelectorAll('tbody tr');
    expect(rows[0].textContent).toContain('Pending...');
  });

  it('should validate Solana base58 mint addresses', () => {
    expect(component.isValidMintAddress('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA')).toBeTrue();
    expect(component.isValidMintAddress('Pending...')).toBeFalse();
    expect(component.isValidMintAddress(null)).toBeFalse();
    expect(component.isValidMintAddress(undefined)).toBeFalse();
    expect(component.isValidMintAddress('0OIl')).toBeFalse();
  });

  it('should build a truncated mint address', () => {
    const mint = 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA';
    expect(component.truncateMintAddress(mint)).toBe('Tokenk...3VQ5DA');
    expect(component.truncateMintAddress('short')).toBe('short');
  });

  it('should display error message on API failure', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.error).toBeTruthy();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load asset tokens');
  });

  it('should open and close the tokenize modal', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/tokens`).flush([]);
    fixture.detectChanges();

    expect(component.showTokenizeModal).toBeFalse();

    component.openTokenizeModal();
    fixture.detectChanges();
    expect(component.showTokenizeModal).toBeTrue();

    component.closeTokenizeModal();
    fixture.detectChanges();
    expect(component.showTokenizeModal).toBeFalse();
  });

  it('should create a new asset token via POST', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/tokens`).flush([]);
    fixture.detectChanges();

    component.assetName = 'Test Token';
    component.valuationUsd = 1000;

    const newToken: AssetToken = {
      id: 'token-uuid-3',
      assetName: 'Test Token',
      valuationUsd: 1000,
      mintAddress: null,
      complianceStatus: 'PENDING_KYC_APPROVAL',
      createdAt: '2026-08-03T00:00:00Z',
      updatedAt: '2026-08-03T00:00:00Z',
    };

    component.createAssetToken();

    const postReq = httpMock.expectOne(`${environment.apiBaseUrl}/tokens`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body.assetName).toBe('Test Token');
    expect(postReq.request.body.valuationUsd).toBe(1000);
    postReq.flush(newToken);

    fixture.detectChanges();

    expect(component.tokens.length).toBe(1);
    expect(component.tokens[0].assetName).toBe('Test Token');
    expect(component.submitSuccess).toContain('Test Token');
  });

  it('should require all fields for tokenization', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/tokens`).flush([]);
    fixture.detectChanges();

    component.assetName = '';
    component.valuationUsd = null;
    component.createAssetToken();

    expect(component.submitError).toBe('All fields are required and valuation must be greater than 0.');
  });

  it('should map compliance status to correct color classes', () => {
    expect(component.statusColor('MINTED')).toBe('text-green-400');
    expect(component.statusColor('KYC_APPROVED')).toBe('text-blue-400');
    expect(component.statusColor('PENDING_KYC_APPROVAL')).toBe('text-yellow-400');
    expect(component.statusColor('FROZEN')).toBe('text-orange-400');
    expect(component.statusColor('BURNED')).toBe('text-gray-400');
    expect(component.statusColor('REJECTED')).toBe('text-red-400');
    expect(component.statusColor('UNKNOWN')).toBe('text-gray-300');
  });
});