import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AssetToken } from '../models/asset-token.model';
import { Investor } from '../models/investor.model';
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

  getAssetTokenById(id: number): Observable<AssetToken> {
    return this.http.get<AssetToken>(`${this.baseUrl}/tokens/${id}`);
  }

  /* ---------- Investors ---------- */

  getInvestors(): Observable<Investor[]> {
    return this.http.get<Investor[]>(`${this.baseUrl}/investors`);
  }

  getInvestorById(id: number): Observable<Investor> {
    return this.http.get<Investor>(`${this.baseUrl}/investors/${id}`);
  }

  registerInvestor(payload: {
    fullName: string;
    email: string;
    solanaAddress: string;
  }): Observable<Investor> {
    return this.http.post<Investor>(`${this.baseUrl}/investors`, payload);
  }

  /* ---------- Audit Logs ---------- */

  getAuditLogs(): Observable<AuditLog[]> {
    return this.http.get<AuditLog[]>(`${this.baseUrl}/audit-logs`);
  }
}