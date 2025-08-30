import type { ExtendKcContext } from "keycloakify/login";
import type { KcEnvName, ThemeName } from "../kc.gen";
import { RegisterDeviceMeta } from "./pages/registerDevice/meta";
import { EcgMeta } from "./pages/ecg/meta";
export type KcContextExtension = {
    themeName: ThemeName;
    properties: Record<KcEnvName, string> & {};
};

export type KcContextExtensionPerPage = {
    "ecg.ftl": EcgMeta;
    "registerDevice.ftl": RegisterDeviceMeta;
};

export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>;
