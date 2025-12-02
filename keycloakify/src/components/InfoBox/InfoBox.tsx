import hauthLogo from "../../assets/hauth_logo.png";
import styles from "./InfoBox.module.css";

interface InfoBoxProps {
    hint: string;
    type?: "success" | "danger" | "warning" | "info";
    onClick?: () => void;
}

const InfoBox = ({hint, type, onClick}: InfoBoxProps) => {
    const isClickable = typeof onClick === "function";

    return (
        <div
            onClick={onClick}
            className={`pf-c-alert pf-m-${type || "info"} ${styles.infoBox}`}
            role="status"
            style={{width: "100%"}}
            data-clickable={isClickable || undefined}
        >
            <img
                src={hauthLogo}
                alt="HeartAuth Logo"
                width={50}
                className={`${styles.icon}`}
            />
            <div className={`pf-c-alert__title ${styles.hint}`}>{hint}</div>
        </div>
    );
};

export default InfoBox;
