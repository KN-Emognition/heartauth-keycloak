import { useState, useMemo, useRef, useEffect } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import { I18n } from "keycloakify/login/i18n";
import { FlowStatus } from "../../../types/FlowStatus";
import InfoBox from "../../../components/InfoBox/InfoBox";
import styles from "./Ecg.module.css";
import { KcContext } from "../../KcContext";
import useStatus from "../../../hooks/useStatus";

type Props = PageProps<Extract<KcContext, { pageId: "ecg.ftl" }>, I18n>;

const TERMINAL: readonly FlowStatus[] = [
    "APPROVED",
    "DENIED",
    "EXPIRED",
    "NOT_FOUND"
] as const;

export default function LoginEcg(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });
    const { url, id, rootAuthSessionId, tabId, watchBase } = kcContext;

    const [status, setStatus] = useState<FlowStatus>("PENDING");
    const isTerminal = TERMINAL.includes(status);

    const finalizeFormRef = useRef<HTMLFormElement>(null);
    const submittedRef = useRef(false);

    useEffect(() => {
        if (isTerminal && !submittedRef.current) {
            submittedRef.current = true;
            finalizeFormRef.current?.submit();
        }
    }, [isTerminal]);

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
                return "Request was denied. Finishing up…";
            case "EXPIRED":
            case "NOT_FOUND":
                return "Request is no longer valid. Finishing up…";
            case "CREATED":
            case "PENDING":
            default:
                return "Approve the request in your HeartAuth app. We’ll continue automatically.";
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
            headerNode={<h1 className={kcClsx("kcFormHeaderClass")}>ECG Authentication</h1>}
        >
            <div className={styles.ecgPanel}>
                <InfoBox hint={hint} type={alertType} />
                <InfoBox hint={`Challenge ID: ${id}`} type="warning" />
            </div>
            <form
                ref={finalizeFormRef}
                method="post"
                action={url.loginAction}
                style={{ display: "none" }}
            />
        </Template>
    );
}
