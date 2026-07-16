import { useRef } from "react";
import ReCAPTCHA from "react-google-recaptcha";
import type { ComponentRenderContext } from "@thunderid/react";
import type { CaptchaFlowComponent } from "./CaptchaRenderer";

interface Props {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
}

export default function GoogleReCaptcha({ component, context }: Props) {
  const fieldRef = component.ref ?? component.id;
  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  const handleVerify = (token: string | null) => {
    onInputChangeRef.current(fieldRef, token ?? "");
  };

  const handleExpire = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  return (
    <ReCAPTCHA
      sitekey={component.siteKey ?? ""}
      onChange={handleVerify}
      onExpired={handleExpire}
      theme={component.theme ?? "light"}
      size={(component.size as "normal" | "compact" | "invisible") ?? "normal"}
    />
  );
}
