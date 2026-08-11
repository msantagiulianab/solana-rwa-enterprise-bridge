import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { InvestorKycComponent } from './investor-kyc.component';
import { environment } from '../../../environments/environment';
import { Investor } from '../../shared/models/investor.model';

describe('InvestorKycComponent', () => {
  let component: InvestorKycComponent;
  let fixture: ComponentFixture<InvestorKycComponent>;
  let httpMock: HttpTestingController;

  const mockInvestors: Investor[] = [
    {
      id: 1,
      fullName: 'Alice Johnson',
      email: 'alice@example.com',
      solanaAddress: 'DRpbCBMxVnDK7maPMoGQFix5grYexXr3coWsyhEcz6iZ',
      kycStatus: 'APPROVED',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InvestorKycComponent, HttpClientTestingModule, FormsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(InvestorKycComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load investors on init', () => {
    fixture.detectChanges();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/investors`);
    expect(req.request.method).toBe('GET');
    req.flush(mockInvestors);

    fixture.detectChanges();

    expect(component.loading).toBeFalse();
    expect(component.investors.length).toBe(1);
    expect(component.investors[0].fullName).toBe('Alice Johnson');
  });

  it('should require all fields for registration', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/investors`).flush([]);
    fixture.detectChanges();

    component.fullName = '';
    component.email = '';
    component.solanaAddress = '';
    component.registerInvestor();

    expect(component.submitError).toBe('All fields are required.');
  });

  it('should register an investor successfully', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/investors`).flush([]);
    fixture.detectChanges();

    component.fullName = 'Bob Smith';
    component.email = 'bob@example.com';
    component.solanaAddress = 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH';
    component.registerInvestor();

    const postReq = httpMock.expectOne(`${environment.apiBaseUrl}/investors`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body.fullName).toBe('Bob Smith');

    const newInvestor: Investor = {
      id: 2,
      fullName: 'Bob Smith',
      email: 'bob@example.com',
      solanaAddress: 'CvjpgaMsCNqmEH65WoFjfKep97Wvwy5uLCEiVRBUcoXH',
      kycStatus: 'PENDING',
      createdAt: '2026-08-03T00:00:00Z',
      updatedAt: '2026-08-03T00:00:00Z',
    };
    postReq.flush(newInvestor);

    fixture.detectChanges();

    expect(component.investors.length).toBe(1);
    expect(component.submitSuccess).toContain('Bob Smith');
    expect(component.fullName).toBe('');
  });

  it('should handle registration error', () => {
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/investors`).flush([]);
    fixture.detectChanges();

    component.fullName = 'Fail User';
    component.email = 'fail@example.com';
    component.solanaAddress = 'InvalidAddress';
    component.registerInvestor();

    const postReq = httpMock.expectOne(`${environment.apiBaseUrl}/investors`);
    postReq.flush({ message: 'Invalid Solana address' }, { status: 400, statusText: 'Bad Request' });

    fixture.detectChanges();

    expect(component.submitError).toBe('Invalid Solana address');
    expect(component.submitting).toBeFalse();
  });

  it('should map KYC status to correct color classes', () => {
    expect(component.kycStatusColor('APPROVED')).toContain('text-green-400');
    expect(component.kycStatusColor('IN_REVIEW')).toContain('text-blue-400');
    expect(component.kycStatusColor('PENDING')).toContain('text-yellow-400');
    expect(component.kycStatusColor('REJECTED')).toContain('text-red-400');
    expect(component.kycStatusColor('UNKNOWN')).toContain('text-gray-400');
  });
});