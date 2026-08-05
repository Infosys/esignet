import React from 'react';

export default function HCaptchaStub({
  sitekey,
}: {
  sitekey?: string;
  [key: string]: unknown;
}) {
  return React.createElement('div', {
    'data-testid': 'hcaptcha',
    'data-sitekey': sitekey,
  });
}
