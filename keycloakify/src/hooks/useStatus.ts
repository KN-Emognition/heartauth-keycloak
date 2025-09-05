import { useEffect } from "react";
import { FlowStatus } from "../types/FlowStatus";
import { StatusResponse } from "../types/StatusReponse";

const TERMINAL: readonly FlowStatus[] = [
    "APPROVED",
    "DENIED",
    "EXPIRED",
    "NOT_FOUND"
] as const;

interface Props {
    id: string;
    rootAuthSessionId: string;
    tabId: string;
    watchBase: string;
    setStatus: (status: FlowStatus) => void;
}

const useStatus = ({
    id: challengeId,
    rootAuthSessionId,
    tabId,
    watchBase,
    setStatus
}: Props) => {
    useEffect(() => {
        if (!watchBase || !rootAuthSessionId || !tabId || !challengeId) return;

        const params = new URLSearchParams({
            root: rootAuthSessionId,
            tab: tabId,
            id: challengeId
        });
        const watchUrl = `${watchBase}?${params.toString()}`;

        let eventSource: EventSource | undefined;

        const handle = (p: StatusResponse | undefined) => {
            const st = (p?.status as FlowStatus) ?? "PENDING";
            setStatus(st);
            if (TERMINAL.includes(st)) {
                eventSource?.close();
            }
        };

        const start = () => {
            try {
                eventSource = new EventSource(watchUrl);

                eventSource.onmessage = event => {
                    try {
                        const payload =
                            typeof event.data === "string"
                                ? (JSON.parse(event.data) as StatusResponse)
                                : (event.data as StatusResponse);
                        handle(payload);
                    } catch {
                        // If bad payload, keep connection but default to PENDING
                        handle({ status: "PENDING" } as StatusResponse);
                    }
                };

                eventSource.onerror = () => {
                    eventSource?.close();
                    setTimeout(() => {
                        if (eventSource && (eventSource as any).readyState === 2) {
                            start();
                        } else {
                            start();
                        }
                    }, 1500);
                };
            } catch {
                setTimeout(start, 1500);
            }
        };

        start();

        return () => {
            eventSource?.close();
        };
    }, [challengeId, rootAuthSessionId, tabId, watchBase, setStatus]);
};

export default useStatus;
