export const environment = {
  production: true,
  apiBaseUrl: 'https://solana-rwa-enterprise-bridge.onrender.com/api',
  solanaRpcEndpoint: 'https://api.devnet.solana.com',
  // Shared secret for mutating API routes (POST/PATCH/PUT/DELETE).
  // Injected at build time via Vercel env (SECURITY_API_KEY); never hardcode a real key.
  apiKey: '',
};
