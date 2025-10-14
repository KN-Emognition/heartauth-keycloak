import {useEffect} from "react";
import {FlowStatus} from "../types/FlowStatus";
import {StatusResponse} from "../types/StatusReponse";

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

const RETRY_BASE_DELAY = 1500;
const RETRY_MAX_DELAY = 8000;

export default ({
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
        let stopped = false;
        let retryDelay = RETRY_BASE_DELAY;

        const clearSource = () => {
            if (eventSource) {
                eventSource.close();
                eventSource = undefined;
            }
        };

        const stop = () => {
            stopped = true;
            clearSource();
        };

        const scheduleReconnect = () => {
            if (stopped) return;
            const delay = retryDelay;
            retryDelay = Math.min(retryDelay * 2, RETRY_MAX_DELAY);
            window.setTimeout(() => {
                if (!stopped) start();
            }, delay);
        };

        const handle = (p: StatusResponse | undefined) => {
            const st = (p?.status as FlowStatus) ?? "PENDING";
            setStatus(st);
            if (TERMINAL.includes(st)) {
                stop();
            }
        };

        const start = () => {
            if (stopped) return;
            clearSource();
            try {
                const source = new EventSource(watchUrl);
                eventSource = source;

                source.onopen = () => {
                    retryDelay = RETRY_BASE_DELAY;
                };

                source.onmessage = event => {
                    try {
                        const payload =
                            typeof event.data === "string"
                                ? (JSON.parse(event.data) as StatusResponse)
                                : (event.data as StatusResponse);
                        handle(payload);
                    } catch {
                        // Default to PENDING when payload cannot be parsed.
                        handle({status: "PENDING"} as StatusResponse);
                    }
                };

                source.onerror = () => {
                    clearSource();
                    scheduleReconnect();
                };
            } catch {
                scheduleReconnect();
            }
        };

        start();

        return stop;
    }, [challengeId, rootAuthSessionId, tabId, watchBase, setStatus]);
};
