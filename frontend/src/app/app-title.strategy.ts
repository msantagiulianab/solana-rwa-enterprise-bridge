import { Injectable } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/**
 * Composes the browser tab title from each route's `title` and the base
 * application title, e.g. `Solana RWA Enterprise Bridge | Asset Tokens`.
 * Routes without a title fall back to the base application title.
 */
@Injectable({ providedIn: 'root' })
export class AppTitleStrategy extends TitleStrategy {
  private static readonly BASE_TITLE = 'Solana RWA Enterprise Bridge';

  constructor(private readonly title: Title) {
    super();
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const routeTitle = this.buildTitle(snapshot);
    this.title.setTitle(
      routeTitle
        ? `${AppTitleStrategy.BASE_TITLE} | ${routeTitle}`
        : AppTitleStrategy.BASE_TITLE
    );
  }
}
