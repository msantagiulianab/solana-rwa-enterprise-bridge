import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AssetTokenizationComponent } from './asset-tokenization.component';
import { environment } from '../../../environments/environment';
import { AssetToken } from '../../shared/models/asset-token.model';

describe('AssetTokenizationComponent', () => {
  let component: AssetTokenizationComponent;
  let fixture: ComponentFixture<AssetTokenizationComponent>;
  let httpMock: HttpTestingController;

  const mockTokens: AssetToken[] = [
    {
      id: 1,
      mintAddress: 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      assetName: 'Test Property Token',
      symbol: 'TPT',
      totalSupply: 1_000_000,
      decimals: 9,
      complianceStatus: 'MINTED',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    },
    {
      id: 2,
      mintAddress: 'So11111111111111111111111111111111111111112',
      assetName: 'Pending Asset',
      symbol: 'PNG',
      totalSupply: 500_000,
      decimals: 6,
      complianceStatus: 'PENDING_KYC_APPROVAL',
      createdAt: '2026-08-02T00:00:00Z',
      updatedAt: '2026-08-02T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssetTokenizationComponent, HttpClientTestingModule],
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
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.animate-spin')).toBeTruthy();
    expect(component.loading).toBeTrue();
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
    expect(rows[0].textContent).toContain('Test Property Token');
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