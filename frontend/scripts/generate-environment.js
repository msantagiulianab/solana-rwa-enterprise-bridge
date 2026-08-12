/**
 * Build-time environment generator for the production bundle.
 *
 * Reads SECURITY_API_KEY from the build environment and bakes it into
 * `src/environments/environment.prod.ts`, which Angular's production
 * configuration swaps in via fileReplacements.
 *
 * The script fails the build when the key is missing so the X-API-Key gate can
 * never be silently disabled in a deployed artifact.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const apiKey = process.env.SECURITY_API_KEY;

if (!apiKey || apiKey.trim() === '') {
  console.error(
    '[generate-environment] SECURITY_API_KEY is not set. ' +
      'Set it in your build environment (Vercel project settings) and retry.'
  );
  process.exit(1);
}

const targetPath = path.resolve(
  __dirname,
  '..',
  'src',
  'environments',
  'environment.prod.ts'
);

const serializedKey = JSON.stringify(apiKey);

// Intentionally mirrors the shape of the other environment files so the
// exported `environment` object stays contract-compatible.
const content = [
  '// Auto-generated during build by scripts/generate-environment.js.',
  '// DO NOT EDIT MANUALLY — SECURITY_API_KEY is injected at build time.',
  '',
  'export const environment = {',
  "  production: true,",
  "  apiBaseUrl: 'https://solana-rwa-enterprise-bridge.onrender.com/api',",
  "  solanaRpcEndpoint: 'https://api.devnet.solana.com',",
  `  apiKey: ${serializedKey},`,
  '};',
  '',
].join('\n');

fs.writeFileSync(targetPath, content, 'utf8');
console.log('[generate-environment] Wrote environment.prod.ts with SECURITY_API_KEY baked in.');