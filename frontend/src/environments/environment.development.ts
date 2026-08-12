export const environment = {
  production: false,
  apiBaseUrl: 'https://solana-rwa-enterprise-bridge.onrender.com/api',
  solanaRpcEndpoint: 'https://api.devnet.solana.com',
  // Shared secret for mutating API routes (POST/PATCH/PUT/DELETE).
  // Injected at build time; never hardcode a real key.
  apiKey: '',
};
