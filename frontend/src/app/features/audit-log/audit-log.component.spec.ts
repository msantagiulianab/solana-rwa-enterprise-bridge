import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { AuditLogComponent } from './audit-log.component';
import { environment } from '../../../environments/environment';
import { AuditLog } from '../../shared/models/audit-log.model';

describe('AuditLogComponent', () => {
  let component: AuditLogComponent;
  let fixture: ComponentFixture<AuditLogComponent>;
  let httpMock: HttpTestingController;

  const mockLogs: AuditLog[] = [
    {
      id: 'log-uuid-1',
      walletAddress: 'DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ',
      action: 'INVESTOR_REGISTERED',
      status: 'SUCCESS',
      reason: 'Investor Alice Johnson registered with KYC status PENDING.',
      timestamp: '2026-08-01T10:00:00Z',
    },
    {
      id: 'log-uuid-2',
      walletAddress: 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH',
      action: 'MINT_ATTEMPT',
      status: 'BLOCKED_BY_COMPLIANCE',
      reason: 'Mint blocked by compliance: investor KYC not approved.',
      timestamp: '2026-08-01T10:05:00Z',
    },
    {
      id: 'log-uuid-3',
      walletAddress: 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      action: 'RPC_CALL',
      status: 'RPC_ERROR',
      reason: 'Solana RPC timeout after 3 retries.',
      timestamp: '2026-08-01T09:55:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogComponent, HttpClientTestingModule, FormsModule],
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
    expect(component.logs.length).toBe(3);
    // Latest timestamp first after sort
    expect(component.filteredLogs[0].action).toBe('MINT_ATTEMPT');
    expect(component.filteredLogs[1].action).toBe('INVESTOR_REGISTERED');
    expect(component.filteredLogs[2].action).toBe('RPC_CALL');
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

  it('should filter logs by action search', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    fixture.detectChanges();

    component.searchAction = 'MINT';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].action).toBe('MINT_ATTEMPT');
  });

  it('should filter logs by status', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    fixture.detectChanges();

    component.filterStatus = 'SUCCESS';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].status).toBe('SUCCESS');
  });

  it('should filter logs by search and status combined', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    fixture.detectChanges();

    component.searchAction = 'rpc';
    component.filterStatus = 'RPC_ERROR';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].action).toBe('RPC_CALL');
  });

  it('should clear all filters', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    fixture.detectChanges();

    component.searchAction = 'something';
    component.filterStatus = 'SUCCESS';
    component.applyFilters();

    component.clearFilters();

    expect(component.searchAction).toBe('');
    expect(component.filterStatus).toBe('');
    expect(component.filteredLogs.length).toBe(3);
  });

  it('should search in action, reason, and wallet address', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    fixture.detectChanges();

    // Search by wallet address
    component.searchAction = 'Cvjpga';
    component.applyFilters();
    expect(component.filteredLogs.length).toBe(1);

    // Search by reason
    component.searchAction = 'timeout';
    component.applyFilters();
    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].status).toBe('RPC_ERROR');
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