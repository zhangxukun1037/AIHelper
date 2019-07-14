package com.android.aihelper.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
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
import com.iflytek.cloud.util.VerifierUtil;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.functions.Consumer;

public class LoginActivity extends AppCompatActivity implements View.OnTouchListener {

    @BindView(R.id.layout_title)
    RelativeLayout layoutTitle;
    @BindView(R.id.tv_yz_tips)
    TextView tvYzTips;
    private String TAG = "LoginActivity";
    @BindView(R.id.tv_register)
    TextView tvRegister;
    @BindView(R.id.iv_mic)
    ImageView ivMic;
    @BindView(R.id.iv_sound_level)
    ImageView ivSoundLevel;
    @BindView(R.id.tv_tips)
    TextView tvTips;

    // 默认为数字密码
    private int mPwdType = 3;
    // 数字密码类型为3，其他类型暂未开放
    private static final int PWD_TYPE_NUM = 3;

    // 会话类型
    private int mSST = 1;
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
                    tvYzTips.setText("验证通过，即将进入AI助手页面");
                    tvYzTips.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    }, 1000);
                } else {
                    tvYzTips.setText("验证失败");
                }
                ivSoundLevel.setVisibility(View.GONE);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            isStartWork = false;
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            if (SpeechEvent.EVENT_VOLUME == eventType) {
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
            }
        }

        @Override
        public void onError(SpeechError error) {
            isStartWork = false;
            mCanStartRecord = false;

            tvYzTips.setText("验证失败" + error.getErrorDescription());
            ivSoundLevel.setVisibility(View.GONE);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);
        checkPermission(new MainActivity.PermissionResult() {
            @Override
            public void onPermissionResult(String[] deniedPermissionNames, boolean hasNever) {
                if (deniedPermissionNames.length > 0) {
                    performTips(hasNever);
                } else {
                    ivMic.setOnTouchListener(LoginActivity.this);
                    SpeechUtility.createUtility(LoginActivity.this, SpeechConstant.APPID + "=5d29d0db");

                    mIdVerifier = IdentityVerifier.createVerifier(LoginActivity.this, new InitListener() {

                        @Override
                        public void onInit(int errorCode) {
                            if (ErrorCode.SUCCESS == errorCode) {
//                    showTip("引擎初始化成功");
                            } else {
                                tvYzTips.setText("引擎初始化失败，即将进入AI助手页面");
                                tvYzTips.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        finish();
                                    }
                                }, 1000);
                            }
                        }
                    });

                    mProDialog = new ProgressDialog(LoginActivity.this);
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
                    mVerifyNumPwd = VerifierUtil.generateNumberPassword(8);
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        checkPermission(null);
    }

    @OnClick(R.id.tv_register)
    public void onViewClicked() {
        startActivity(new Intent(this, VoiceprintRegisterActivity.class));
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!checkInstance()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                ivSoundLevel.setVisibility(View.VISIBLE);
                if (!isStartWork) {
                    // 根据业务类型调用服务
                    vocalVerify();
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
        return true;
    }

    private boolean checkInstance() {
        if (null == mIdVerifier) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            tvYzTips.setText("引擎初始化失败，即将进入AI助手页面");
            tvYzTips.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }
            }, 1000);
            return false;
        } else {
            return true;
        }
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

    private void vocalVerify() {

        StringBuffer strBuffer = new StringBuffer();
        strBuffer.append("您的验证密码：" + mVerifyNumPwd + "\n");
        strBuffer.append("请长按“麦克风”按钮进行验证！");
        tvYzTips.setText(strBuffer.toString());
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


    @Override
    protected void onDestroy() {
        try {
            if (null != mIdVerifier) {
                mPcmRecorder.stopRecord(true);
                mIdVerifier.cancel();
                mIdVerifier.destroy();
                mIdVerifier = null;
                mPcmRecorder = null;
                mPcmRecordListener = null;
            }
            SpeechUtility.getUtility().destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
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
