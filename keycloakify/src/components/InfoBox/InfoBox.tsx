import hauthLogo from "../../assets/hauth_logo.png";
import styles from "./InfoBox.module.css";
interface InfoBoxProps {
    hint: string;
    type?: "success" | "danger" | "warning" | "info";
}
const InfoBox = ({ hint, type }: InfoBoxProps) => {
    return (
        <div
            className={`pf-c-alert pf-m-${type || "info"} ${styles.infoBox}`}
            role="status"
            style={{ width: "100%" }}
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
