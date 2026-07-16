import { useRef } from "react";
import { Turnstile } from "@marsidev/react-turnstile";
import type { ComponentRenderContext } from "@thunderid/react";
import type { CaptchaFlowComponent } from "./CaptchaRenderer";

interface Props {
  component: CaptchaFlowComponent;
  context: ComponentRenderContext;
}

export default function CloudflareTurnstile({ component, context }: Props) {
  const fieldRef = component.ref ?? component.id;
  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  const handleSuccess = (token: string) => {
    onInputChangeRef.current(fieldRef, token);
  };

  const handleError = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  const handleExpire = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  return (
    <Turnstile
      siteKey={component.siteKey ?? ""}
      onSuccess={handleSuccess}
      onError={handleError}
      onExpire={handleExpire}
      options={{
        theme: component.theme ?? "light",
        size: (component.size as "normal" | "compact" | "flexible") ?? "normal",
      }}
    />
  );
}
