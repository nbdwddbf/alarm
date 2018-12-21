package com.cloume.monitoralarm.util;

import com.cloume.monitoralarm.service.IAlarmService;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @ program: api
 * @ description: 方法库
 * @ author: dabuff
 * @ create: 2018-12-18 15:25
 */
public class MonitorAlarmUtil {

    private static HCNetSDK hcNetSDK = HCNetSDK.INSTANCE;
    private HCNetSDK.NET_DVR_DEVICEINFO_V30 deviceInfo;//设备信息

    private NativeLong lUserID;//用户句柄
    private NativeLong lAlarmHandle;//报警布防句柄

    private FMSGCallBack_V31 fMSFCallBack_V31;//报警回调函数实现
    private IAlarmService handler;//图片抓拍回调函数实现

    private String deviceIP;//已登录设备的IP地址
    private int devicePort;//设备端口号
    private String username;//设备用户名
    private String password;//设备登陆密码

    private void init() {
        //初始化
        boolean initSuc = hcNetSDK.NET_DVR_Init();
        lUserID = new NativeLong(-1);
        lAlarmHandle = new NativeLong(-1);
        fMSFCallBack_V31 = null;
        //判断摄像头是否开启
        if (!initSuc)
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "SDK初始化失败!!!");
        else
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "SDK初始化成功!!!");

        //设置连接时间与重连
        hcNetSDK.NET_DVR_SetConnectTime(20000, 5);
        hcNetSDK.NET_DVR_SetReconnect(10000, true);
    }

    //单个注册启动
    public void register(Map<String, Object> registInfo, IAlarmService handler) {
        deviceIP = (String) registInfo.get("ip");
        devicePort = (int) registInfo.get("port");
        username = (String) registInfo.get("username");
        password = (String) registInfo.get("password");

        registerAndSetupAlarm(deviceIP, devicePort, username, password, handler);
    }

    //多个注册启动
    public void register(List<Map<String, Object>> registInfo, IAlarmService handler) {
        for (Map<String, Object> item : registInfo) {
            deviceIP = (String) item.get("ip");
            devicePort = (int) item.get("port");
            username = (String) item.get("username");
            password = (String) item.get("password");

            registerAndSetupAlarm(deviceIP, devicePort, username, password, handler);
        }
    }

    private void registerAndSetupAlarm(String ip, int port, String username, String password, IAlarmService handler) {
        init();
        //注册之前先注销已注册的用户预览情况下不可注销
        if (lUserID.intValue() > -1) {
            //先注销
            hcNetSDK.NET_DVR_Logout(lUserID);
            lUserID = new NativeLong(-1);
        }

        //注册
        deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V30();
        //Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "设备信息：" + deviceInfo);

        //登录信息
        lUserID = hcNetSDK.NET_DVR_Login_V30(ip, (short) port, username, password, deviceInfo);

        int userID = lUserID.intValue();
        if (userID < 0) {
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "设备注册失败:" + hcNetSDK.NET_DVR_GetLastError());
            hcNetSDK.NET_DVR_Cleanup();
        } else
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "设备注册成功，lUserID:" + lUserID);
        setupAlarm(handler);
    }

    //单个注销
    public void logout(Map<String, Object> logoutInfo) {
        deviceIP = (String) logoutInfo.get("ip");
        devicePort = (int) logoutInfo.get("port");
        username = (String) logoutInfo.get("username");
        password = (String) logoutInfo.get("password");

        closeAndLogout(deviceIP, devicePort, username, password);
    }

    //多个注销
    public void logout(List<Map<String, Object>> logoutInfo) {

        for (Map<String, Object> item : logoutInfo) {
            deviceIP = (String) item.get("ip");
            devicePort = (int) item.get("port");
            username = (String) item.get("username");
            password = (String) item.get("password");

            closeAndLogout(deviceIP, devicePort, username, password);
        }
    }

    private void closeAndLogout(String ip, int port, String username, String password) {
        //设备信息
        deviceInfo = new HCNetSDK.NET_DVR_DEVICEINFO_V30();
        //登录信息
        lUserID = hcNetSDK.NET_DVR_Login_V30(ip, (short) port, username, password, deviceInfo);

        HCNetSDK.NET_DVR_SETUPALARM_PARAM m_strAlarmInfo = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
        m_strAlarmInfo.dwSize = m_strAlarmInfo.size();
        m_strAlarmInfo.byLevel = 1;
        m_strAlarmInfo.byAlarmInfoType = 1;
        m_strAlarmInfo.byDeployType = 1;
        m_strAlarmInfo.write();
        lAlarmHandle = hcNetSDK.NET_DVR_SetupAlarmChan_V41(lUserID, m_strAlarmInfo);
        //报警撤防
        NativeLong returnAlarm = closeAlarm(lAlarmHandle);
        if (returnAlarm.intValue() == -1)
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "撤防成功!!!");
        //注销
        if (lUserID.longValue() > -1) {
            if (hcNetSDK.NET_DVR_Logout(lUserID)) {
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "注销成功!!!");
                lUserID = new NativeLong(-1);
            }
        }
    }

    private class FMSGCallBack_V31 implements HCNetSDK.FMSGCallBack_V31 {

        @Override
        public boolean invoke(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
            alarmDataHandle(lCommand, pAlarmer, pAlarmInfo, dwBufLen, pUser);

            return true;
        }
    }

    private void alarmDataHandle(NativeLong lCommand, HCNetSDK.NET_DVR_ALARMER pAlarmer, Pointer pAlarmInfo, int dwBufLen, Pointer pUser) {
        String sAlarmType;
        String[] newRow = new String[3];
        //报警时间
        Date today = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String[] sIP;

        sAlarmType = "lCommand=" + lCommand.intValue();
        //lCommand是传的报警类型
        switch (lCommand.intValue()) {
            case HCNetSDK.COMM_UPLOAD_FACESNAP_RESULT:
                //实时人脸抓拍上传
                HCNetSDK.NET_VCA_FACESNAP_RESULT strFaceSnapInfo = new HCNetSDK.NET_VCA_FACESNAP_RESULT();
                strFaceSnapInfo.write();
                Pointer pFaceSnapInfo = strFaceSnapInfo.getPointer();
                pFaceSnapInfo.write(0, pAlarmInfo.getByteArray(0, strFaceSnapInfo.size()), 0, strFaceSnapInfo.size());
                strFaceSnapInfo.read();
                sAlarmType = sAlarmType + "：人脸抓拍上传，人脸评分：" + strFaceSnapInfo.dwFaceScore;
                newRow[0] = dateFormat.format(today);
                //报警类型
                newRow[1] = sAlarmType;
                //报警设备IP地址
                sIP = new String(strFaceSnapInfo.struDevInfo.struDevIP.sIpV4).split("\0", 2);
                newRow[2] = sIP[0];

                int length = strFaceSnapInfo.dwFacePicLen;//抓拍图片长度
                byte[] bytess = strFaceSnapInfo.pBuffer1.getByteArray(0, length);//抓拍图片的bytes
                SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss"); //设置日期格式
                Long time = System.currentTimeMillis(); // new Date()为获取当前系统时间
                handler.onFaceSnaped(sIP[0], bytess, length, time);
                //System.out.println("报警时间：" + newRow[0] + "\n报警类型：" + newRow[1] + "\n报警设备IP地址：" + newRow[2]);
                break;
            default:
                newRow[0] = dateFormat.format(today);
                //报警类型
                newRow[1] = sAlarmType;
                //报警设备IP地址
                sIP = new String(pAlarmer.sDeviceIP).split("\0", 2);
                newRow[2] = sIP[0];
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "报警时间：" + newRow[0] + "\n报警类型：" + newRow[1] + "\n报警设备IP地址：" + newRow[2]);
                break;
        }
    }

    private void setupAlarm(IAlarmService handler) {
        this.handler = handler;
        if (lUserID.intValue() == -1) {
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "请先注册!!!");

            return;
        }
        //System.out.println("lAlarmHandle :" + lAlarmHandle);
        if (lAlarmHandle.intValue() < 0)//尚未布防,需要布防
        {
            if (fMSFCallBack_V31 == null) {
                fMSFCallBack_V31 = new FMSGCallBack_V31();
                Pointer pUser = null;
                if (!hcNetSDK.NET_DVR_SetDVRMessageCallBack_V31(fMSFCallBack_V31, pUser)) {
                    Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "设置回调函数失败!!!");
                }
            }
            HCNetSDK.NET_DVR_SETUPALARM_PARAM m_strAlarmInfo = new HCNetSDK.NET_DVR_SETUPALARM_PARAM();
            m_strAlarmInfo.dwSize = m_strAlarmInfo.size();
            m_strAlarmInfo.byLevel = 1;
            m_strAlarmInfo.byAlarmInfoType = 1;
            m_strAlarmInfo.byDeployType = 1;
            m_strAlarmInfo.write();
            lAlarmHandle = hcNetSDK.NET_DVR_SetupAlarmChan_V41(lUserID, m_strAlarmInfo);
            if (lAlarmHandle.intValue() == -1) {
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "布防失败!!!");
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "错误代码：" + hcNetSDK.NET_DVR_GetLastError());
            } else {
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "布防成功!!!");
            }
        } else {
            Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "已经布防，不要重复操作!!!");
        }
    }

    private NativeLong closeAlarm(NativeLong lAlarmHandle) {
        //报警撤防
        if (lAlarmHandle.intValue() > -1) {
            if (!hcNetSDK.NET_DVR_CloseAlarmChan_V30(lAlarmHandle)) {
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "撤防失败!!!");
            } else {
                lAlarmHandle = new NativeLong(-1);
                Logger.getLogger(MonitorAlarmUtil.class.getName()).log(Level.INFO, "撤防成功!!!");
            }
        }

        return lAlarmHandle;
    }
}