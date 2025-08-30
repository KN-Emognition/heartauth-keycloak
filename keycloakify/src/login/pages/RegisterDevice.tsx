import { useMemo } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";
import styles from "../style/RegisterDevice.module.css";

type Props = PageProps<Extract<KcContext, { pageId: "registerDevice.ftl" }>, I18n>;

export default function RegisterDevice(props: Props) {
  const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
  const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

  // these attributes come from your Required Action (ctx.form().setAttribute(...))
  const { url, qr, sessionId } = kcContext;

  const text = useMemo(() => ({
    title: "Register device",
    hint: "Scan this QR code in your HeartAuth app. After pairing, click the button below.",
    paired: "I’ve paired my device",
    cancel: "Cancel",
  }), []);

  const Title = () => <h1 className={kcClsx("kcFormHeaderClass")}>{text.title}</h1>;

  return (
    <Template
      kcContext={kcContext}
      i18n={i18n}
      doUseDefaultCss={doUseDefaultCss}
      classes={classes}
      displayInfo={false}
      headerNode={<Title />}
    >
      <div className={styles.container}>
        <div className={styles.panel}>
          {/* PF info alert, matches verify-email vibe */}
          <div className="pf-c-alert pf-m-info" role="status" style={{ width: "100%" }}>
            <div className="pf-c-alert__title">{text.hint}</div>
          </div>

          <img src={qr} className={styles.qr} alt="Pairing QR code" width={260} height={260} />

          {/* Post back to the RA (processAction) */}
          <form method="post" action={url.loginAction} className={styles.actions}>
            <input type="hidden" name="session" value={sessionId ?? ""} />
            <button
              className={`${kcClsx("kcButtonClass")} ${kcClsx("kcButtonPrimaryClass")}`}
              type="submit"
              name="confirm"
              value="1"
            >
              {text.paired}
            </button>
            <button
              className={kcClsx("kcButtonClass")}
              type="submit"
              name="cancel"
              value="1"
            >
              {text.cancel}
            </button>
          </form>
        </div>
      </div>
    </Template>
  );
}
