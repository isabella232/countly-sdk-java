package ly.count.sdk.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ly.count.sdk.Cly;
import ly.count.sdk.Usage;
import ly.count.sdk.android.internal.Ctx;
import ly.count.sdk.android.internal.CtxImpl;
import ly.count.sdk.android.internal.ModuleRating;
import ly.count.sdk.android.internal.SDK;
import ly.count.sdk.android.internal.Utils;
import ly.count.sdk.Crash;
import ly.count.sdk.CrashProcessor;
import ly.count.sdk.Event;
import ly.count.sdk.Session;
import ly.count.sdk.User;
import ly.count.sdk.UserEditor;
import ly.count.sdk.internal.ModuleRemoteConfig;

/**
 * Main Countly SDK API class.
 * <ul>
 *     <li>Initialize Countly SDK using {@code #init(Application, Config)}.</li>
 *     <li>Stop Countly SDK with {@link #stop(Context, boolean)} if needed.</li>
 *     <li>Call {@link #onActivityCreated(Activity, Bundle)}, {@link #onActivityStarted(Activity)} {@link #onActivityStopped(Activity)} if targeting API levels < 14.</li>
 *     <li>Use {@link #session(Context)} to get a {@link Session} instance.</li>
 *     <li>Use {@link #login(Context, String)} & {@link #logout(Context)} when user logs in & logs out.</li>
 * </ul>
 */

public class Countly extends CountlyLifecycle {

    protected static Countly cly;
    protected SDK sdk;

    //needed for testing
    private Countly(){}

    protected Countly(SDK sdk, Ctx ctx) {
        super();
        cly = this;
        super.sdkInterface = this.sdk = sdk;
        this.ctx = ctx;
    }

    private static Ctx ctx(Context context) {
        return new CtxImpl(cly.sdk, cly.sdk.config(), context);
    }

    /**
     * Returns active {@link Session} if any or creates new {@link Session} instance.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link Config.DID} changes.
     * {@link Session} also holds application context.
     * So either do not store {@link Session} instances in any static variables and use this method or {@link #getSession()} every time you need it,
     * or check {@link Session#isActive()} before using it.
     *
     * @param context current Android Ctx
     * @return active {@link Session} instance
     */
    public static Session session(Context context){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return Cly.session(ctx(context));
    }



    /**
     * Returns active {@link Session} if any or {@code null} otherwise.
     *
     * NOTE: {@link Session} instances can expire, for example when {@link Config.DID} changes.
     * {@link Session} also holds application context.
     * So either do not store {@link Session} instances in any static variables and use this method or {@link #session(Context)} every time you need it,
     * or check {@link Session#isActive()} before using it.
     *
     * @return active {@link Session} instance if there is one, {@code null} otherwise
     */
    public static Session getSession(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return Cly.getSession();
    }

    /**
     * Shorthand method for {@code Countly.session(context).event(key)}.
     *
     * Creates new event instance with supplied key, count=1 and empty other properties.
     * Returned {@link Event} instance can be used to add {@link Event#addSegment(String, String)},
     * {@link Event#setCount(int)}, {@link Event#setSum(double)}, {@link Event#setDuration(double)} .
     *
     * NOTE: Event won't be recorded until you call {@link Event#record()}.
     * NOTE: This method automatically creates new {@link Session} if none exists, however
     * it doesn't {@link Session#begin()}s it. But that session will be began once you call {@link Event#record()}.
     *
     * @param context Ctx to run in
     * @param key event key
     * @return {@link Event} instance
     */
    public static Event event(Context context, String key) {
        return session(context).event(key);
    }

    public static User user(Context context) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        }
        return session(ctx(context)).user();
    }

