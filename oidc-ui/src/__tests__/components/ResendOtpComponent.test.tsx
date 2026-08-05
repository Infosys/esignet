import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import React from 'react';
import ResendOtp from '../../components/ResendOtpComponent/ResendOtpComponent';
import ResendOtpRenderer from '../../components/ResendOtpComponent/ResendOtpRenderer';
import type { ResendOtpFlowComponent } from '../../components/ResendOtpComponent/ResendOtpModel';
import type { ComponentRenderContext } from '@thunderid/react';

const mockT = vi.fn((key: string) => key);

vi.mock('@thunderid/react', () => ({
  useTranslation: () => ({ t: mockT }),
  Button: ({
    children,
    disabled,
    onClick,
    id,
  }: {
    children: React.ReactNode;
    disabled?: boolean;
    onClick?: () => void;
    id?: string;
  }) =>
    React.createElement(
      'button',
      { disabled, onClick, id, 'data-testid': 'resend-button' },
      children,
    ),
}));

// Stub CaptchaComponent — we test the captcha integration separately
vi.mock('../../components/index', () => ({
  CaptchaComponent: () =>
    React.createElement('div', { 'data-testid': 'captcha' }),
}));

function makeComponent(
  overrides: Partial<ResendOtpFlowComponent> = {},
): ResendOtpFlowComponent {
  return {
    id: 'resend-otp',
    type: 'RESEND_OTP',
    timeLeft: 0,
    ...overrides,
  } as ResendOtpFlowComponent;
}

function makeContext(
  overrides: Partial<ComponentRenderContext> = {},
): ComponentRenderContext {
  return {
    onSubmit: vi.fn(),
    onInputChange: vi.fn(),
    formValues: {},
    formErrors: {},
    touchedFields: {},
    resetForm: vi.fn(),
    ...overrides,
  } as unknown as ComponentRenderContext;
}

describe('ResendOtpComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the resend button', () => {
    render(<ResendOtp component={makeComponent()} context={makeContext()} />);
    expect(screen.getByTestId('resend-button')).toBeDefined();
  });

  it('button is enabled when timeLeft is 0', () => {
    render(
      <ResendOtp component={makeComponent({ timeLeft: 0 })} context={makeContext()} />,
    );
    expect(screen.getByTestId('resend-button')).not.toBeDisabled();
  });

  it('button is disabled while the countdown is active', () => {
    render(
      <ResendOtp component={makeComponent({ timeLeft: 60 })} context={makeContext()} />,
    );
    expect(screen.getByTestId('resend-button')).toBeDisabled();
  });

  it('shows the countdown timer heading while time remains', () => {
    render(
      <ResendOtp component={makeComponent({ timeLeft: 90 })} context={makeContext()} />,
    );
    // Timer heading renders the translation key for resend_timer
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
  });

  it('hides the countdown heading when timer reaches zero', () => {
    render(
      <ResendOtp component={makeComponent({ timeLeft: 1 })} context={makeContext()} />,
    );
    act(() => { vi.advanceTimersByTime(1500); });
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
  });

  it('button becomes enabled after the countdown expires', () => {
    render(
      <ResendOtp component={makeComponent({ timeLeft: 1 })} context={makeContext()} />,
    );
    act(() => { vi.advanceTimersByTime(1500); });
    expect(screen.getByTestId('resend-button')).not.toBeDisabled();
  });

  it('decrements the countdown — covers return prev - 1 branch (prev > 1)', () => {
    // timeLeft: 2 → first tick (1000ms): prev=2 > 1, so return prev-1=1 (false branch of if)
    render(
      <ResendOtp component={makeComponent({ timeLeft: 2 })} context={makeContext()} />,
    );
    // After one interval tick (1000ms), remaining goes from 2 → 1 via the false branch.
    act(() => { vi.advanceTimersByTime(1000); });
    // Timer heading still shows (remaining is 1, not yet 0).
    expect(screen.getByText(/app\.otp\.resend_timer/)).toBeDefined();
    // After another tick the timer reaches 0.
    act(() => { vi.advanceTimersByTime(1000); });
    expect(screen.queryByText(/app\.otp\.resend_timer/)).toBeNull();
  });

  it('calls context.onSubmit when button is clicked', () => {
    const context = makeContext();
    render(<ResendOtp component={makeComponent({ timeLeft: 0 })} context={context} />);
    fireEvent.click(screen.getByTestId('resend-button'));
    expect(context.onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'resend-otp' }),
      expect.any(Object),
      true,
    );
  });

  it('shows captcha widget after timer expires when captcha is configured', () => {
    render(
      <ResendOtp
        component={makeComponent({
          timeLeft: 1,
          captcha: { provider: 'hcaptcha', siteKey: 'key' },
        })}
        context={makeContext()}
      />,
    );
    act(() => { vi.advanceTimersByTime(1500); });
    expect(screen.getByTestId('captcha')).toBeDefined();
  });

  it('does not show captcha when timeLeft > 0', () => {
    render(
      <ResendOtp
        component={makeComponent({
          timeLeft: 60,
          captcha: { provider: 'hcaptcha', siteKey: 'key' },
        })}
        context={makeContext()}
      />,
    );
    expect(screen.queryByTestId('captcha')).toBeNull();
  });

  it('blocks submission and marks captcha touched when captcha is unverified', () => {
    const context = makeContext();
    render(
      <ResendOtp
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: 'hcaptcha', siteKey: 'key' },
        })}
        context={context}
      />,
    );
    fireEvent.click(screen.getByTestId('resend-button'));
    expect(context.onSubmit).not.toHaveBeenCalled();
    expect(context.touchedFields['resend-otp_captcha']).toBe(true);
  });

  it('shows captcha form error when present in context', () => {
    const captchaId = 'resend-otp_captcha';
    const context = makeContext({
      formErrors: { [captchaId]: 'Captcha required' },
    });
    render(
      <ResendOtp
        component={makeComponent({
          timeLeft: 0,
          captcha: { provider: 'hcaptcha', siteKey: 'key' },
        })}
        context={context}
      />,
    );
    expect(screen.getByText('Captcha required')).toBeDefined();
  });
});

describe('ResendOtpRenderer', () => {
  it('wraps ResendOtp in a back-button-renderer div', () => {
    const { container } = render(
      <>{ResendOtpRenderer(makeComponent(), makeContext())}</>,
    );
    expect(container.querySelector('.back-button-renderer')).not.toBeNull();
  });
});
