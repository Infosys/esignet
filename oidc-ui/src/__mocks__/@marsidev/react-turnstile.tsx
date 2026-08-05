import React from 'react';

export function Turnstile({
  siteKey,
}: {
  siteKey?: string;
  [key: string]: unknown;
}) {
  return React.createElement('div', {
    'data-testid': 'cloudflare-turnstile',
    'data-sitekey': siteKey,
  });
}
