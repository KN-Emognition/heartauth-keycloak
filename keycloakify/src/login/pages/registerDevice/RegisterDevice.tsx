// RegisterDevice.tsx
import { useState, useMemo, useEffect, useRef } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import styles from "./RegisterDevice.module.css";
import type { KcContext } from "../../../kc.gen";
import { I18n } from "keycloakify/login/i18n";
import labels from "./labels.json";
import QRCode from "react-qr-code";
import InfoBox from "../../../components/InfoBox/InfoBox";
import useStatus from "../../../hooks/useStatus";
import type { FlowStatus } from "../../../types/FlowStatus";

type Props = PageProps<Extract<KcContext, { pageId: "registerDevice.ftl" }>, I18n>;

export default function RegisterDevice(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url, qr, id, rootAuthSessionId, tabId, watchBase } = kcContext;

    const [status, setStatus] = useState<FlowStatus>("PENDING");
    const confirmFormRef = useRef<HTMLFormElement>(null);

    useStatus({ setStatus, id, rootAuthSessionId, tabId, watchBase });

    useEffect(() => {
        if (status === "APPROVED") {
            confirmFormRef.current?.submit();
        }
    }, [status]);

    const hint = useMemo(() => {
        switch (status) {
            case "APPROVED":
                return labels.approved;
            case "DENIED":
                return labels.denied;
            case "EXPIRED":
            default:
                return labels.hint;
        }
    }, [status]);

    const alertType = useMemo(() => {
        switch (status) {
            case "APPROVED":
                return "success";
            case "DENIED":
                return "danger";
            case "EXPIRED":
                return "warning";
            default:
                return "info";
        }
    }, [status]);

    const showRestart = status !== "PENDING" && status !== "APPROVED";

    const Title = () => (
        <div>
            <h1 className={kcClsx("kcFormHeaderClass")}>{labels.title}</h1>
        </div>
    );

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayInfo={false}
            headerNode={<Title />}
        >
            <div className={styles.panel} aria-live="polite">
                {qr && <QRCode style={{ height: "auto", width: "80%" }} value={qr} />}
                <InfoBox hint={hint} type={alertType} />

                {status === "APPROVED" && (
                    <button
                        type="button"
                        className={kcClsx("kcButtonClass", "kcButtonPrimaryClass")}
                        onClick={() => confirmFormRef.current?.submit()}
                    >
                        Continue
                    </button>
                )}

                {showRestart && (
                    <>
                        {url.loginRestartFlowUrl ? (
                            <a
                                className={kcClsx(
                                    "kcButtonClass",
                                    "kcButtonPrimaryClass"
                                )}
                                href={url.loginRestartFlowUrl}
                            >
                                Restart login
                            </a>
                        ) : (
                            <form method="post" action={url.loginAction}>
                                <input type="hidden" name="cancel" value="1" />
                                <button
                                    className={kcClsx(
                                        "kcButtonClass",
                                        "kcButtonPrimaryClass"
                                    )}
                                    type="submit"
                                >
                                    Restart login
                                </button>
                            </form>
                        )}
                    </>
                )}
            </div>
            <form
                ref={confirmFormRef}
                method="post"
                action={url.loginAction}
                style={{ display: "none" }}
            >
                <input type="hidden" name="confirm" value="1" />
            </form>
        </Template>
    );
}
