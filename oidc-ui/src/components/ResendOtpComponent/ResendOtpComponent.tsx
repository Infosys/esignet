import { useRef, useState, useEffect } from "react";
import { type ComponentRenderContext, useTranslation } from "@thunderid/react";
import { CaptchaComponent } from "../index";
import type { ResendOtpFlowComponent } from "./ResendOtpModel";

export default function ResendOtpComponent({
  component,
  context,
}: {
  component: ResendOtpFlowComponent;
  context: ComponentRenderContext;
}) {
  const { t } = useTranslation();
  const expiresIn = component?.timeLeft ?? 0;
  const captchaId = `${component.id}_captcha`;

  // The countdown is derived from a deadline rather than a ticking counter, so
  // restarting it is a single state update and never drifts.
  const [deadline, setDeadline] = useState<number>(
    () => Date.now() + expiresIn * 1000,
  );
  const [now, setNow] = useState<number>(() => Date.now());
  const [prevExpiresIn, setPrevExpiresIn] = useState<number>(expiresIn);
  const [showCaptcha, setShowCaptcha] = useState<boolean>(false);
  const [captchaError, setCaptchaError] = useState<boolean>(false);
  // Guards against a captcha token refresh re-triggering an in-flight resend.
  const submittingRef = useRef<boolean>(false);

  // Restart the countdown when the flow hands back a new expiry.
  if (prevExpiresIn !== expiresIn) {
    setPrevExpiresIn(expiresIn);
    startCountdown();
  }

  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));
  const timeLeft = remaining > 0;

  function startCountdown() {
    const from = Date.now();
    setNow(from);
    setDeadline(from + expiresIn * 1000);
  }

  useEffect(() => {
    if (!timeLeft) {
      return undefined;
    }

    const interval: ReturnType<typeof setInterval> = setInterval(
      () => setNow(Date.now()),
      1000,
    );

    return () => clearInterval(interval);
  }, [timeLeft, deadline]);

  const formatTime = (seconds: number): string => {
    if (seconds <= 0) {
      return t("app.otp.timed_out");
    }
    const m: number = Math.floor(seconds / 60);
    const s: number = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const submitResend = (captchaToken: string) => {
    if (!context.onSubmit || submittingRef.current) {
      return;
    }
    submittingRef.current = true;

    context.resetForm?.();
    context.onSubmit(
      component,
      component.captcha ? { captcha_token: captchaToken } : {},
      true,
    );

    // Collapse back to the countdown state for the new OTP.
    setShowCaptcha(false);
    setCaptchaError(false);
    startCountdown();
  };

  // Reveal the captcha first; sending happens once it is solved. Flows without
  // a captcha configured resend straight away.
  const handleResendClick = () => {
    submittingRef.current = false;

    if (!component.captcha) {
      submitResend("");
      return;
    }
    setCaptchaError(false);
    setShowCaptcha(true);
  };

  const handleCancel = () => {
    setShowCaptcha(false);
    setCaptchaError(false);
    context.onInputChange(captchaId, "");
  };

  const captchaChanged = (token: string | null | undefined) => {
    const solved = typeof token === "string" && token.trim().length > 0;
    setCaptchaError(!solved);

    if (solved) {
      submitResend(token.trim());
    }
  };

  return (
    <div className="resend-otp-panel">
      {timeLeft && (
        <p className="resend-otp-panel__text">
          {t("app.otp.resend_timer")} {formatTime(remaining)}
        </p>
      )}

      {!timeLeft && !showCaptcha && (
        <>
          <p className="resend-otp-panel__text">
            {t("otp.didnt_receive_otp")}
          </p>
          <button
            type="button"
            id={component.id}
            className="resend-otp-panel__link"
            onClick={handleResendClick}
            data-testid="thunderid-resend-otp-button"
          >
            {t(component?.label ?? "otp.resend_otp")}
          </button>
        </>
      )}

      {!timeLeft && showCaptcha && (
        <>
          <p className="resend-otp-panel__title">{t("otp.resend_otp")}</p>
          <p className="resend-otp-panel__text">
            {t("otp.resend_captcha_prompt")}
          </p>

          <div className="resend-otp-panel__captcha">
            <CaptchaComponent
              component={{ ...component, ref: captchaId }}
              context={context}
              key={captchaId}
              captchaChanged={captchaChanged}
            ></CaptchaComponent>
          </div>

          {captchaError && (
            <p className="resend-otp-panel__error">
              {t("validations.required.field.error")}
            </p>
          )}

          <button
            type="button"
            className="resend-otp-panel__link"
            onClick={handleCancel}
            data-testid="thunderid-resend-otp-cancel"
          >
            {t("button.cancel")}
          </button>
        </>
      )}
    </div>
  );
}
