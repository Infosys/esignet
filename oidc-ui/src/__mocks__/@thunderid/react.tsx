/**
 * Stub for @thunderid/react used in tests only.
 * Each test file overrides individual exports via vi.mock('@thunderid/react', factory).
 * This stub only needs to be resolvable by Vite — the real implementations come from
 * per-test vi.mock factories.
 */
import React from 'react';

export const I18nContext = React.createContext<{
  t: (key: string) => string;
  currentLanguage: string;
} | null>(null);

export const ThunderIDProvider = ({
  children,
}: {
  children: React.ReactNode;
}) => React.createElement(React.Fragment, null, children);

export const SignIn = () =>
  React.createElement('div', { 'data-testid': 'sign-in' }, 'SignIn');

export const Button = ({
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  children?: React.ReactNode;
  fullWidth?: boolean;
  variant?: string;
  color?: string;
}) => React.createElement('button', props, children);

export const FormControl = ({
  children,
  error,
}: {
  children?: React.ReactNode;
  error?: string;
}) =>
  React.createElement(
    'div',
    { 'data-testid': 'form-control', 'data-error': error },
    children,
  );

export const useTranslation = () => ({ t: (key: string) => key });

export interface LanguageOption {
  code: string;
  displayName: string;
}

export const LanguageSwitcher = ({
  children,
}: {
  children: (props: {
    languages: LanguageOption[];
    currentLanguage: string;
    onLanguageChange: (code: string) => void;
    isLoading: boolean;
  }) => React.ReactNode;
}) =>
  React.createElement(
    React.Fragment,
    null,
    children({
      languages: [],
      currentLanguage: 'en',
      onLanguageChange: () => {},
      isLoading: false,
    }),
  );

export const resolveFlowTemplateLiterals = (
  label: string,
  _opts: unknown,
): string => label;

export const validateFieldValue = (
  _value: unknown,
  _type: unknown,
  _required: unknown,
  _isTouched: unknown,
): string | null => null;

// Type-only exports — these are erased at runtime but needed for TS in tests
export type ComponentRenderContext = {
  onSubmit?: (
    component: EmbeddedFlowComponent,
    payload: Record<string, unknown>,
    isBack?: boolean,
  ) => void;
  onInputChange: (fieldRef: string, value: string) => void;
  resetForm?: () => void;
  formValues: Record<string, string>;
  formErrors: Record<string, string>;
  touchedFields: Record<string, boolean>;
};

export type EmbeddedFlowComponent = {
  id: string;
  type: string;
  label?: string;
  ref?: string;
  required?: boolean;
  variant?: string;
  [key: string]: unknown;
};
