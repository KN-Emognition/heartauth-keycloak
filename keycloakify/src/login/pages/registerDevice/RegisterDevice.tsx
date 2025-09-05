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

const TERMINAL: readonly FlowStatus[] = [
    "APPROVED",
    "DENIED",
    "EXPIRED",
    "NOT_FOUND"
] as const;

export default function RegisterDevice(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { url, qr, id, rootAuthSessionId, tabId, watchBase } = kcContext;

    const [status, setStatus] = useState<FlowStatus>("PENDING");
    const isTerminal = TERMINAL.includes(status);

    const finalizeFormRef = useRef<HTMLFormElement>(null);
    const submittedRef = useRef(false);

    useStatus({ setStatus, id, rootAuthSessionId, tabId, watchBase });

    useEffect(() => {
        if (isTerminal && !submittedRef.current) {
            submittedRef.current = true;
            finalizeFormRef.current?.submit();
        }
    }, [isTerminal]);

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
            <div className={styles.panel} aria-live="polite">
                {qr && (
                    <QRCode
                        style={{ height: "auto", width: "80%" }}
                        value={qr}
                    />
                )}
                <InfoBox hint={hint} type={alertType} />
                <InfoBox hint={qr} type={alertType} />
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
