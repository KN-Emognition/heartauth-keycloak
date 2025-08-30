/* eslint-disable @typescript-eslint/no-empty-object-type */
import type { ExtendKcContext } from "keycloakify/login";
import type { KcEnvName, ThemeName } from "../kc.gen";

export type KcContextExtension = {
  themeName: ThemeName;
  properties: Record<KcEnvName, string> & {};
};

// ECG-only fields, scoped to the new page id
export type KcContextExtensionPerPage = {
  "ecg.ftl": {
    challengeId?: string;
    rootAuthSessionId?: string;
    tabId?: string;
    pollMs?: number;
    watchBase?: string;
  };
  "registerDevice.ftl": {
    qr?: string;
    sessionId?: string;
  };
};

export type KcContext = ExtendKcContext<KcContextExtension, KcContextExtensionPerPage>;
