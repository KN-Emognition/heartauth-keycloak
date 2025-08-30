import { useEffect } from "react";
import { FlowStatus } from "../types/FlowStatus";
import { StatusResponse } from "../types/StatusReponse";

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
        if (!watchBase) return;

        const params = new URLSearchParams({
            root: rootAuthSessionId ?? "",
            tab: tabId ?? "",
            id: challengeId ?? ""
        });
        const watchUrl = `${watchBase}?${params.toString()}`;

        const handle = async (p: StatusResponse) => {
            const st: FlowStatus = p.status ?? "PENDING";
            setStatus(st);
        };

        let eventSource!: EventSource;
        const start = () => {
            try {
                eventSource = new EventSource(watchUrl);
                eventSource.onmessage = event => {
                    const payload =
                        typeof event.data === "string"
                            ? JSON.parse(event.data)
                            : event.data;
                    handle(payload);
                };
                eventSource.onerror = () => {
                    eventSource?.close();
                    setTimeout(start, 1500);
                };
            } catch {
                setTimeout(start, 1500);
            }
        };

        start();
        return () => {
            eventSource?.close();
        };
    }, [challengeId, rootAuthSessionId, tabId, watchBase]);
};

export default useStatus;
