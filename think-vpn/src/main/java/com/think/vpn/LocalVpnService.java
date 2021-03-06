package com.think.vpn;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.think.R;
import com.think.core.util.AppUtils;
import com.think.core.util.LogUtils;
import com.think.core.util.ThreadManager;

import java.util.HashSet;
import java.util.Set;

/**
 * @author : zzp
 * @date : 2020/8/31
 **/
public class LocalVpnService extends VpnService implements Runnable {

    private static final String TAG = LocalVpnService.class.getSimpleName();
    /**
     * 开启VpnService指令
     */
    public static final String ACTION_CONNECT = "com.think.vpn.CONNECT";
    /**
     * 关闭vpnService指令
     */
    public static final String ACTION_DISCONNECT = "com.think.vpn.DISCONNECT";
    /* 通知栏ID */
    public static final int NOTIFICATION_ID = 0x0010;
    public static final String NOTIFICATION_CHANNEL_ID = "VPN";

    /**
     * 一般分包大小 MTU = 1500
     */
    public static final int MTU_PACK_SIZE = 1500;
    /**
     * SessionName
     */
    public static final String SESSION_NAME = "VPN";
    /* 是否已经连接 */
    private boolean mIsConnected = false;

    /**
     * 配置意图，用于打开配置页
     */
    private PendingIntent mConfigureIntent;

    /**
     * VPN连接处理
     */
    private VpnConnection mVpnConnect;

    /* 远程服务器名称 */
    private String mServerName;
    /* 远程服务器端口 */
    private int mServerPort;
    /* 代理主机 */
    private String proxyHost;
    /* 代理端口 */
    private int proxyPort;
    /* 拦截网络数据的应用包名 */
    private Set<String> mPackages;
    /* 主线程处理 */
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent();;
        intent.setComponent(new ComponentName("com.think.demo","com.think.demo.VpnActivity"));
        mConfigureIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mServerName = "";
        mServerPort = 2345;
        proxyHost = "";
        proxyPort = 0;
        mPackages = AppUtils.getPackageNames(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            // 断开上一次连接
            if (mIsConnected) {
                disconnect();
            }
            return START_NOT_STICKY;
        }
        // 启动连接
        connect();
        return START_STICKY;
    }

    /**
     * 开始连接
     */
    public void connect() {
        LogUtils.debug(TAG,"connect 方法调用");
        onStatusChange("VpnService 正在连接");
        mIsConnected = true;
        ThreadManager.getInstance().execPool(this);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        LogUtils.debug(TAG,"disconnect 方法调用");
        onStatusChange("VpnService 断开连接");
        ThreadManager.getInstance().cancelSchedule(mVpnConnect);
        mIsConnected = false;
    }

    @Override
    public void run() {
        LogUtils.debug(TAG,"LocalVpnService run ");
//        waitUntilPrepared();
        LogUtils.debug(TAG, "已经关闭其他app的Vpn服务，正在准备当前vpn服务");
        ParcelFileDescriptor fileDescriptor = establishVpn();
        mVpnConnect = new VpnConnection(this,fileDescriptor, mServerName, mServerPort, proxyHost, proxyPort);
        ThreadManager.getInstance().execPoolFuture(mVpnConnect);
        LogUtils.debug(TAG, "Vpn服务开始启动");
        onStatusChange("VpnService 连接中");
    }

    private ParcelFileDescriptor establishVpn() {
        Builder builder = new Builder()
                .setMtu(MTU_PACK_SIZE)
                .setSession(SESSION_NAME)
                .addAddress(VpnConnection.LOCAL_IP_ADDRESS_STR, 24)
                .addAddress("10.8.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("10.0.2.3")
                .setBlocking(false)
                .addRoute("0.0.0.0", 0)
                .addRoute("255.255.0.0", 16);
//        try {
//            LogUtils.debug("允许包名：" + getPackageName() + "被捕获");
//            builder.addAllowedApplication(getPackageName());
//        }catch (Exception e){
//            e.printStackTrace();
//        }
        for (String packageName : mPackages) {
            try {
                LogUtils.debug("允许包名：" + packageName + "被捕获");
                builder.addAllowedApplication(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "VPN 构建时，无法找到包名:" + packageName);
            }
        }
        ParcelFileDescriptor fileDescriptor = builder.establish();
        LogUtils.debug(TAG, "fileDescriptor = " + fileDescriptor);
        return fileDescriptor;
    }


    public void onStatusChange(final String msg){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                updateForegroundService(msg);
            }
        });
    }

    private void updateForegroundService(String msg) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        Notification.Builder builder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this,NOTIFICATION_CHANNEL_ID);
        }else{
            builder = new Notification.Builder(this);
        }
        Notification notification = builder.setContentIntent(mConfigureIntent)
                .setSmallIcon(R.drawable.ic_vpn)
//                .setTicker(msg)
                .setContentText(msg)
                .build();
        notificationManager.notify(NOTIFICATION_ID,notification);
    }
}
