import type { ComponentRenderContext } from "@thunderid/react";
import type { CaptchaFlowComponent } from "./CaptchaRenderer";
import GoogleReCaptcha from "./GoogleReCaptcha";
import CloudflareTurnstile from "./CloudflareTurnstile";
import HCaptcha from "./HCaptcha";

interface CaptchaComponentProps {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
}

export default function CaptchaComponent({ component, context }: CaptchaComponentProps) {
  const { captchaProvider } = component;

  if (captchaProvider === "google-recaptcha") {
    return <GoogleReCaptcha component={component} context={context} />;
  }

  if (captchaProvider === "cloudflare-turnstile") {
    return <CloudflareTurnstile component={component} context={context} />;
  }

  if (captchaProvider === "hcaptcha") {
    return <HCaptcha component={component} context={context} />;
  }

  return null;
}
