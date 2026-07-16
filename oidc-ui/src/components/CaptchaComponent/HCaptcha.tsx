import { useRef } from "react";
import HCaptchaLib from "@hcaptcha/react-hcaptcha";
import type { ComponentRenderContext } from "@thunderid/react";
import type { CaptchaFlowComponent } from "./CaptchaRenderer";

interface Props {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
}

export default function HCaptcha({ component, context }: Props) {
  const fieldRef = component.ref ?? component.id;
  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  const handleVerify = (token: string) => {
    onInputChangeRef.current(fieldRef, token);
  };

  const handleExpire = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  const handleError = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  return (
    <HCaptchaLib
      sitekey={component.siteKey ?? ""}
      onVerify={handleVerify}
      onExpire={handleExpire}
      onError={handleError}
      theme={component.theme ?? "light"}
      size={(component.size as "normal" | "compact" | "invisible") ?? "normal"}
    />
  );
}
