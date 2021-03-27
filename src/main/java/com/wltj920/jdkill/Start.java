package com.wltj920.jdkill;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.extra.mail.MailAccount;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;
import cn.hutool.setting.dialect.Props;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author: lianghuan
 * @date: 2021/1/8 20:59
 */
public class Start {
    public static Props props = Props.getProp(new File(".", "config.properties").getAbsolutePath());
    public static MailAccount mailAccount;

    static {
        mailAccount = new MailAccount()
                .setSslEnable(props.getBool("EMAIL_SSL_ENABLED"))
                .setHost(props.getStr("EMAIL_SMTP_SERVER"))
                .setPort(props.getInt("EMAIL_SMTP_PORT"))
                .setUser(props.getStr("EMAIL_USERNAME"))
                .setPass(props.getStr("EMAIL_PASSWORD"))
                .setFrom(props.getStr("EMAIL_FROM"));
    }


    final static String headerAgent = "User-Agent";
    final static String headerAgentArg = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.135 Safari/537.36";
    final static String Referer = "Referer";
    final static String RefererArg = "https://passport.jd.com/new/login.aspx";
    //商品id
    static String pid = props.getStr("PID");
    //eid
    static String eid = props.getStr("EID");
    //fp
    static String fp = props.getStr("FP");
    //抢购数量
    volatile static Integer ok = props.getInt("QUANTITY");

    //一个线程抢购的次数
    volatile static Integer optCount = props.getInt("THREAD_OPT_COUNT");

    //开几个线程
    static Integer threadNum = props.getInt("THREAD_NUM");

    //提前抢购的便宜量，毫秒
    static Integer offsetMs = props.getInt("OFFSET");

    static CookieManager manager = new CookieManager();


    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException, ParseException {
        System.out.println("==================欢迎使用=================");
        System.out.println("此软件引发的任何问题，作者概不负责！！！请使用24小时内删除");
        System.out.println("作者：TeRny");
        System.out.println("京东抢购助手：v1.0.0");
        System.out.println("==================欢迎使用=================");
        CookieHandler.setDefault(manager);
        //获取venderId
//        String shopDetail = util.get(null, "https://item.jd.com/" + RushToPurchase.pid + ".html");
//        String venderID = shopDetail.split("isClosePCShow: false,\n" +
//                "                venderId:")[1].split(",")[0];
//        RushToPurchase.venderId = venderID;
        //登录
        Login.Login();
        //判断是否开始抢购
        judgePruchase();
        //开始抢购，线程池
        ExecutorService executorService = ThreadUtil.newExecutor(threadNum);
        for (int i = 0; i < threadNum; i++) {
            executorService.execute(new RushToPurchase());
        }
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.HOURS);
        Scanner scanner = new Scanner(System.in);
        System.out.println("任务结束，输入任一字符退出：");
        scanner.next();
        System.exit(0);
    }

    public static void judgePruchase() throws IOException, ParseException, InterruptedException {
        //获取开始时间
        Map<String, String> headers = new HashMap<>(2);
        headers.put(Start.headerAgent, Start.headerAgentArg);
        headers.put(Start.Referer, Start.RefererArg);
        //获取商店的信息，自动获取抢购时间
        cn.hutool.json.JSONObject shopDetail = JSONUtil.parseObj(HttpRequest.get("https://item-soa.jd.com/getWareBusiness?skuId=" + pid).addHeaders(headers).execute().body());
        if (shopDetail.get("yuyueInfo") != null) {
            String buyDate = JSONObject.parseObject(shopDetail.get("yuyueInfo").toString()).get("buyTime").toString();
            String startDate = buyDate.split("-202")[0] + ":00";
            //提前抢购的偏移量，毫秒
            DateTime startDateTime = DateUtil.parse(startDate).offset(DateField.MILLISECOND, offsetMs);
            startDate = startDateTime.toString("yyyy-MM-dd HH:mm:ss.SSS");
            long startTime = startDateTime.getTime();
            String price = shopDetail.getByPath("price.op").toString();
            boolean isStock = Boolean.parseBoolean(shopDetail.getByPath("stockInfo.isStock").toString());
            String productPage = HttpRequest.get("https://item.jd.com/" + Start.pid + ".html").addHeaders(headers).setReadTimeout(1000).execute().body();
            String productName = ReUtil.get("<title>.+】(.*)【.+</title>", productPage, 1);
            System.out.println("准备抢购的产品：" + productName + "，产品价格：" + price + "元，是否有货：" + isStock);
            System.out.println("产品官方抢购时间：" + buyDate);
            System.out.println("系统准备抢购时间：" + startDate);
            //获取京东时间
            long subTime = 0L;
            String testJdTime = HttpRequest.get("https://api.m.jd.com/client.action?functionId=queryMaterialProducts&client=wh5").addHeaders(headers).execute().body();
            if (!JSONUtil.isJson(testJdTime)) {
                System.out.println("【警告】获取京东时间失败，改用系统时间计算抢购");
            } else {
                //京东时间戳
                Long jdTime = Convert.toLong(JSONUtil.parseObj(testJdTime).get("currentTime2"));
                long localTime = System.currentTimeMillis();
                subTime = jdTime - localTime;
                System.out.println("检测到京东当前时间为：" + DateUtil.date(jdTime).toString("yyyy-MM-dd HH:mm:ss.SSS") + "，本地当前时间为：" +
                        DateUtil.date(localTime).toString("yyyy-MM-dd HH:mm:ss.SSS") + "，相差" + subTime + "毫秒。系统已自动修正！");
            }
            System.out.println("开始抢购任务，抢购时间到达会自动抢购！");
            //开始抢购
            while (true) {
                //京东时间=系统时间+相差毫秒
                long serverTime = System.currentTimeMillis() + subTime;
                if (startTime >= serverTime) {
                    //200毫秒检查一次
                    ThreadUtil.safeSleep(200L);
                } else {
                    break;
                }
            }
        }
    }
}
