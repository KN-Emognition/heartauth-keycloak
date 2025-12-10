import { useEffect, useMemo, useRef, useState } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import { I18n } from "keycloakify/login/i18n";
import { FlowStatus } from "../../../types/FlowStatus";
import InfoBox from "../../../components/InfoBox/InfoBox";
import styles from "./Ecg.module.css";
import { KcContext } from "../../KcContext";
import useStatus from "../../../hooks/useStatus";
import useCopyToClipboard from "../../../hooks/useCopyToClipboard.ts";
import ProgressBar from "../../../components/progressBar/ProgressBar.tsx";
import useExpiryCountdown from "../../../components/progressBar/useExpirtyCountdownParams.ts";

type Props = PageProps<Extract<KcContext, { pageId: "ecg.ftl" }>, I18n>;

export default function LoginEcg(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });
    const { url, id, rootAuthSessionId, tabId, watchBase, exp, ttl } = kcContext;
    const { handleCopy } = useCopyToClipboard();
    const [status, setStatus] = useState<FlowStatus>("PENDING");
    const isApproved = status === "APPROVED";

    const finalizeFormRef = useRef<HTMLFormElement>(null);
    const submittedRef = useRef(false);

    useEffect(() => {
        setStatus("PENDING");
        submittedRef.current = false;
    }, [id]);

    const { formattedTime, percentage: countdownPercentage } = useExpiryCountdown({
        ttl,
        exp,
        triggerKey: id
    });

    const resendDisabled = status === "APPROVED";

    useEffect(() => {
        if (isApproved && !submittedRef.current) {
            submittedRef.current = true;
            finalizeFormRef.current?.submit();
        }
    }, [isApproved]);

    useStatus({
        setStatus,
        id,
        rootAuthSessionId,
        tabId,
        watchBase
    });

    const hint = useMemo(() => {
        switch (status) {
            case "APPROVED":
                return "Continuing your sign-in…";
            case "DENIED":
                return "Request was denied. You can send a new request below.";
            case "EXPIRED":
            case "NOT_FOUND":
                return "Request is no longer valid. Send a new request below.";
            case "CREATED":
            case "PENDING":
            default:
                return "Approve the request in your HeartAuth app or resend a new one below. We’ll continue automatically.";
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

    return (
        <Template
            kcContext={kcContext}
            i18n={i18n}
            doUseDefaultCss={doUseDefaultCss}
            classes={classes}
            displayInfo={false}
            headerNode={
                <h1 className={kcClsx("kcFormHeaderClass")}>HeartAuth Challenge</h1>
            }
        >
            <section className={styles.ecgShell}>
                <span className={styles.sparkTop} aria-hidden="true"></span>
                <span className={styles.sparkBottom} aria-hidden="true"></span>
                <div className={styles.ecgPanel}>
                    <InfoBox hint={hint} type={alertType} onClick={handleCopy(id)} />
                    {alertType === "info" && (
                        <ProgressBar
                            percent={countdownPercentage}
                            timeLeft={formattedTime}
                        />
                    )}
                    <form
                        method="post"
                        action={url.loginAction}
                        className={styles.resendForm}
                    >
                        <input type="hidden" name="resend" value="true" />
                        <button
                            type="submit"
                            className={kcClsx("kcButtonClass", "kcButtonPrimaryClass")}
                            disabled={resendDisabled}
                        >
                            Send a new authentication request
                        </button>
                    </form>
                </div>
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
