import { useEffect, useState } from "react";
import { Progress } from "antd";
import icon from "../../assets/illustrations/intro-card.svg";
import styles from "./ProgressBar.module.css";

type Props = {
    percent: number;
    timeLeft?: string;
};

export default function GradientCircle({ percent, timeLeft }: Props) {
    const [pulse, setPulse] = useState(false);

    useEffect(() => {
        if (!timeLeft) return;
        setPulse(true);
        const timer = setTimeout(() => setPulse(false), 2000);
        return () => clearTimeout(timer);
    }, [timeLeft]);

    return (
        <Progress
            trailColor="#c2a9a9ff"
            type="dashboard"
            percent={percent}
            strokeWidth={10}
            strokeColor={{
                "0%": "#b31d1d",
                "100%": "#2a0a3d"
            }}
            size={150}
            format={() => (
                <div
                    style={{
                        textAlign: "center",
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center"
                    }}
                >
                    <img
                        src={icon}
                        alt="Progress Icon"
                        className={`${styles.progressIcon} ${
                            pulse ? styles.progressIconPulse : ""
                        }`}
                    />
                    <div
                        style={{
                            fontSize: 24,
                            fontWeight: "bold",
                            color: "#b31d1d"
                        }}
                    >
                        {timeLeft}
                    </div>
                </div>
            )}
        />
    );
}
