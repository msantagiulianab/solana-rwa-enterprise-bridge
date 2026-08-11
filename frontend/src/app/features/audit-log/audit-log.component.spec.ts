import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AuditLogComponent } from './audit-log.component';
import { environment } from '../../../environments/environment';
import { AuditLog } from '../../shared/models/audit-log.model';

describe('AuditLogComponent', () => {
  let component: AuditLogComponent;
  let fixture: ComponentFixture<AuditLogComponent>;
  let httpMock: HttpTestingController;

  const mockLogs: AuditLog[] = [
    {
      id: 1,
      eventType: 'INVESTOR_REGISTERED',
      description: 'Investor Alice Johnson registered with KYC status PENDING.',
      status: 'SUCCESS',
      timestamp: '2026-08-01T10:00:00Z',
      investorId: 1,
      onchainTxHash: null,
    },
    {
      id: 2,
      eventType: 'MINT_ATTEMPT',
      description: 'Mint of TPT token blocked by compliance: investor KYC not approved.',
      status: 'BLOCKED_BY_COMPLIANCE',
      timestamp: '2026-08-01T10:05:00Z',
      investorId: 2,
      assetTokenId: 1,
      onchainTxHash: null,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogComponent, HttpClientTestingModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditLogComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load audit logs sorted by timestamp descending', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`);
    expect(req.request.method).toBe('GET');
    req.flush(mockLogs);

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.logs.length).toBe(2);
    // BLOCKED_BY_COMPLIANCE has later timestamp, so it should be first after sort
    expect(component.logs[0].eventType).toBe('MINT_ATTEMPT');
    expect(component.logs[1].eventType).toBe('INVESTOR_REGISTERED');
  });

  it('should display error message on API failure', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`);
    req.flush('Internal Server Error', { status: 500, statusText: 'Server Error' });

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.error).toBeTruthy();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load audit logs');
  });

  it('should map status to correct badge classes', () => {
    const success = component.statusBadge('SUCCESS');
    expect(success).toContain('text-green-400');

    const blocked = component.statusBadge('BLOCKED_BY_COMPLIANCE');
    expect(blocked).toContain('text-yellow-400');

    const rejected = component.statusBadge('REJECTED');
    expect(rejected).toContain('text-red-400');

    const rpcError = component.statusBadge('RPC_ERROR');
    expect(rpcError).toContain('text-orange-400');

    const unknown = component.statusBadge('UNKNOWN');
    expect(unknown).toContain('text-gray-400');
  });
});