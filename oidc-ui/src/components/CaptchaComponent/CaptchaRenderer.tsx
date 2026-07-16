import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";
import CaptchaComponent from "./CaptchaComponent";

export type CaptchaProvider =
  | "google-recaptcha"
  | "cloudflare-turnstile"
  | "hcaptcha";

export interface CaptchaFlowComponent extends EmbeddedFlowComponent {
  captchaProvider?: CaptchaProvider;
  siteKey?: string;
  theme?: "light" | "dark";
  size?: string;
}

export default function CaptchaRenderer(
  component: CaptchaFlowComponent,
  context: ComponentRenderContext,
) {
  return (
    <div className="captcha-renderer">
      <CaptchaComponent key={component.id} component={component} context={context} />
    </div>
  );
}
