package com.android.aihelper.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.aihelper.R;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.IdentityListener;
import com.iflytek.cloud.IdentityResult;
import com.iflytek.cloud.IdentityVerifier;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.record.PcmRecorder;
import com.iflytek.idata.extension.IFlyCollectorExt;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.functions.Consumer;

public class VoiceprintRegisterActivity extends AppCompatActivity implements View.OnTouchListener {
    @BindView(R.id.tv_auth_id)
    EditText tvAuthId;
    private String TAG = "VoiceprintRegisterActivity";
    @BindView(R.id.iv_back)
    ImageView ivBack;
    @BindView(R.id.layout_title)
    RelativeLayout layoutTitle;
    @BindView(R.id.tv_speak)
    TextView tvSpeak;
    @BindView(R.id.tv_speak_count)
    TextView tvSpeakCount;
    @BindView(R.id.tv_tips)
    TextView tvTips;
    @BindView(R.id.iv_mic)
    ImageView ivMic;
    @BindView(R.id.iv_sound_level)
    ImageView ivSoundLevel;
    // 默认为数字密码
    private int mPwdType = 3;
    // 数字密码类型为3，其他类型暂未开放
    private static final int PWD_TYPE_NUM = 3;

    // 会话类型
    private int mSST = 0;
    // 注册
    private static final int SST_ENROLL = 0;
    // 验证
    private static final int SST_VERIFY = 1;

    // 模型操作类型
    private int mModelCmd;
    // 查询模型
    private static final int MODEL_QUE = 0;
    // 删除模型
    private static final int MODEL_DEL = 1;

    // 用户id，唯一标识
    private String authid = "zhangxukun1037";
    // 身份验证对象
    private IdentityVerifier mIdVerifier;
    // 数字声纹密码
    private String mNumPwd = "";
    // 数字声纹密码段，默认有5段
    private String[] mNumPwdSegs;
    // 用于验证的数字密码
    private String mVerifyNumPwd = "";

    // 是否可以录音
    private boolean mCanStartRecord = false;
    // 是否可以录音
    private boolean isStartWork = false;
    // 录音采样率
    private final int SAMPLE_RATE = 16000;
    // pcm录音机
    private PcmRecorder mPcmRecorder;
    // 进度对话框
    private ProgressDialog mProDialog;

