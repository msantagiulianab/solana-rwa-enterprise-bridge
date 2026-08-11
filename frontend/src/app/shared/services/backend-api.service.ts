import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AssetToken, CreateAssetTokenRequest } from '../models/asset-token.model';
import { Investor, RegisterInvestorRequest, UpdateInvestorStatusRequest } from '../models/investor.model';
import { AuditLog } from '../models/audit-log.model';

@Injectable({
  providedIn: 'root',
})
export class BackendApiService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  /* ---------- Asset Tokens ---------- */

  getAssetTokens(): Observable<AssetToken[]> {
    return this.http.get<AssetToken[]>(`${this.baseUrl}/tokens`);
  }

  getAssetTokenById(id: string): Observable<AssetToken> {
    return this.http.get<AssetToken>(`${this.baseUrl}/tokens/${id}`);
  }

  createAssetToken(payload: CreateAssetTokenRequest): Observable<AssetToken> {
    return this.http.post<AssetToken>(`${this.baseUrl}/tokens`, payload);
  }

  /* ---------- Investors ---------- */

  getInvestors(): Observable<Investor[]> {
    return this.http.get<Investor[]>(`${this.baseUrl}/investors`);
  }

  getInvestorById(id: string): Observable<Investor> {
    return this.http.get<Investor>(`${this.baseUrl}/investors/${id}`);
  }

  registerInvestor(payload: RegisterInvestorRequest): Observable<Investor> {
    return this.http.post<Investor>(`${this.baseUrl}/investors`, payload);
  }

  updateInvestorStatus(id: string, payload: UpdateInvestorStatusRequest): Observable<Investor> {
    return this.http.patch<Investor>(`${this.baseUrl}/investors/${id}/status`, payload);
  }

  /* ---------- Audit Logs ---------- */

  getAuditLogs(): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.baseUrl}/audit-logs`);
  }
}