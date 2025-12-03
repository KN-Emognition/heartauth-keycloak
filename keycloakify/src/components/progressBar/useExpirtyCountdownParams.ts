import { useEffect, useMemo, useState } from "react";

type UseExpiryCountdownParams = {
    ttl?: number; // time-to-live in seconds
    exp?: number; // absolute expiry timestamp in seconds (epoch)
    triggerKey: string | number; // something that changes per request (id, etc.)
};

export default function useExpiryCountdown({
    ttl,
    exp,
    triggerKey
}: UseExpiryCountdownParams) {
    const [remainingSeconds, setRemainingSeconds] = useState(0);

    useEffect(() => {
        const nowSeconds = Math.floor(Date.now() / 1000);
        const expRemaining = typeof exp === "number" ? exp - nowSeconds : undefined;

        const initialRemaining =
            expRemaining !== undefined && !Number.isNaN(expRemaining)
                ? Math.max(0, expRemaining)
                : Math.max(0, ttl ?? 0);

        setRemainingSeconds(initialRemaining);

        if (initialRemaining <= 0) {
            return;
        }

        const intervalId = window.setInterval(() => {
            setRemainingSeconds(prev => {
                if (prev <= 1) {
                    window.clearInterval(intervalId);
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);

        return () => window.clearInterval(intervalId);
    }, [ttl, exp, triggerKey]);

    const percentage = useMemo(() => {
        if (!ttl || ttl <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (remainingSeconds / ttl) * 100));
    }, [remainingSeconds, ttl]);

    const formattedTime = useMemo(() => {
        const total = remainingSeconds;
        const minutes = Math.floor(total / 60);
        const seconds = total % 60;
        return `${minutes}:${seconds.toString().padStart(2, "0")}`;
    }, [remainingSeconds]);

    const showCountdown = remainingSeconds > 0;

    return {
        remainingSeconds,
        formattedTime,
        percentage,
        showCountdown,
        isExpired: remainingSeconds <= 0
    };
}