    /**
     * 下载密码监听器
     */
    private IdentityListener mDownloadPwdListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            mProDialog.dismiss();
            ivMic.setClickable(true);
            switch (mPwdType) {
                case PWD_TYPE_NUM:
                    boolean newPwd = mNumPwdSegs != null;
                    StringBuilder numberString = new StringBuilder();
                    try {
                        JSONObject object = new JSONObject(result.getResultString());
                        if (!object.has("num_pwd")) {
                            mNumPwd = null;
                            return;
                        }

                        JSONArray pwdArray = object.optJSONArray("num_pwd");
                        numberString.append(pwdArray.get(0));
                        for (int i = 1; i < pwdArray.length(); i++) {
                            numberString.append("-").append(pwdArray.get(i));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mNumPwd = numberString.toString();
                    mNumPwdSegs = mNumPwd.split("-");
                    if (newPwd) {
                        tvSpeak.setText("声纹密码已更新！请开始训练");
                        tvSpeakCount.setVisibility(View.GONE);
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            mProDialog.dismiss();
            showTip("密码下载失败！" + error.getPlainDescription(true));
            finish();
        }
    };

    /**
     * 声纹注册监听器
     */
    private IdentityListener mEnrollListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, result.getResultString());

            JSONObject jsonResult = null;
            try {
                jsonResult = new JSONObject(result.getResultString());
                int ret = jsonResult.getInt("ret");

                if (ErrorCode.SUCCESS == ret) {

                    final int suc = Integer.parseInt(jsonResult.optString("suc"));
                    final int rgn = Integer.parseInt(jsonResult.optString("rgn"));

                    if (suc == rgn) {
                        tvSpeak.setText("注册成功");
                        tvSpeakCount.setVisibility(View.GONE);

                        mCanStartRecord = false;
                        isStartWork = false;
                        if (mPcmRecorder != null) {
                            mPcmRecorder.stopRecord(true);
                        }
                    } else {
                        int nowTimes = suc + 1;
                        int leftTimes = 5 - nowTimes;

                        String strBuffer = "请长按“麦克风”！\n" +
                                "请读出：" + mNumPwdSegs[nowTimes - 1] + "\n";
                        tvSpeak.setText(strBuffer);
                        tvSpeakCount.setText("训练 第" + nowTimes + "遍，剩余" + leftTimes + "遍");
                    }

                } else {
//                    tvSpeak.setText("读入密码错误，");
                    ivSoundLevel.setVisibility(View.GONE);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle bundle) {
            if (SpeechEvent.EVENT_VOLUME == eventType) {
//                showTip("音量：" + arg1);
                tvSpeakCount.setVisibility(View.VISIBLE);
                ivSoundLevel.setVisibility(View.VISIBLE);
                switch (arg1 % 7) {
                    default:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_1);
                        break;
                    case 1:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_2);
                        break;
                    case 2:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_3);
                        break;
                    case 3:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_4);
                        break;
                    case 4:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_5);
                        break;
                    case 5:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_6);
                        break;
                    case 6:
                        ivSoundLevel.setImageResource(R.drawable.icon_recording_level_7);
                        break;
                }
            } else if (SpeechEvent.EVENT_VAD_EOS == eventType) {
                ivSoundLevel.setVisibility(View.GONE);
                tvSpeakCount.setVisibility(View.GONE);
            }

        }

        @Override
        public void onError(SpeechError error) {
            isStartWork = false;
            tvSpeak.setText("注册失败，声纹已注册！");
            tvSpeakCount.setText("请修改声纹ID后重新注册!");
        }

    };

    /**
     * 声纹验证监听器
     */
    private IdentityListener mVerifyListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, "verify:" + result.getResultString());

            try {
                JSONObject object = new JSONObject(result.getResultString());
                String decision = object.getString("decision");

                if ("accepted".equalsIgnoreCase(decision)) {
                    tvSpeak.setText("验证通过");
                } else {
                    tvSpeak.setText("验证失败\n声纹未注册");
                }
                tvSpeakCount.setVisibility(View.GONE);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            isStartWork = false;
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            if (SpeechEvent.EVENT_VOLUME == eventType) {
                showTip("音量：" + arg1);
            } else if (SpeechEvent.EVENT_VAD_EOS == eventType) {
                showTip("录音结束");
            }
        }

        @Override
        public void onError(SpeechError error) {
            isStartWork = false;
            mCanStartRecord = false;

            StringBuffer errorResult = new StringBuffer();
            errorResult.append("验证失败！\n");
            errorResult.append("错误信息：" + error.getPlainDescription(true) + "\n");
            tvSpeak.setText(errorResult.toString());
            tvSpeakCount.setText("请长按“麦克风”重新验证!");
        }
    };

    /**
     * 声纹模型操作监听器
     */
    private IdentityListener mModelListener = new IdentityListener() {

        @Override
        public void onResult(IdentityResult result, boolean islast) {
            Log.d(TAG, "model operation:" + result.getResultString());

            mProDialog.dismiss();

            JSONObject jsonResult = null;
            int ret = ErrorCode.SUCCESS;
            try {
                jsonResult = new JSONObject(result.getResultString());
                ret = jsonResult.getInt("ret");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            switch (mModelCmd) {
                case MODEL_QUE:
                    if (ErrorCode.SUCCESS == ret) {
                        showTip("模型存在");
                    } else {
                        showTip("模型不存在");
                    }
                    break;
                case MODEL_DEL:
                    if (ErrorCode.SUCCESS == ret) {
                        showTip("模型已删除");
                    } else {
                        showTip("模型删除失败");
                    }
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }

        @Override
        public void onError(SpeechError error) {
            mProDialog.dismiss();
            showTip(error.getPlainDescription(true));
        }
    };

    private void showTip(String msg) {
        ToastUtils.showShort(msg);
    }

    /**
     * 录音机监听器
     */
    private PcmRecorder.PcmRecordListener mPcmRecordListener = new PcmRecorder.PcmRecordListener() {

        @Override
        public void onRecordStarted(boolean success) {
        }

        @Override
        public void onRecordReleased() {
        }

        @Override
        public void onRecordBuffer(byte[] data, int offset, int length) {
            StringBuffer params = new StringBuffer();

            switch (mSST) {
                case SST_ENROLL:
                    params.append("rgn=5,");
                    params.append("ptxt=" + mNumPwd + ",");
                    params.append("pwdt=" + mPwdType + ",");
                    mIdVerifier.writeData("ivp", params.toString(), data, 0, length);
                    break;
                case SST_VERIFY:
                    params.append("ptxt=" + mVerifyNumPwd + ",");
                    params.append("pwdt=" + mPwdType + ",");
                    mIdVerifier.writeData("ivp", params.toString(), data, 0, length);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(SpeechError e) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voiceprint_register);
        ButterKnife.bind(this);
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=5d29d0db");

        ivMic.setOnTouchListener(this);
        mIdVerifier = IdentityVerifier.createVerifier(this, new InitListener() {

            @Override
            public void onInit(int errorCode) {
                if (ErrorCode.SUCCESS == errorCode) {
//                    showTip("引擎初始化成功");
                    tvSpeak.setText("引擎初始化成功！");
                } else {
                    showTip("引擎初始化失败，错误码：" + errorCode + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                    finish();
                }
            }
        });

        mProDialog = new ProgressDialog(this);
        mProDialog.setCancelable(true);
        mProDialog.setTitle("请稍候");
        // cancel进度框时，取消正在进行的操作
        mProDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                if (null != mIdVerifier) {
                    mIdVerifier.cancel();
                }
            }
        });

        downloadPwd();


    }

    private void cancelOperation() {
        isStartWork = false;
        mIdVerifier.cancel();

        if (null != mPcmRecorder) {
            mPcmRecorder.stopRecord(true);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!checkInstance()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                authid = getAuthid();
                if (TextUtils.isEmpty(authid)) {
                    showTip("请输入authid");
                    break;
                }
                ivSoundLevel.setVisibility(View.VISIBLE);
                if (!isStartWork) {
                    // 根据业务类型调用服务
                    if (mSST == SST_ENROLL) {
                        if (null == mNumPwdSegs) {
                            // 启动录音机时密码为空，中断此次操作，下载密码
                            downloadPwd();
                            break;
                        }
                        vocalEnroll();
                    } else if (mSST == SST_VERIFY) {
                        vocalVerify();
                    } else {
                        showTip("请先选择相应业务！");
                        break;
                    }
                    isStartWork = true;
                    mCanStartRecord = true;
                }
                if (mCanStartRecord) {
                    try {
                        mPcmRecorder = new PcmRecorder(SAMPLE_RATE, 40);
                        mPcmRecorder.startRecording(mPcmRecordListener);
                    } catch (SpeechError e) {
                        e.printStackTrace();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                v.performClick();
                ivSoundLevel.setVisibility(View.GONE);
                mIdVerifier.stopWrite("ivp");
                if (null != mPcmRecorder) {

                    mPcmRecorder.stopRecord(true);
                }
                break;

            default:
                break;
        }
        return false;
    }


    /**
     * 注册
     */
    private void vocalEnroll() {
        StringBuffer strBuffer = new StringBuffer();
        strBuffer.append("请读出：" + mNumPwdSegs[0]);
        tvSpeak.setText(strBuffer.toString());
        tvSpeakCount.setVisibility(View.VISIBLE);
        tvSpeakCount.setText("训练 第" + 1 + "遍，剩余4遍");

        // 设置声纹注册参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ivp");
        // 设置会话类型
        mIdVerifier.setParameter(SpeechConstant.MFV_SST, "enroll");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, authid);
        // 设置监听器，开始会话
        mIdVerifier.startWorking(mEnrollListener);
    }

    private void vocalVerify() {

        StringBuffer strBuffer = new StringBuffer();
        strBuffer.append("您的验证密码：" + mVerifyNumPwd + "\n");
        strBuffer.append("请长按“麦克风”按钮进行验证！\n");
        tvSpeak.setText(strBuffer.toString());
        // 设置声纹验证参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ivp");
        // 设置会话类型
        mIdVerifier.setParameter(SpeechConstant.MFV_SST, "verify");
        // 验证模式，单一验证模式：sin
        mIdVerifier.setParameter(SpeechConstant.MFV_VCM, "sin");
        // 用户的唯一标识，在声纹业务获取注册、验证、查询和删除模型时都要填写，不能为空
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, authid);
        // 设置监听器，开始会话
        mIdVerifier.startWorking(mVerifyListener);
    }


    private void downloadPwd() {
        authid = getAuthid();
        if (TextUtils.isEmpty(authid)) {
            showTip("请输入authid");
            return;
        }
        // 获取密码之前先终止之前的操作
        mIdVerifier.cancel();
        mNumPwd = null;
        // 下载密码时，按住说话触摸无效
        ivMic.setClickable(false);

        mProDialog.setMessage("下载中...");
        mProDialog.show();

        // 设置下载密码参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ivp");

        // 子业务执行参数，若无可以传空字符传
        StringBuffer params = new StringBuffer();
        // 设置模型操作的密码类型
        params.append("pwdt=" + mPwdType + ",");
        // 执行密码下载操作
        int ret = mIdVerifier.execute("ivp", "download", params.toString(), mDownloadPwdListener);
        if (ret != 0)
            mProDialog.dismiss();
    }


    /**
     * 模型操作
     *
     * @param cmd 命令
     */
    private void executeModelCommand(String cmd) {
        if ("query".equals(cmd)) {
            mProDialog.setMessage("查询中...");
        } else if ("delete".equals(cmd)) {
            mProDialog.setMessage("删除中...");
        }
        mProDialog.show();
        // 设置声纹模型参数
        // 清空参数
        mIdVerifier.setParameter(SpeechConstant.PARAMS, null);
        // 设置会话场景
        mIdVerifier.setParameter(SpeechConstant.MFV_SCENES, "ivp");
        // 用户id
        mIdVerifier.setParameter(SpeechConstant.AUTH_ID, authid);

        // 子业务执行参数，若无可以传空字符传
        StringBuffer params3 = new StringBuffer();
        // 设置模型操作的密码类型
        params3.append("pwdt=" + mPwdType + ",");
        // 执行模型操作
        mIdVerifier.execute("ivp", cmd, params3.toString(), mModelListener);
    }

    @Override
    protected void onDestroy() {
        if (null != mIdVerifier) {
            mIdVerifier.destroy();
            mIdVerifier = null;
        }
        super.onDestroy();
    }

    private boolean checkInstance() {
        if (null == mIdVerifier) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            this.showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化");
            return false;
        } else {
            return true;
        }
    }

    private String getAuthid() {
        return authid;
    }

    @OnClick({R.id.btn_set_id})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_set_id:
                String tempAuthId = tvAuthId.getText().toString();
                if (TextUtils.isEmpty(tempAuthId)) {
                    showTip("请输入要注册的ID");
                    return;
                }
                if (tempAuthId.length() < 6) {
                    showTip("请输入声纹id 长度6-20位");
                    return;
                }
                authid = tempAuthId;
                downloadPwd();
                break;
        }
    }

    private String[] requestPermissionList = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO};

    private int permissionIndex;

    public void checkPermission(MainActivity.PermissionResult callback) {
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

    private void performTips(boolean hasNever) {
        if (hasNever) {
            new AlertDialog.Builder(this).setTitle("错误").setMessage("未授权，无法使用功能！请前往设置中心打开权限").setNegativeButton("取消", null)
                    .setPositiveButton("前往设置中心", (dialog, which) -> PermissionUtils.launchAppDetailsSettings()).show();
        } else {
            ToastUtils.showShort("未授权，无法使用语音功能！");
            finish();
        }
    }
}
