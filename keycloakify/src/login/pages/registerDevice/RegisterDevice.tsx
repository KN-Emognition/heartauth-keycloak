import type { PageProps } from "keycloakify/login/pages/PageProps";
import { getKcClsx } from "keycloakify/login/lib/kcClsx";
import styles from "./RegisterDevice.module.css";
import { KcContext } from "../../../kc.gen";
import { I18n } from "keycloakify/login/i18n";
import labels from "./labels.json";
import QRCode from "react-qr-code";
import InfoBox from "../../../components/InfoBox/InfoBox";
type Props = PageProps<
    Extract<KcContext, { pageId: "registerDevice.ftl" }>,
    I18n
>;

export default function RegisterDevice(props: Props) {
    const { kcContext, i18n, doUseDefaultCss, Template, classes } = props;
    const { kcClsx } = getKcClsx({ doUseDefaultCss, classes });

    const { qr } = kcContext;

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
            <div className={styles.panel}>
                {qr && <QRCode style={{ height: "auto", width: "80%" }} value={qr} />}
                <InfoBox hint={labels.hint} />
            </div>
        </Template>
    );
}