//    /**
//     * Token refresh callback to be called from {@code FirebaseInstanceIdService} whenever new token is acquired.
//     *
//     * @param service context to run in (supposed to be called from {@code FirebaseInstanceIdService})
//     * @param token String token to be sent to Countly server
//     */
//    public static void onFirebaseToken(Service service, String token) {
//        if (!isInitialized()) {
//            L.wtf("Countly SDK is not initialized yet.");
//        } else {
//            Core.onPushTokenRefresh(service, token);
//        }
//    }

    /**
     * Login function to set device (user) id on Countly server to the string specified here.
     * Closes current session, then starts new one automatically if {@link Config#setAutoSessionsTracking(boolean)} is on, acquires device id.
     *
     * @param context Ctx to run in
     * @param id new user / device id string, cannot be empty
     */
    public static void login(Context context, String id) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            cly.sdk.login(ctx(context), id);
        }
    }

    /**
     * Logout function to make current user anonymous (that is with random id according to
     * {@link Config#setDeviceIdStrategy(Config.DeviceIdStrategy)} and such). Obviously makes sense only after a call to {@link #login(Context, String)},
     * so it throws error or does nothing (depending on {@link Config#testMode}) if current id wasn't set using {@link #login(Context, String)}.
     *
     * Closes current session.
     *
     * @param context Ctx to run in
     */
    public static void logout(Context context) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            cly.sdk.logout(ctx(context));
        }
    }

    /**
     * Resetting id without merging profiles on server, just set device id to new one:
     * <ul>
     *     <li>End current session if any</li>
     *     <li>Begin new session with new id if previously ended a session</li>
     * </ul>
     *
     * @param context Ctx to run in
     * @param id new user / device id string, cannot be empty
     */
    public static void resetDeviceId(Context context, String id) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            cly.sdk.resetDeviceId(ctx(context), id);
        }
    }

    /**
     * Consent function which enables corresponding features of SDK with respect to GDPR.
     * Activates corresponding SDK features.
     * Works only when {@link Config#setRequiresConsent(boolean)} is {@code true}.
     *
     * @param context Ctx to run in
     * @param features features to turn on
     */
    public static void onConsent(Context context, Config.Feature... features) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            int ftrs = 0;
            for (Config.Feature f : features) {
                ftrs = ftrs | f.getIndex();
            }
            cly.sdk.onConsent(ctx(context), ftrs);
        }
    }

    /**
     * Consent function which disables corresponding features of SDK with respect to GDPR.
     * Gracefully deactivates corresponding SDK features. Closes session if needed.
     * Works only when {@link Config#setRequiresConsent(boolean)} is {@code true}.
     *
     * @param context Ctx to run in
     * @param features features to turn offf
     */
    public static void onConsentRemoval(Context context, Config.Feature... features) {
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
        } else {
            int ftrs = 0;
            for (Config.Feature f : features) {
                ftrs = ftrs | f.getIndex();
            }
            cly.sdk.onConsentRemoval(ctx(context), ftrs);
        }
    }

    /**
     * Get the interface to Rating related features
     * @return
     */
    public static ModuleRating.Ratings ratings(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
            return null;
        } else {
            ModuleRating mr = cly.sdk.module(ModuleRating.class);
            if (mr != null) {
                return mr.new Ratings();
            }
            //if it is null, feature was not enabled, return mock
            L.w("Star Ratings module was not enabled, returning dummy module");
            ModuleRating emptyMr = new ModuleRating();
            emptyMr.disableModule();
            return emptyMr.new Ratings();
        }
    }

    /**
     * Get the interface to remote config related features
     * @return
     */
    public static ModuleRemoteConfig.RemoteConfig remoteConfig(){
        if (!isInitialized()) {
            L.wtf("Countly SDK is not initialized yet.");
            return null;
        } else {
            ModuleRemoteConfig mr = cly.sdk.module(ModuleRemoteConfig.class);
            if(mr!= null){
                return mr.new RemoteConfig();
            }

            //if it is null, feature was not enabled, return mock
            L.wtf("Remote Config module was not enabled, returning dummy module");
            ModuleRemoteConfig emptyMr = new ModuleRemoteConfig();
            emptyMr.disableModule();
            return emptyMr.new RemoteConfig();
        }
    }

    /*
     * ------------------------------------ Legacy methods -----------------------------------------
     */

    public static Countly sharedInstance() {
        return cly;
    }

    /**
     * Changes current device id to the one specified in parameter. Merges user profile with new id
     * (if any) with old profile.
     *
     * @deprecated since 19.0, use {@link #login(Context, String)}}, {@link #logout(Context)}
     * an {@link #resetDeviceId(Context, String)} instead
     * @param context Ctx to run in
     * @param id new user / device id string
     */
    public Countly changeDeviceId(Context context, String id) {
        if (Utils.isEmpty(id)) {
            logout(context);
        } else {
            login(context, id);
        }
        return this;
    }

    /**
     * Records a custom event with no segmentation values, a count of one and a sum of zero.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    @Deprecated
    public Countly recordEvent(final String key) {
        return recordEvent(key, null, 1, 0);
    }

    /**
     * Records a custom event with no segmentation values, the specified count, and a sum of zero.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    @Deprecated
    public Countly recordEvent(final String key, final int count) {
        return recordEvent(key, null, count, 0);
    }

    /**
     * Records a custom event with no segmentation values, and the specified count and sum.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    @Deprecated
    public Countly recordEvent(final String key, final int count, final double sum) {
        return recordEvent(key, null, count, sum);
    }

    /**
     * Records a custom event with the specified segmentation values and count, and a sum of zero.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty
     */
    @Deprecated
    public Countly recordEvent(final String key, final Map<String, String> segmentation, final int count) {
        return recordEvent(key, segmentation, count, 0);
    }

    /**
     * Records a custom event with the specified values.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     */
    @Deprecated
    public Countly recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        return recordEvent(key, segmentation, count, sum, 0);
    }

    /**
     * Records a custom event with the specified values.
     *
     * @deprecated since 19.0, use {@link #event(Context, String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @param dur duration of an event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     */
    @Deprecated
    public Countly recordEvent(final String key, final Map<String, String> segmentation, final int count, final double sum, final double dur) {
        if (!isInitialized()) {
            throw new IllegalStateException("Countly.init() must be called before recordEvent");
        } else {
            Event event = session(cly.ctx).event(key);
            if (segmentation != null) {
                event.setSegmentation(segmentation);
            }
            if (count != 1) {
                event.setCount(count);
            }
            if (sum != 0) {
                event.setSum(sum);
            }
            if (dur != 0) {
                event.setDuration(dur);
            }
            event.record();
        }
        return this;
    }

    /**
     * Start new view.
     * In case previous view in this session is not ended yet, it will be ended automatically.
     * In case session ends and last view haven't been ended yet, it will be ended automatically.
     * Creates begin request if this session hasn't yet been began.
     *
     * @deprecated since 19.0, use {@link Session#view(String)}} instead
     * @param viewName String representing name of this View
     */
    @Deprecated
    public Countly recordView(String viewName) {
        session(cly.ctx).view(viewName).start(false);
        return this;
    }

    /**
     * Sets information about user. Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     *
     * @deprecated since 19.0, use {@link #user()} instead
     * @param data Map&lt;String, String&gt; with user data
     */
    @Deprecated
    public Countly setUserData(Map<String, String> data) {
        return setUserData(data, null);
    }

    /**
     * Sets information about user with custom properties.
     * In custom properties you can provide any string key values to be stored with user
     * Possible keys are:
     * <ul>
     * <li>
     * name - (String) providing user's full name
     * </li>
     * <li>
     * username - (String) providing user's nickname
     * </li>
     * <li>
     * email - (String) providing user's email address
     * </li>
     * <li>
     * organization - (String) providing user's organization's name where user works
     * </li>
     * <li>
     * phone - (String) providing user's phone number
     * </li>
     * <li>
     * picture - (String) providing WWW URL to user's avatar or profile picture
     * </li>
     * <li>
     * picturePath - (String) providing local path to user's avatar or profile picture
     * </li>
     * <li>
     * gender - (String) providing user's gender as M for male and F for female
     * </li>
     * <li>
     * byear - (int) providing user's year of birth as integer
     * </li>
     * </ul>
     *
     * @deprecated since 19.0, use {@link #user()} instead
     * @param data Map&lt;String, String&gt; with user data
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     */
    @Deprecated
    public Countly setUserData(Map<String, String> data, Map<String, String> customdata) {
        UserEditor editor = user().edit();

        if (data != null) for (String key : data.keySet()) {
            editor.set(key, data.get(key));
        }

        if (customdata != null) for (String key : customdata.keySet()) {
            editor.set(key, customdata.get(key));
        }

        editor.commit();
        return this;
    }

    /**
     * Sets custom properties.
     * In custom properties you can provide any string key values to be stored with user
     *
     * @deprecated since 19.0, use {@link #user()} instead
     * @param customdata Map&lt;String, String&gt; with custom key values for this user
     */
    @Deprecated
    public Countly setCustomUserData(Map<String, String> customdata) {
        return setUserData(null, customdata);
    }

    /**
     * Set user location.
     *
     * Countly detects user location based on IP address. But for geolocation-enabled apps,
     * it's better to supply exact location of user.
     * Allows sending messages to a custom segment of users located in a particular area.
     *
     * @deprecated since 19.0, use {@link Session#addLocation(double, double)} instead
     * @param lat Latitude
     * @param lon Longitude
     */
    @Deprecated
    public Countly setLocation(double lat, double lon) {
        session(cly.ctx).addLocation(lat, lon);
        return this;
    }

    @Override
    public Usage login(String id) {
        sdk.login(ctx, id);
        return this;
    }

    @Override
    public Usage logout() {
        sdk.logout(ctx);
        return this;
    }

    @Override
    public Usage resetDeviceId(String id) {
        sdk.resetDeviceId(ctx, id);
        return this;
    }

    /**
     * Example {@link CrashProcessor} class to make legacy crash reporting methods work:
     * {@link #setCustomCrashSegments(Map)}, {@link #addCrashLog(String)}
     *
     * @deprecated since 19.0, use {@link Config#setCrashProcessorClass(Class)} instead
     */
    @Deprecated
    public static class DefaultCrashProcessor implements CrashProcessor {
        public final Map<String, String> segments = new HashMap<>();
        public final List<String> logs = new ArrayList<>();

        @Override
        public Crash process(Crash crash) {
            if (segments.size() > 0) {
                crash.setSegments(segments);
            }
            if (logs.size() > 0) {
                crash.setLogs(logs.toArray(new String[0]));
            }
            return crash;
        }
    }

    public static DefaultCrashProcessor legacyMethodCrashProcessor = null;

    /**
     * Sets custom segments to be reported with crash reports
     * In custom segments you can provide any string key values to segments crashes by
     *
     * @deprecated since 19.0, use {@link Config#setCrashProcessorClass(Class)} instead
     * @param segments Map&lt;String, String&gt; key segments and their values
     */
    @Deprecated
    public Countly setCustomCrashSegments(Map<String, String> segments) {
        if (legacyMethodCrashProcessor == null) {
            legacyMethodCrashProcessor = new DefaultCrashProcessor();
        }
        legacyMethodCrashProcessor.segments.putAll(segments);
        return this;
    }

    /**
     * Add crash breadcrumb like log record to the log that will be send together with crash report
     *
     * @deprecated since 19.0, use {@link Config#setCrashProcessorClass(Class)} instead
     * @param record String a bread crumb for the crash report
     */
    @Deprecated
    public Countly addCrashLog(String record) {
        if (legacyMethodCrashProcessor == null) {
            legacyMethodCrashProcessor = new DefaultCrashProcessor();
        }
        legacyMethodCrashProcessor.logs.add(record);
        return this;
    }

    /**
     * Log handled exception to report it to server as non fatal crash
     *
     * @deprecated since 19.0, use {@link Session#addCrashReport(Throwable, boolean, String, Map, String...)} instead
     * @param exception Exception to log
     */
    @Deprecated
    public Countly logException(Throwable exception) {
        return logException(exception, false);
    }

    /**
     * Log handled exception to report it to server
     *
     * @deprecated since 19.0, use {@link Session#addCrashReport(Throwable, boolean, String, Map, String...)} instead
     * @param exception Exception to log
     * @param fatal {@code true} if this exception is fatal, that is app cannot continue running
     */
    @Deprecated
    public Countly logException(Throwable exception, boolean fatal) {
        session(cly.ctx).addCrashReport(exception, fatal);
        return this;
    }

    /**
     * Start timed event with a specified key
     *
     * @deprecated since 19.0, use {@link Session#timedEvent(String)} instead
     * @param key name of the custom event, required, must not be the empty string or null
     * @return true if no event with this key existed before and event is started, false otherwise
     */
    @Deprecated
    public synchronized boolean startEvent(final String key) {
        session(cly.ctx).timedEvent(key);
        return false;
    }

    /**
     * End timed event with a specified key
     *
     * @deprecated since 19.0, use {@link Event#endAndRecord()} on {@link Session#timedEvent(String)} instead
     * @param key name of the custom event, required, must not be the empty string or null
     * @return true if event with this key has been previously started, false otherwise
     */
    @Deprecated
    public synchronized boolean endEvent(final String key) {
        session(cly.ctx).timedEvent(key).endAndRecord();
        return true;
    }

    /**
     * End timed event with a specified key
     *
     * @deprecated since 19.0, use {@link Event#endAndRecord()} on {@link Session#timedEvent(String)} instead
     * @param key name of the custom event, required, must not be the empty string
     * @param segmentation segmentation dictionary to associate with the event, can be null
     * @param count count to associate with the event, should be more than zero
     * @param sum sum to associate with the event
     * @throws IllegalStateException if Countly SDK has not been initialized
     * @throws IllegalArgumentException if key is null or empty, count is less than 1, or if
     *                                  segmentation contains null or empty keys or values
     * @return true if event with this key has been previously started, false otherwise
     */
    @Deprecated
    public synchronized boolean endEvent(final String key, final Map<String, String> segmentation, final int count, final double sum) {
        Event event = session(cly.ctx).timedEvent(key);
        if (segmentation != null && segmentation.size() > 0) {
            event.setSegmentation(segmentation);
        }
        if (count > 0) {
            event.setCount(count);
        }
        if (sum != 0) {
            event.setSum(sum);
        }
        event.endAndRecord();
        return true;
    }
}
