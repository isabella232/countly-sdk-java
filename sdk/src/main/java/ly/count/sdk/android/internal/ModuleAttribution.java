package ly.count.sdk.android.internal;

import android.content.BroadcastReceiver;
import android.content.Intent;

import ly.count.sdk.android.Config;
import ly.count.sdk.internal.CtxCore;
import ly.count.sdk.internal.InternalConfig;
import ly.count.sdk.internal.Log;
import ly.count.sdk.internal.Module;
import ly.count.sdk.internal.ModuleRequests;
import ly.count.sdk.internal.Request;
import ly.count.sdk.internal.Tasks;

/**
 * Attribution plugin implementation:
 * <ul>
 *     <li>Get Advertising ID from {@link ModuleDeviceId#acquireId(CtxCore, Config.DID, boolean, Tasks.Callback)}, save it in {@link InternalConfig}</li>
 *     <li>Implement {@link android.content.BroadcastReceiver} listening & decoding for INSTALL_REFERRER broadcast</li>
 *     <li>Sends corresponding requests if needed</li>
 * </ul>
 */

public class ModuleAttribution extends ModuleBase {
    private static final Log.Module L = Log.module("ModuleAttribution");

    public static final String ACTION = "com.android.vending.INSTALL_REFERRER";
    public static final String EXTRA = "referrer";
    public static final String CLY_CID = "countly_cid";
    public static final String CLY_UID = "countly_cuid";
    public static final String CLY_AID = "adid";

    private InternalConfig config;
    private boolean advertisingIdNotAvailable = false;
    private String adid = null;

    public static class AttributionReferrerReceiver extends BroadcastReceiver {
        private ly.count.sdk.android.internal.Ctx receiveiverCtx = null;

        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            final PendingResult[] pendingResult = new PendingResult[1];
            if (Device.dev.API(11)) {
                pendingResult[0] = goAsync();
            }

            //receiveiverCtx = SDK.instance.config;// ctx(context);
            receiveiverCtx = new CtxImpl(SDK.instance, SDK.instance.config, context);
            if (receiveiverCtx == null) {
                return;
            }

            L.d("It's " + ACTION + " broadcast");
            if (intent == null || !ACTION.equals(intent.getAction()) || !intent.hasExtra(EXTRA)) {
                return;
            }

            String referrer = Utils.urldecode(intent.getStringExtra(EXTRA));
            L.d("Referrer is " + referrer);

            if (Utils.isNotEmpty(referrer)) {
                extractReferrer(context, pendingResult, referrer);
            }
        }

        public void extractReferrer(android.content.Context context, final PendingResult[] pendingResult, String referrer) {
            if (referrer == null) {
                return;
            }
            String parts[] = referrer.split("&");
            String cid = null;
            String uid = null;
            for (String part : parts) {
                if (part.startsWith(CLY_CID)) {
                    cid = part.substring(CLY_CID.length() + 1).trim();
                }
                if (part.startsWith(CLY_UID)) {
                    uid = part.substring(CLY_UID.length() + 1).trim();
                }
            }
            L.i("Extracted Countly referrer: cid " + cid + " / uid " + uid);

            if (Utils.isNotEmpty(cid) || Utils.isNotEmpty(uid)) {
                // request won't be sent until advertising id is acquired
                Request request = recordRequest(cid, uid);

                ModuleRequests.pushAsync(SDK.instance.ctx(context), request, new Tasks.Callback<Boolean>() {
                    @Override
                    public void call(Boolean success) throws Exception {
                        L.i("Done adding request: " + (success ? "success" : "failure"));
                        if (Device.dev.API(11)) {
                            pendingResult[0].finish();
                        }
                    }
                });
            }
        }

        public Request recordRequest(String cid, String uid) {
            Request request = ModuleRequests.nonSessionRequest(receiveiverCtx);
            request.params.add("campaign_id", cid, "campaign_user", uid);
            request.own(ModuleAttribution.class);
            return request;
        }
    }

    @Override
    public void init(InternalConfig config) {
        this.config = config;
    }

    /**
     * Getting {@link Config.DeviceIdRealm#ADVERTISING_ID} procedure.
     * Works in line with {@link Module#onDeviceId(CtxCore, Config.DID, Config.DID)} in some cases.
     */
    @Override
    public void onContextAcquired(final CtxCore ctx) {
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.ADVERTISING_ID.getIndex()) {
            L.d("waiting for ModuleDeviceIdCore to finish acquiring ADVERTISING_ID");
        } else {
            Config.DID did = config.getDeviceId();
            Config.DID adv = config.getDeviceId(Config.DeviceIdRealm.ADVERTISING_ID.getIndex());
            if (adv == null) {
                if (did != null && did.strategy == Config.DeviceIdStrategy.ADVERTISING_ID.getIndex()) {
                    L.i("setting id from ADVERTISING_ID device id");
                    adv = new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID.getIndex(), Config.DeviceIdStrategy.ADVERTISING_ID.getIndex(), did.id);
                    SDK.instance.onDeviceId(ctx, adv, null);
                } else {
                    L.d("getting ADVERTISING_ID");
                    Config.DID newDid = new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID.getIndex(), Config.DeviceIdStrategy.ADVERTISING_ID.getIndex(), null);
                    SDK.instance.acquireId(ctx, newDid, false, new Tasks.Callback<Config.DID>() {
                        @Override
                        public void call(Config.DID param) throws Exception {
                            if (param != null && param.id != null) {
                                L.i("getting ADVERTISING_ID succeeded: " + param);
                                SDK.instance.onDeviceId(ctx, param, null);
                            } else {
                                L.w("no ADVERTISING_ID available, Countly Attribution is unavailable");
                                advertisingIdNotAvailable = true;
                            }
                        }
                    });
                }
            } else {
                L.d("acquired ADVERTISING_ID previously");
                SDK.instance.onDeviceId(ctx, adv, adv);
            }
        }
    }

    /**
     * Getting {@link Config.DeviceIdRealm#ADVERTISING_ID} procedure.
     * Works in line with {@link #onContextAcquired(CtxCore)}} in some cases.
     */
    @Override
    public void onDeviceId(CtxCore ctx, Config.DID deviceId, Config.DID oldDeviceId) {
        if (config.getDeviceIdStrategy() == Config.DeviceIdStrategy.ADVERTISING_ID.getIndex() && deviceId != null && deviceId.realm == Config.DeviceIdRealm.DEVICE_ID.getIndex()) {
            if (deviceId.strategy == Config.DeviceIdStrategy.ADVERTISING_ID.getIndex()) {
                L.d("waiting for ModuleDeviceIdCore to finish acquiring ADVERTISING_ID done: " + deviceId);
                SDK.instance.onDeviceId(ctx, new Config.DID(Config.DeviceIdRealm.ADVERTISING_ID.getIndex(), Config.DeviceIdStrategy.ADVERTISING_ID.getIndex(), deviceId.id), null);
            } else {
                L.w("no ADVERTISING_ID available, Countly Attribution is unavailable after ModuleDeviceIdCore flow");
                advertisingIdNotAvailable = true;
            }
        } else if (deviceId != null && deviceId.realm == Config.DeviceIdRealm.ADVERTISING_ID.getIndex()) {
            adid = deviceId.id;
        }
    }

    @Override
    public Boolean onRequest(Request request) {
        if (adid != null) {
            request.params.add(CLY_AID, adid);
            return true;
        } else if (advertisingIdNotAvailable) {
            return false;
        } else {
            return null;
        }
    }

    @Override
    public Integer getFeature() {
        return Config.Feature.Attribution.getIndex();
    }
}
