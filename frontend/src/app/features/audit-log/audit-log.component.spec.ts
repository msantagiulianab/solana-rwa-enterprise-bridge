import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuditLogComponent } from './audit-log.component';
import { environment } from '../../../environments/environment';
import { AuditLog, TransferHookAuditLog } from '../../shared/models/audit-log.model';

describe('AuditLogComponent', () => {
  let component: AuditLogComponent;
  let fixture: ComponentFixture<AuditLogComponent>;
  let httpMock: HttpTestingController;
  let routerMock: jasmine.SpyObj<Router>;
  let queryParamMapSubject: BehaviorSubject<ParamMap>;

  const mockLogs: AuditLog[] = [
    {
      id: 'log-uuid-1',
      walletAddress: 'DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ',
      action: 'INVESTOR_REGISTERED',
      status: 'APPROVED',
      reason: 'Investor Alice Johnson registered with KYC status PENDING.',
      timestamp: '2026-08-01T10:00:00Z',
    },
    {
      id: 'log-uuid-2',
      walletAddress: 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH',
      action: 'MINT_ATTEMPT',
      status: 'BLOCKED',
      reason: 'Mint blocked by compliance: investor KYC not approved.',
      timestamp: '2026-08-01T10:05:00Z',
    },
    {
      id: 'log-uuid-3',
      walletAddress: 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      action: 'RPC_CALL',
      status: 'BLOCKED',
      reason: 'Solana RPC timeout after 3 retries.',
      timestamp: '2026-08-01T09:55:00Z',
    },
  ];

  const mockTransferHookLogs: TransferHookAuditLog[] = [
    {
      id: 'transfer-hook-uuid-1',
      mintAddress: 'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      sourceWallet: 'DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ',
      destinationWallet: 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH',
      amount: 1250000,
      complianceStatus: 'CLEARED',
      reasonCode: null,
      transactionSignature:
        '5mocS4Rj8EXZQSYrT1mHGkPm9Kg7g4TsmQ6RzqDpEVPPu2VYJmL8cQwS4Rj8EXZQSYrT1mHGkPm9Kg7g4TsmQ6',
      createdAt: '2026-08-01T10:10:00Z',
    },
    {
      id: 'transfer-hook-uuid-2',
      mintAddress: 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v',
      sourceWallet: 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH',
      destinationWallet: '9aYWGxnFxdYqXwWXQ3x4WjZPZMmWtDQ6Vp8F3nLfQKgW',
      amount: 500000,
      complianceStatus: 'BLOCKED',
      reasonCode: 'SANCTIONED_DESTINATION',
      transactionSignature: null,
      createdAt: '2026-08-01T10:15:00Z',
    },
  ];

  beforeEach(async () => {
    routerMock = jasmine.createSpyObj<Router>('Router', ['navigate']);
    routerMock.navigate.and.resolveTo(true);
    queryParamMapSubject = new BehaviorSubject<ParamMap>(convertToParamMap({}));

    await TestBed.configureTestingModule({
      imports: [AuditLogComponent, HttpClientTestingModule, FormsModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParamMapSubject.asObservable(),
          } as unknown as ActivatedRoute,
        },
        { provide: Router, useValue: routerMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditLogComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /** Flushes both the general ledger and transfer-hook requests fired on init. */
  function flushInitialLoad(): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/v1/compliance/transfer-hook-audit-logs`)
      .flush(mockTransferHookLogs);
  }

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load audit logs sorted by timestamp descending', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`);
    expect(req.request.method).toBe('GET');
    req.flush(mockLogs);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/v1/compliance/transfer-hook-audit-logs`)
      .flush(mockTransferHookLogs);

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.logs.length).toBe(3);
    // Latest timestamp first after sort
    expect(component.filteredLogs[0].action).toBe('MINT_ATTEMPT');
    expect(component.filteredLogs[1].action).toBe('INVESTOR_REGISTERED');
    expect(component.filteredLogs[2].action).toBe('RPC_CALL');
  });

  it('should fetch transfer hook audit logs on init', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/v1/compliance/transfer-hook-audit-logs`
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockTransferHookLogs);
    httpMock.expectOne(`${environment.apiBaseUrl}/audit-logs`).flush(mockLogs);

    fixture.detectChanges();

    expect(component.transferHookLogs.length).toBe(2);
    expect(component.transferHooksLoading).toBeFalse();
  });

  it('should display error message on API failure', () => {
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/audit-logs`)
      .flush('Internal Server Error', { status: 500, statusText: 'Server Error' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/v1/compliance/transfer-hook-audit-logs`)
      .flush(mockTransferHookLogs);

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.error).toBeTruthy();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load audit logs');
  });

  it('should filter logs by action search', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.searchAction = 'MINT';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].action).toBe('MINT_ATTEMPT');
  });

  it('should filter logs by status', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.filterStatus = 'APPROVED';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].status).toBe('APPROVED');
  });

  it('should filter logs by search and status combined', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.searchAction = 'rpc';
    component.filterStatus = 'BLOCKED';
    component.applyFilters();

    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].action).toBe('RPC_CALL');
  });

  it('should clear all filters', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.searchAction = 'something';
    component.filterStatus = 'APPROVED';
    component.applyFilters();

    component.clearFilters();

    expect(component.searchAction).toBe('');
    expect(component.filterStatus).toBe('');
    expect(component.filteredLogs.length).toBe(3);
  });

  it('should search in action, reason, and wallet address', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    // Search by wallet address
    component.searchAction = 'Cvjpga';
    component.applyFilters();
    expect(component.filteredLogs.length).toBe(1);

    // Search by reason
    component.searchAction = 'timeout';
    component.applyFilters();
    expect(component.filteredLogs.length).toBe(1);
    expect(component.filteredLogs[0].status).toBe('BLOCKED');
  });

  it('should map status to correct badge classes', () => {
    const approved = component.statusBadge('APPROVED');
    expect(approved).toContain('text-green-400');

    const blocked = component.statusBadge('BLOCKED');
    expect(blocked).toContain('text-yellow-400');

    const unknown = component.statusBadge('UNKNOWN');
    expect(unknown).toContain('text-gray-400');
  });

  it('should map action to a human-readable label', () => {
    expect(component.actionLabel('TOKENIZE_ASSET')).toBe('Tokenize Asset');
    expect(component.actionLabel('KYC_VERIFIED')).toBe('KYC Verified');
    expect(component.actionLabel('CHECK_ELIGIBILITY')).toBe('Check Eligibility');
    expect(component.actionLabel('INVESTOR_REGISTERED')).toBe('Investor Registered');
    expect(component.actionLabel('MINT_ATTEMPT')).toBe('Mint Attempt');
    expect(component.actionLabel('RPC_CALL')).toBe('RPC Call');
    expect(component.actionLabel('UNKNOWN_ACTION')).toBe('Unknown Action');
  });

  it('should map action to a distinct badge style', () => {
    expect(component.actionBadge('TOKENIZE_ASSET')).toContain('text-solana-purple');
    expect(component.actionBadge('KYC_VERIFIED')).toContain('text-blue-400');
    expect(component.actionBadge('CHECK_ELIGIBILITY')).toContain('text-cyan-400');
    expect(component.actionBadge('SOMETHING_ELSE')).toContain('text-gray-300');
  });

  it('should default to the general ledger tab', () => {
    expect(component.activeTab).toBe('general');
  });

  it('should toggle between general and transfer-hook tabs', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(component.activeTab).toBe('general');

    component.setActiveTab('transfer-hook');
    fixture.detectChanges();
    expect(component.activeTab).toBe('transfer-hook');

    component.setActiveTab('general');
    fixture.detectChanges();
    expect(component.activeTab).toBe('general');
  });

  it('should select the transfer-hook tab from the tab query param on init', () => {
    queryParamMapSubject.next(convertToParamMap({ tab: 'transfer-hook' }));
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(component.activeTab).toBe('transfer-hook');
  });

  it('should sync the active tab to the tab query param via the router', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.setActiveTab('transfer-hook');
    expect(routerMock.navigate).toHaveBeenCalledWith([], {
      queryParams: { tab: 'transfer-hook' },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });

    component.setActiveTab('general');
    expect(routerMock.navigate).toHaveBeenCalledWith([], {
      queryParams: { tab: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  });

  it('should switch tabs from the segmented control', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(compiled.querySelectorAll('button'));
    const transferHookBtn = buttons.find((b) =>
      (b.textContent || '').includes('Token-2022 Transfer Hook Audits')
    );
    expect(transferHookBtn).toBeTruthy();

    transferHookBtn!.click();
    fixture.detectChanges();

    expect(component.activeTab).toBe('transfer-hook');
  });

  it('should render transfer hook rows with explorer links and null-sig indicator', () => {
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    component.setActiveTab('transfer-hook');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // CLEARED row with a non-null signature renders a Solana Devnet explorer link
    const link = compiled.querySelector(
      'a[href*="explorer.solana.com/tx/"]'
    ) as HTMLAnchorElement;
    expect(link).toBeTruthy();
    expect(link.getAttribute('href')).toContain('cluster=devnet');
    expect(link.getAttribute('href')).toContain(
      mockTransferHookLogs[0].transactionSignature
    );
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');

    // BLOCKED row with a null signature renders the distinct indicator
    expect(compiled.textContent).toContain('Blocked (Null Sig)');

    // Status badges render both CLEARED and BLOCKED
    expect(compiled.textContent).toContain('CLEARED');
    expect(compiled.textContent).toContain('BLOCKED');
  });

  it('should map transfer hook compliance status to badge classes', () => {
    expect(component.transferHookStatusBadge('CLEARED')).toContain('text-green-400');
    expect(component.transferHookStatusBadge('BLOCKED')).toContain('text-red-400');
    expect(component.transferHookStatusBadge('UNKNOWN')).toContain('text-gray-400');
  });
});
