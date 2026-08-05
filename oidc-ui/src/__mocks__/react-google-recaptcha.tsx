import React from 'react';

export default function ReCAPTCHAStub({
  sitekey,
}: {
  sitekey?: string;
  [key: string]: unknown;
}) {
  return React.createElement('div', {
    'data-testid': 'google-recaptcha',
    'data-sitekey': sitekey,
  });
}
