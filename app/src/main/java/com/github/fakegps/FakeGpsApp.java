package com.github.fakegps;

import android.app.Application;
import android.content.Context;

import com.litesuits.orm.LiteOrm;
import com.tencent.fakegps.BuildConfig;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;

import tiger.radio.loggerlibrary.Logger;

/**
 * Created by tiger on 7/23/16.
 */
public class FakeGpsApp extends Application {

    private static Context sAppContext;
    private volatile static LiteOrm sLiteOrm;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sAppContext = base;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            initLogger();
        }
        initOsmdroid();
        JoyStickManager.get().init(this);
    }

    public static Context get() {
        return sAppContext;
    }

    public static LiteOrm getLiteOrm() {
        if (sLiteOrm == null) {
            synchronized (FakeGpsApp.class) {
                if (sLiteOrm == null) {
                    sLiteOrm = LiteOrm.newSingleInstance(sAppContext, "fake_gps.db");
                    sLiteOrm.setDebugged(BuildConfig.DEBUG);
                }
            }
        }
        return sLiteOrm;
    }

    private void initLogger() {
        // Keep diagnostic logs in internal storage; release builds do not log at all.
        File outputDir = new File(getFilesDir(), "logs");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        Logger.configure(outputDir, "logs");
    }

    /**
     * Must run before the first MapView is created. Mapnik rejects the library's
     * default user agent and would otherwise serve a blank map.
     */
    private void initOsmdroid() {
        IConfigurationProvider configuration = Configuration.getInstance();
        configuration.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        configuration.setOsmdroidBasePath(new File(getFilesDir(), "osmdroid"));
        configuration.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));
        configuration.setUserAgentValue(getPackageName() + "/" + BuildConfig.VERSION_NAME);
        // Keep the tile cache small on low-end devices.
        configuration.setTileFileSystemCacheMaxBytes(50L * 1024 * 1024);
        configuration.setTileFileSystemCacheTrimBytes(40L * 1024 * 1024);
    }

}
