package com.android.aihelper;

import android.app.Application;

import com.baidu.tts.client.SpeechSynthesizer;

/**
 * 2019/7/12 22:37
 */
public class App extends Application {
    public static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }
}
