package com.android.aihelper.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.aihelper.App;
import com.android.aihelper.R;
import com.android.aihelper.ai.AutoCheck;
import com.android.aihelper.ai.CityUtils;
import com.android.aihelper.ai.InitConfig;
import com.android.aihelper.ai.MyAI;
import com.android.aihelper.bean.QingYunKeResultBean;
import com.android.aihelper.bean.WeatherResult;
import com.android.aihelper.common.ApiService;
import com.android.aihelper.utils.RetrofitUtils;
import com.baidu.aip.asrwakeup3.core.recog.MyRecognizer;
import com.baidu.aip.asrwakeup3.core.recog.RecogResult;
import com.baidu.aip.asrwakeup3.core.recog.listener.ChainRecogListener;
import com.baidu.aip.asrwakeup3.core.recog.listener.StatusRecogListener;
import com.baidu.aip.asrwakeup3.core.util.MyLogger;
import com.baidu.aip.asrwakeup3.uiasr.params.CommonRecogParams;
import com.baidu.aip.asrwakeup3.uiasr.params.OnlineRecogParams;
import com.baidu.tts.auth.AuthInfo;
import com.baidu.tts.client.SpeechError;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.baidu.voicerecognition.android.ui.BaiduASRDigitalDialog;
import com.baidu.voicerecognition.android.ui.DigitalDialogInput;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.gson.Gson;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_QUESTION = 99;
    private String TAG = "MainActivity";
    @BindView(R.id.iv_mic)
    ImageView ivMic;
    @BindView(R.id.view_triangle_mic)
    View viewTriangleMic;
    @BindView(R.id.tv_mic)
    TextView tvMic;
    @BindView(R.id.iv_robot_sound)
    ImageView ivRobotSound;
    @BindView(R.id.view_triangle_robot)
    View viewTriangleRobot;
    @BindView(R.id.tv_robot)
    TextView tvRobot;
    @BindView(R.id.iv_robot)
    ImageView ivRobot;
    private boolean running;
    /**
     * 对话框界面的输入参数
     */
    private DigitalDialogInput input;
    private ChainRecogListener chainRecogListener;
    /**
     * 识别控制器，使用MyRecognizer控制识别的流程
     */
    protected MyRecognizer myRecognizer;
    private Disposable loadingDisposable;
    private boolean isSpeeching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        initBaidu();

        checkPermission(null);
    }

    private String[] requestPermissionList = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO};
    private int permissionIndex;

    public void checkPermission(PermissionResult callback) {
        permissionIndex = 0;
        new RxPermissions(this).requestEach(requestPermissionList).subscribe(new Consumer<Permission>() {
            List<String> deniedPermissionNames = new ArrayList<>();
            boolean hasNever;

            @Override
            public void accept(Permission permission) throws Exception {
                permissionIndex++;
                if (permission.shouldShowRequestPermissionRationale) {
                    deniedPermissionNames.add(permission.name);
                } else if (!permission.granted) {
                    deniedPermissionNames.add(permission.name);
                    hasNever = true;
                }

                if (permissionIndex >= requestPermissionList.length) {
                    if (callback != null) {
                        callback.onPermissionResult(deniedPermissionNames.toArray(new String[deniedPermissionNames.size()]), hasNever);
                    } else {
                        if (deniedPermissionNames.size() > 0) {
                            performTips(hasNever);
                        }
                    }
                }
            }
        });
    }


    private void initBaidu() {
        initSpeak();
        initRecognizer();
    }

    private void initRecognizer() {
        //初始化语音识别SDK
        myRecognizer = new MyRecognizer(this, new StatusRecogListener());
        chainRecogListener = new ChainRecogListener();
        // DigitalDialogInput 输入 ，MessageStatusRecogListener可替换为用户自己业务逻辑的listener
        chainRecogListener.addListener(new ChainRecogListener() {
            @Override
            public void onAsrFinalResult(String[] results, RecogResult recogResult) {
                super.onAsrFinalResult(results, recogResult);
                String message = results[0];
                showMyAsk(message);
            }
        });
        myRecognizer.setEventListener(chainRecogListener); // 替换掉原来的listener
    }


    private void showRobotMsg(String msg, boolean needSpeak) {
        viewTriangleRobot.setVisibility(View.VISIBLE);
        tvRobot.setVisibility(View.VISIBLE);
        tvRobot.setText(msg);
        if (needSpeak) {
            robotSpeak(msg);
        }
    }

    private void showRobotMsg(String msg) {
        showRobotMsg(msg, true);
    }

    private void hideRobotMsg() {
        viewTriangleRobot.setVisibility(View.GONE);
        tvRobot.setVisibility(View.GONE);
        tvRobot.setText("");
    }

    private void robotSpeak(String msg) {
        ivRobotSound.setVisibility(View.VISIBLE);
        AnimationDrawable drawable = (AnimationDrawable) ivRobotSound.getDrawable();
        drawable.start();
        if (mSpeechSynthesizer == null) {
            print("[ERROR], 初始化失败");
            return;
        }
        int result = mSpeechSynthesizer.speak(msg);
        print("合成并播放 按钮已经点击");
        checkResult(result, "speak");
    }

    private void stopRobotSpeak() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::stopRobotSpeak);
            return;
        }
        AnimationDrawable drawable = (AnimationDrawable) ivRobotSound.getDrawable();
        drawable.stop();
        ivRobotSound.setVisibility(View.GONE);
        print("停止合成引擎 按钮已经点击");
        int result = mSpeechSynthesizer.stop();
        checkResult(result, "stop");
    }


    private void showMyAsk(String msg) {
        tvMic.setVisibility(View.VISIBLE);
        viewTriangleMic.setVisibility(View.VISIBLE);
        tvMic.setText(msg);

        String respond = MyAI.checkAsk(msg);
        if (msg.equals(respond)) {//MyAI没有预设回应 那么使用网络请求
            loadingDisposable = Observable.interval(0, 300, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                    .subscribe(aLong -> {
                        if (aLong % 3 == 0) {
                            showRobotMsg(".", false);
                        } else if (aLong % 3 == 1) {
                            showRobotMsg("..", false);
                        } else {
                            showRobotMsg("...", false);
                        }
                    });
            ApiService apiService = RetrofitUtils.defBuilder().create(ApiService.class);
            if (respond.contains("天气")) {
                respond = respond.replaceAll("天气", "");
                if (TextUtils.isEmpty(respond)) {
                    stopLoading();
                    showRobotMsg("查天气的时候请说 城市+天气(只支持市级城市)");
                } else {
                    apiService.getWeather(CityUtils.queryCity(respond)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                            .subscribe(responseBody -> {
                                stopLoading();
                                try {
                                    WeatherResult bean = new Gson().fromJson(responseBody.string(), WeatherResult.class);
                                    if (bean.getResultcode() == 200) {
                                        WeatherResult.ResultBean result = bean.getResult();
                                        WeatherResult.ResultBean.TodayBean today = result.getToday();
                                        String stringBuilder = today.getCity() + "天气情况：" +
                                                "\n气温：" + today.getTemperature() + "\n天气：" + today.getWeather() +
                                                "\n风向：" + today.getWind() + "\n紫外线强度：" + today.getUv_index() +
                                                "\n穿衣指数：" + today.getDressing_index() + "\n穿衣建议：" + today.getDressing_advice();
                                        showRobotMsg(stringBuilder);
                                    } else {
                                        try {
                                            String reason = bean.getReason();
                                            if (TextUtils.isEmpty(reason)) {
                                                askRobotError();
                                            } else {
                                                showRobotMsg(reason);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            askRobotError();
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    askRobotError();
                                }
                            }, throwable -> {
                                stopLoading();
                                showRobotMsg("网络故障，再试一次吧");
                            });
                }
            } else {
                apiService.qingyunkeChat(respond).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                        .subscribe(responseBody -> {
                            stopLoading();
                            try {
                                QingYunKeResultBean bean = new Gson().fromJson(responseBody.string(), QingYunKeResultBean.class);
                                String content = bean.getContent();
                                if (content.contains("{br}")) {
                                    content = content.replaceAll("\\{br\\}", "\n");
                                }
                                showRobotMsg(content);
                            } catch (Exception e) {
                                e.printStackTrace();
                                askRobotError();
                            }
                        }, throwable -> {
                            stopLoading();
                            showRobotMsg("网络故障，再试一次吧");
                        });
            }
        } else {
            showRobotMsg(respond);
        }
    }

    private void askRobotError() {
        switch (new Random().nextInt(5)) {
            case 0:
                showRobotMsg("你好?神仙?妖怪?救救我.....");
                break;
            case 1:
                showRobotMsg("emm...你好！emm...我该说啥来着?");
                break;
            case 2:
                showRobotMsg("BOSS，我不理解你说的话！");
                break;
            case 3:
                showRobotMsg("BOSS，我好像坏了，但是我觉的我还要再抢救一下！");
                break;
            default:
            case 4:
                showRobotMsg("BOSS，我好像出故障了！需要修理一下了！");
                break;
        }
    }

    private void stopLoading() {
        if (loadingDisposable != null && !loadingDisposable.isDisposed()) {
            loadingDisposable.dispose();
        }
    }

    private void hideMyAsk() {
        tvMic.setVisibility(View.GONE);
        viewTriangleMic.setVisibility(View.GONE);
        tvMic.setText("");
    }

    @OnClick(R.id.iv_mic)
    public void onMicClicked() {
        checkPermission(new PermissionResult() {
            @Override
            public void onPermissionResult(String[] deniedPermissionNames, boolean hasNever) {
                if (deniedPermissionNames.length == 0) {
                    stopRobotSpeak();
                    showASRDialog();
                } else {
                    performTips(hasNever);
                }
            }
        });
    }

    private void performTips(boolean hasNever) {
        if (hasNever) {
            new AlertDialog.Builder(this).setTitle("错误").setMessage("未授权，无法使用功能！请前往设置中心打开权限").setNegativeButton("取消", null)
                    .setPositiveButton("前往设置中心", (dialog, which) -> PermissionUtils.launchAppDetailsSettings()).show();
        } else {
            ToastUtils.showShort("未授权，无法使用语音功能！");
        }
    }

    private void showASRDialog() {
        // 此处params可以打印出来，直接写到你的代码里去，最终的json一致即可。
        final Map<String, Object> params = fetchParams();

        // BaiduASRDigitalDialog的输入参数
        input = new DigitalDialogInput(myRecognizer, chainRecogListener, params);
        BaiduASRDigitalDialog.setInput(input); // 传递input信息，在BaiduASRDialog中读取,
        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);

        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "requestCode" + requestCode);
        if (requestCode == 2) {
            String message = "对话框的识别结果：";
            if (resultCode == RESULT_OK) {
                ArrayList results = data.getStringArrayListExtra("results");
                if (results != null && results.size() > 0) {
                    message += results.get(0);
                }
            } else {
                message += "没有结果";
            }
            MyLogger.info(message);
        }
        if (REQUEST_CODE_QUESTION == requestCode) {
            if (data != null) {
                String question = data.getStringExtra("question");
                if (!TextUtils.isEmpty(question)) {
                    showMyAsk(question);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (myRecognizer != null) {
            myRecognizer.release();
        }
        super.onDestroy();
    }

    /*
        * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
        */
    private final CommonRecogParams apiParams = new OnlineRecogParams();

    protected Map<String, Object> fetchParams() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        //  上面的获取是为了生成下面的Map， 自己集成时可以忽略
        Map<String, Object> params = apiParams.fetch(sp);
        //  集成时不需要上面的代码，只需要params参数。
        return params;
    }

    @OnClick({R.id.iv_robot, R.id.iv_question})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.iv_robot:
                if (isSpeeching) {
                    ToastUtils.showShort("你好，我是你的A I助手，我叫小A");
                } else {
                    hideMyAsk();
                    showRobotMsg("你好，我是你的A I助手，我叫小A");
                }
                break;
            case R.id.iv_question:
                stopRobotSpeak();
                startActivityForResult(new Intent(this, QuestionActivity.class), REQUEST_CODE_QUESTION);
                break;
        }
    }

    public interface PermissionResult {
        //当有拒绝授权时 返回的 deniedPermissionNames 会包含拒绝的权限名称  hasNever 表示有选择不再提醒的拒绝
        void onPermissionResult(String[] deniedPermissionNames, boolean hasNever);
    }


    private TtsMode ttsMode = TtsMode.ONLINE;
    private static final String TEMP_DIR = "/sdcard/baiduTTS";
    private static final String TEXT_FILENAME = TEMP_DIR + "/" + "bd_etts_text.dat";
    private static final String MODEL_FILENAME = TEMP_DIR + "/" + "bd_etts_common_speech_m15_mand_eng_high_am-mix_v3.0.0_20170505.dat";
    private SpeechSynthesizer mSpeechSynthesizer;

    private void initSpeak() {
        boolean isMix = ttsMode.equals(TtsMode.MIX);
        boolean isSuccess;
        if (isMix) {
            // 检查2个离线资源是否可读
            isSuccess = checkOfflineResources();
            if (!isSuccess) {
                return;
            } else {
                print("离线资源存在并且可读, 目录：" + TEMP_DIR);
            }
        }

        // 1. 获取实例
        mSpeechSynthesizer = SpeechSynthesizer.getInstance();
        mSpeechSynthesizer.setContext(this);

        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
            @Override
            public void onSynthesizeStart(String s) {

            }

            @Override
            public void onSynthesizeDataArrived(String s, byte[] bytes, int i) {

            }

            @Override
            public void onSynthesizeFinish(String s) {

            }

            @Override
            public void onSpeechStart(String s) {
                isSpeeching = true;
            }

            @Override
            public void onSpeechProgressChanged(String s, int i) {

            }

            @Override
            public void onSpeechFinish(String s) {
                isSpeeching = false;
                stopRobotSpeak();
            }

            @Override
            public void onError(String s, SpeechError speechError) {
                isSpeeching = false;
            }
        };
        mSpeechSynthesizer.setSpeechSynthesizerListener(listener);

        // 3. 设置appId，appKey.secretKey
        int result = mSpeechSynthesizer.setAppId("16798262");
        checkResult(result, "setAppId");
        result = mSpeechSynthesizer.setApiKey("7fmFaPqU8vZNvmb1PlLUtIWb", "34D2MiHQxUZLohCeHf1aVjrBYvclkwcn");
        checkResult(result, "setApiKey");

        // 4. 支持离线的话，需要设置离线模型
        if (isMix) {
            // 检查离线授权文件是否下载成功，离线授权文件联网时SDK自动下载管理，有效期3年，3年后的最后一个月自动更新。
            isSuccess = checkAuth();
            if (!isSuccess) {
                return;
            }
            // 文本模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, TEXT_FILENAME);
            // 声学模型文件路径 (离线引擎使用)， 注意TEXT_FILENAME必须存在并且可读
            mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, MODEL_FILENAME);
        }

        // 5. 以下setParam 参数选填。不填写则默认值生效
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "4");
        // 设置合成的音量，0-9 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOLUME, "9");
        // 设置合成的语速，0-9 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, "5");
        // 设置合成的语调，0-9 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_PITCH, "5");

        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 该参数设置为TtsMode.MIX生效。即纯在线模式不生效。
        // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线

        // x. 额外 ： 自动so文件是否复制正确及上面设置的参数
        Map<String, String> params = new HashMap<>();
        // 复制下上面的 mSpeechSynthesizer.setParam参数
        // 上线时请删除AutoCheck的调用
        if (isMix) {
            params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, TEXT_FILENAME);
            params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, MODEL_FILENAME);
        }
        InitConfig initConfig = new InitConfig("16798262", "7fmFaPqU8vZNvmb1PlLUtIWb", "34D2MiHQxUZLohCeHf1aVjrBYvclkwcn", ttsMode, params, listener);
        AutoCheck.getInstance(App.instance).check(initConfig, new Handler() {
            @Override
            /**
             * 开新线程检查，成功后回调
             */
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    AutoCheck autoCheck = (AutoCheck) msg.obj;
                    synchronized (autoCheck) {
                        String message = autoCheck.obtainDebugMessage();
                        print(message); // 可以用下面一行替代，在logcat中查看代码
                        // Log.w("AutoCheckMessage", message);
                    }
                }
            }

        });

        // 6. 初始化
        result = mSpeechSynthesizer.initTts(ttsMode);
        checkResult(result, "initTts");
    }

    private boolean checkOfflineResources() {
        String[] filenames = {TEXT_FILENAME, MODEL_FILENAME};
        for (String path : filenames) {
            File f = new File(path);
            if (!f.canRead()) {
                print("[ERROR] 文件不存在或者不可读取，请从assets目录复制同名文件到：" + path);
                print("[ERROR] 初始化失败！！！");
                return false;
            }
        }
        return true;
    }

    public void checkResult(int result, String method) {
        if (result != 0) {
            Log.i(TAG, "error code :" + result + " method:" + method + ", 错误码文档:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }

    public void print(String message) {
        Log.i(TAG, message);
    }

    private boolean checkAuth() {
        AuthInfo authInfo = mSpeechSynthesizer.auth(ttsMode);
        if (!authInfo.isSuccess()) {
            // 离线授权需要网站上的应用填写包名。本demo的包名是com.baidu.tts.sample，定义在build.gradle中
            String errorMsg = authInfo.getTtsError().getDetailMessage();
            print("【error】鉴权失败 errorMsg=" + errorMsg);
            return false;
        } else {
            print("验证通过，离线正式授权文件存在。");
            return true;
        }
    }
}
