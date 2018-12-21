package com.cloume.monitoralarm.service;

/**
 * @program: monitoralarm
 * @description: TODO
 * @author: dabuff
 * @create: 2018-12-20 15:25
 */
public interface IAlarmService {
    /**
     *
     * @param ip 用来标识哪个摄像头
     * @param bytes 图片字节流
     * @param length 字节流长度
     * @param time 时间戳（毫秒）
     */
    void onFaceSnaped(String ip, byte[] bytes, int length, long time);
}