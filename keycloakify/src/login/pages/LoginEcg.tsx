import { useEffect, useRef, useState, useMemo } from "react";
import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import type { I18n } from "../i18n";
import type { KcContext } from "../KcContext";
import hauthLogo from "../assets/hauth_logo.png";
import styles from "../style/LoginEcg.module.css";

type EcgState = "PENDING" | "APPROVED" | "DENIED" | "EXPIRED" | "NOT_FOUND";
// at the top of your component (near other refs/constants)
const sleep = (ms: number) => new Promise(res => setTimeout(res, ms));

const EXTRA_STATUS_MS = 2500; // show the terminal status ("Approved…", "Denied", …) before redirect

export default function LoginEcg(props: PageProps<Extract<KcContext, { pageId: "ecg.ftl" }>, I18n>) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });
    const { url, challengeId, rootAuthSessionId, tabId, pollMs = 2000, watchBase } = kcContext;
    const firstPendingAtRef = useRef<number | null>(Date.now()); // when user first saw "PENDING"
    const isFinalizingRef = useRef(false);
    const finalizeRef = useRef<HTMLFormElement>(null);
    const statusRef = useRef<HTMLDivElement>(null);

    const [ecgState, setEcgState] = useState<EcgState>("PENDING");

    const stateCopy = useMemo(() => {
        switch (ecgState) {
            case "APPROVED":
                return { title: "Approved", desc: "Continuing your sign-in…" };
            case "DENIED":
                return { title: "Denied", desc: "Request was denied. You can restart the login." };
            case "EXPIRED":
            case "NOT_FOUND":
                return { title: "Request expired", desc: "This approval is no longer valid. Please restart." };
            default:
                return {
                    title: "Approve sign-in",
                    desc: "Approve the request in your HeartAuth app. We’ll continue automatically."
                };
        }
    }, [ecgState]);

    const alertMod = useMemo(() => {
        switch (ecgState) {
            case "APPROVED":
                return "pf-m-success";
            case "DENIED":
                return "pf-m-danger";
            case "EXPIRED":
            case "NOT_FOUND":
                return "pf-m-warning";
            default:
                return "pf-m-info";
        }
    }, [ecgState]);

    useEffect(() => {
        if (!watchBase) return;

        const watchUrl =
            `${watchBase}/watch?root=${encodeURIComponent(rootAuthSessionId ?? "")}` +
            `&tab=${encodeURIComponent(tabId ?? "")}` +
            `&challengeId=${encodeURIComponent(challengeId ?? "")}` +
            `&pollMs=${encodeURIComponent(String(pollMs))}`;

        const setStatus = (s: string) => {
            if (statusRef.current) statusRef.current.textContent = s;
        };
        const finalizeOnce = () => {
            finalizeRef.current?.requestSubmit?.() ?? finalizeRef.current?.submit();
        };

        const handle = async (p: any) => {
            const st: EcgState = (p && p.state) || "PENDING";
            setEcgState(st);

            // track when PENDING started (for minimum visible time)
            if (st === "PENDING") {
                if (firstPendingAtRef.current === null) {
                    firstPendingAtRef.current = Date.now();
                }
                setStatus("Waiting for approval…");
                return;
            }


            // set final status line
            if (st === "APPROVED") {
                setStatus("Approved. Continuing…");
            } else if (st === "DENIED") {
                setStatus("Denied");
            } else if (st === "EXPIRED" || st === "NOT_FOUND") {
                setStatus("Challenge expired.");
            }

            if (isFinalizingRef.current) return;
            isFinalizingRef.current = true;

            // pause so users can read statuses
            await sleep(EXTRA_STATUS_MS);

            // finalize
            finalizeOnce();
        };

        let es: EventSource | undefined;
        const start = () => {
            try {
                es = new EventSource(watchUrl);
                es.onmessage = e => {
                    try {
                        handle(JSON.parse(e.data));
                    } catch {}
                };
                es.onerror = () => {
                    try {
                        es?.close();
                    } catch {}
                    setTimeout(start, 1500);
                };
            } catch {
                setTimeout(start, 1500);
            }
        };

        start();
        return () => {
            try {
                es?.close();
            } catch {}
        };
    }, [challengeId, rootAuthSessionId, tabId, pollMs, watchBase]);

    const Title = () => <h1 className={kcClsx("kcFormHeaderClass")}>{stateCopy.title}</h1>;

    const StatusAlert = () => (
        <div className={`pf-c-alert ${alertMod}`} role={ecgState === "APPROVED" ? "status" : "alert"}>
            <div className="pf-c-alert__title">{stateCopy.title}</div>
            <div className="pf-c-alert__description">{stateCopy.desc}</div>
        </div>
    );

    const Body = () => (
        <div className={styles.ecgPanel}>
            <img src={hauthLogo} width={200} alt="HeartAuth logo" className={styles.ecgLogo} />
            <StatusAlert />
            {challengeId && <p className={styles.ecgDebug}>{challengeId}</p>}
        </div>
    );

    // const Actions = () => (
    //     <div className={styles.ecgActions}>
    //         <form method="post" action={url.loginRestartFlowUrl}>
    //             <button className={`${kcClsx("kcButtonClass")} ${kcClsx("kcButtonPrimaryClass")}`} name="cancel" value="1" type="submit">
    //                 Cancel
    //             </button>
    //         </form>
    //         {(ecgState === "DENIED" || ecgState === "EXPIRED" || ecgState === "NOT_FOUND") && (
    //             <a href={url.loginRestartFlowUrl} className={styles.ecgLinkRestart}>
    //                 Restart login
    //             </a>
    //         )}
    //     </div>
    // );

    return (
        <Template kcContext={kcContext} i18n={i18n} doUseDefaultCss={doUseDefaultCss} classes={classes} displayInfo={false} headerNode={<Title />}>
            <Body />
            <form ref={finalizeRef} method="post" action={url.loginAction} style={{ display: "none" }}>
                <input type="hidden" name="finalize" value="1" />
            </form>
            {/* <Actions /> */}
        </Template>
    );
}
