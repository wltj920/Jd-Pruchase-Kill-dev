package com.wltj920.jdkill;

import cn.hutool.extra.mail.MailUtil;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author: lianghuan
 * @date: 2021/1/8 20:51
 */
public class RushToPurchase implements Runnable {
    volatile static Integer times = 0; //抢购次数(顺序执行)
    volatile static Integer successTimes = 0;//成功抢购次数
    //请求头
    static Map<String, List<String>> stringListMap = new HashMap<>();

    @Override
    public void run() {
        JSONObject headers = new JSONObject();
        while (times < Start.optCount && successTimes < Start.ok) {
            //获取ip，使用的是免费的 携趣代理 ，不需要或者不会用可以注释掉
            //setIpProxy();

            headers.put(Start.headerAgent, Start.headerAgentArg);
            headers.put(Start.Referer, Start.RefererArg);
            //抢购
            try {
                //这里是加到购物车里，显示购物车页面
                HttpUrlConnectionUtil.get(headers, "https://cart.jd.com/gate.action?pcount=1&ptype=1&pid=" + Start.pid);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //stringListMap.clear();
            try {
                //订单信息
                stringListMap = Start.manager.get(new URI("https://trade.jd.com/shopping/order/getOrderInfo.action"), stringListMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            List<String> cookie = stringListMap.get("Cookie");
            headers.put("Cookie", cookie.get(0));
            try {
                //这一步是跳转到订单结算页面
                HttpUrlConnectionUtil.get(headers, "https://trade.jd.com/shopping/order/getOrderInfo.action");
            } catch (IOException e) {
                e.printStackTrace();
            }
            //提交订单
            JSONObject subData = new JSONObject();
            headers = new JSONObject();
            subData.put("overseaPurchaseCookies", "");
            subData.put("vendorRemarks", "[]");
            subData.put("submitOrderParam.sopNotPutInvoice", "false");
            subData.put("submitOrderParam.ignorePriceChange", "1");
            subData.put("submitOrderParam.btSupport", "0");
            subData.put("submitOrderParam.isBestCoupon", "1");
            subData.put("submitOrderParam.jxj", "1");
            subData.put("submitOrderParam.trackID", Login.ticket);
            subData.put("submitOrderParam.eid", Start.eid);
            subData.put("submitOrderParam.fp", Start.fp);
            subData.put("submitOrderParam.needCheck", "1");

            headers.put("Referer", "http://trade.jd.com/shopping/order/getOrderInfo.action");
            headers.put("origin", "https://trade.jd.com");
            headers.put("Content-Type", "application/json");
            headers.put("x-requested-with", "XMLHttpRequest");
            headers.put("upgrade-insecure-requests", "1");
            headers.put("sec-fetch-user", "?1");
            //stringListMap.forEach((key, list) -> list.clear());
            //stringListMap.clear();
            //MapUtil.clear(stringListMap);
            try {
                //打开订单页面
                stringListMap = Start.manager.get(new URI("https://trade.jd.com/shopping/order/getOrderInfo.action"), stringListMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            cookie = stringListMap.get("Cookie");
            headers.put("Cookie", cookie.get(0));
            String submitOrder = null;
            try {
                if (successTimes < Start.ok) {
                    //最重要的一步：提交订单
                    submitOrder = HttpUrlConnectionUtil.post(headers, "https://trade.jd.com/shopping/order/submitOrder.action", null);
                } else {
                    System.out.println("已抢购" + Start.ok + "件，请尽快完成付款");
                    //发邮件通知
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (submitOrder.contains("刷新太频繁了") || submitOrder.contains("抱歉，您访问的内容不存在")) {
                System.out.println("刷新太频繁了,您访问的内容不存在");
                continue;
            }
            JSONObject jsonObject = JSONObject.parseObject(submitOrder);
            String success = null;
            String message = null;
            if (jsonObject != null && jsonObject.get("success") != null) {
                success = jsonObject.get("success").toString();
            }
            if (jsonObject != null && jsonObject.get("message") != null) {
                message = jsonObject.get("message").toString();
            }

            if (success == "true") {
                System.out.println("抢购成功，请尽快完成付款");
                MailUtil.send(Start.mailAccount,"17562207@qq.com", "抢到东西啦", "京东抢购助手成功抢到1件商品，快付款吧，不然过期就没啦！", false);
                successTimes++;
            } else {
                if (message != null) {
                    System.out.println(message);
                } else if (submitOrder.contains("很遗憾没有抢到")) {
                    System.out.println("很遗憾没有抢到，再接再厉哦");
                } else if (submitOrder.contains("抱歉，您提交过快，请稍后再提交订单！")) {
                    System.out.println("抱歉，您提交过快，请稍后再提交订单！");
                } else if (submitOrder.contains("系统正在开小差，请重试~~")) {
                    System.out.println("系统正在开小差，请重试~~");
                } else if (submitOrder.contains("您多次提交过快")) {
                    System.out.println("您多次提交过快，请稍后再试");
                } else {
                    System.out.println("获取用户订单信息失败");
                }
            }
            times ++;
        }
    }

    public static void setIpProxy() {
        String ip = null;
        try {
            ip = HttpUrlConnectionUtil.ips().get(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] r1 = ip.split(":");
        System.out.println(ip);
        System.getProperties().setProperty("http.proxyHost", r1[0]);
        System.getProperties().setProperty("http.proxyPort", r1[1]);
        System.err.println(r1[0] + ":" + r1[1]);
    }
}
