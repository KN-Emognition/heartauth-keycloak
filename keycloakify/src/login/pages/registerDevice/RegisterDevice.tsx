import { useEffect, useMemo, useRef, useState } from "react";
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
import useCopyToClipboard from "../../../hooks/useCopyToClipboard.ts";
import useExpiryCountdown from "../../../components/progressBar/useExpirtyCountdownParams.ts";
import ProgressBar from "../../../components/progressBar/ProgressBar.tsx";

type Props = PageProps<Extract<KcContext, { pageId: "registerDevice.ftl" }>, I18n>;

export default function RegisterDevice(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url, qr, id, rootAuthSessionId, tabId, watchBase, ttl, exp } = kcContext;

    const [status, setStatus] = useState<FlowStatus>("PENDING");
    const isApproved = status === "APPROVED";
    const resendDisabled = status === "APPROVED";

    const finalizeFormRef = useRef<HTMLFormElement>(null);
    const submittedRef = useRef(false);

    useStatus({ setStatus, id, rootAuthSessionId, tabId, watchBase });

    const { handleCopy } = useCopyToClipboard();
    useEffect(() => {
        setStatus("PENDING");
        submittedRef.current = false;
    }, [id]);
    const { formattedTime, percentage: countdownPercentage } = useExpiryCountdown({
        ttl,
        exp,
        triggerKey: id
    });

    useEffect(() => {
        if (isApproved && !submittedRef.current) {
            submittedRef.current = true;
            finalizeFormRef.current?.submit();
        }
    }, [isApproved]);

    const hint = useMemo(() => {
        switch (status) {
            case "APPROVED":
                return labels.approved;
            case "DENIED":
                return labels.denied;
            case "EXPIRED":
            case "NOT_FOUND":
                return labels.expired ?? "Pairing expired or not found.";
            case "CREATED":
            case "PENDING":
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
            case "NOT_FOUND":
                return "warning";
            default:
                return "info";
        }
    }, [status]);

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
            <section className={styles.registerShell}>
                <span className={styles.artTop} aria-hidden="true"></span>
                <span className={styles.artBottom} aria-hidden="true"></span>
                <div className={styles.panel} aria-live="polite">
                    {qr && (
                        <div className={styles.qr}>
                            <QRCode
                                className={styles.qrCode}
                                value={qr}
                                size={220}
                                bgColor="#fff5f5" // soft brandy background
                                fgColor="#2a0a3d" // plum modules
                                level="M"
                                onClick={handleCopy(qr)}
                            />
                        </div>
                    )}
                    <InfoBox hint={hint} type={alertType} />
                    <ProgressBar percent={countdownPercentage} timeLeft={formattedTime} />
                </div>

                <form method="post" action={url.loginAction} className={styles.actions}>
                    <input type="hidden" name="resend" value="true" />
                    <button
                        type="submit"
                        className={kcClsx("kcButtonClass", "kcButtonPrimaryClass")}
                        disabled={resendDisabled}
                    >
                        {labels.resend ?? "Send a new pairing request"}
                    </button>
                </form>
            </section>

            <form
                ref={finalizeFormRef}
                method="post"
                action={url.loginAction}
                style={{ display: "none" }}
            />
        </Template>
    );
}
