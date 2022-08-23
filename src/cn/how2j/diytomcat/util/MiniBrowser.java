/**
 * @Title: MiniBrowser
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/5 16:26
 */
package cn.how2j.diytomcat.util;

import cn.hutool.http.HttpUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MiniBrowser {

    /**
     * 核心就是getHttpString，编写，拼接Request Headers发给网页
     * 而getContentString的作用就是取到Response Headers（响应标头）最后的那个值，这里就是 hello diytomcat
     */
    public static void main(String[] args) throws Exception {
        //初始化请求地址,这个请求地址就是待会会去链接的地址:http://static.how2j.cn/diytomcat.html
        String url = "http://static.how2j.cn/diytomcat.html";

        //调用该方法getContentString,获取 http请求返回值的返回内容(简而言之就是去除掉了返回头的那些字符串)
        String contentString = getContentString(url, false);
        System.out.println(contentString);

        //这个方法就是获取全部的Http返回内容的字符串的方法了 response
        String httpString = getHttpString(url, false);
        System.out.println(httpString);
    }

    public static byte[] getContentBytes(String url, Map<String, Object> params, boolean isGet) {
        return getContentBytes(url, false, params, isGet);
    }

    public static byte[] getContentBytes(String url, boolean gzip) {

        return getContentBytes(url, gzip, null, true);
    }

    public static byte[] getContentBytes(String url) {
        return getContentBytes(url, false, null, true);
    }

    public static String getContentString(String url, Map<String, Object> params, boolean isGet) {
        return getContentString(url, false, params, isGet);

    }

    public static String getContentString(String url, boolean gzip) {
        return getContentString(url, gzip, null, true);
    }

    public static String getContentString(String url) {
        return getContentString(url, false, null, true);
    }

    public static String getContentString(String url, boolean gzip, Map<String, Object> params, boolean isGet) {
        byte[] result = getContentBytes(url, gzip, params, isGet);
        if (null == result)
            return null;
        try {
            return new String(result, "utf-8").trim();
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    //这里是真正的逻辑,就是与请求地址建立连接的逻辑,是整个类的核心,其他方法都只是处理这个方法返回值的一些逻辑而已
    //这个方法的根本目的也就是取到返回值最后空格下面的值(具体内容部分,在这个代码中是hello diytomcat)
    public static byte[] getContentBytes(String url, boolean gzip, Map<String, Object> params, boolean isGet) {
        byte[] response = getHttpBytes(url, gzip, params, isGet);
        /**
         * 这个doubleReturnq其实是这样来的:我们获取的返回值正常其实是这样的
         * (响应标头部分)
         * xxxxx（X-Swift-CacheTime: 3600）
         * xxxxx（Timing-Allow-Origin: *）
         * xxxxx（EagleId: 76b4381516571558957318584e）
         *
         * xxx(具体内容部分,在这个代码中是hello diytomcat)
         * 也就是说响应头部分和具体内容部分其实隔了一行, \r表示回到行首\n表示换到下一行,那么\r\n就相当于说先到了空格一行的那一行的行首,接着又到了具体内容的那部分的行首
         *第一个\r\n代表这行结束，第二个才是换行，这样doubleReturn才能获取到hello diytomcat
         * */
        byte[] doubleReturn = "\r\n\r\n".getBytes();

        //接着这里初始化一个记录值,做记录用，这个用0或者-1都没啥意义，都一样。
        int pos = -1;
        //开始遍历返回内容
        for (int i = 0; i < response.length - doubleReturn.length; i++) {
            //这里的意思就是不断去初始化一个数组(从原数组进行拷贝),目的其实是为了获取到\r\n这一行的起始位置
            // copyOfRange(int[] original, int from, int to)
            // 第一个参数表示源数组
            // 第二个参数表示开始位置(取得到)
            // 第三个参数表示结束位置(取不到)
            byte[] temp = Arrays.copyOfRange(response, i, i + doubleReturn.length);

            //来到这里,就是比较内容,当走到这里,说明temp这个字节数组的内容就是\r\n\r\n的内容了,说明我们找到了他的其实位置
            if (Arrays.equals(temp, doubleReturn)) {
                //将pos等于i,记录位置
                pos = i;
                break;
            }
        }
        //如果没记录到,那就说明压根没具体内容,那其实就是null
        if (-1 == pos)
            return null;

        //接着pos就是\r\n\n的第一个\的这个位置,加上\r\n\r\n的长度,相当于来到了具体内容的其实位置
        //pos =pos + doubleReturn.length;
        pos += doubleReturn.length;
        //最后,确定了具体内容是在哪个字节开始,就拷贝这部分内容返回
        byte[] result = Arrays.copyOfRange(response, pos, response.length);
        return result;
    }


    public static String getHttpString(String url, boolean gzip) {
        return getHttpString(url, gzip, null, true);
    }

    public static String getHttpString(String url) {
        return getHttpString(url, false, null, true);
    }

    public static String getHttpString(String url, boolean gzip, Map<String, Object> params, boolean isGet) {
        byte[] bytes = getHttpBytes(url, gzip, params, isGet);
        return new String(bytes).trim();
    }

    public static String getHttpString(String url, Map<String, Object> params, boolean isGet) {
        return getHttpString(url, false, params, isGet);
    }

    public static byte[] getHttpBytes(String url, boolean gzip, Map<String, Object> params, boolean isGet) {
        String method = isGet ? "GET" : "POST";
        //首先初始化一个返回值,这个返回值是一个字节数组,utf-8编码的
        byte[] result = null;

        try {
            //通过url来new一个URL对象,这样你就不用自己去截取他的端口啊或者请求路径啥的,可以直接调他的方法获取
            URL u = new URL(url);

            //开启一个socket链接,client(客户端)指的就是你现在的这台计算机
            Socket client = new Socket();
            //获取到端口号,要是端口号是-1,那就默认取80端口(这个端口也是web常用端口)
            int port = u.getPort();
            if (-1 == port)
                port = 80;
            //这个是socket编程的内容,简单来说就是通过一个host+端口,和这个url建立连接
            InetSocketAddress inetSocketAddress = new InetSocketAddress(u.getHost(), port);
            //开始连接了,1000是超时时间,等于说超过1秒就算你超时了
            client.connect(inetSocketAddress, 1000);
            //准备初始化请求标头
            Map<String, String> requestHeaders = new HashMap<>();

            //这几个参数都是http请求时会带上的请求标头
            /**
             * Host: static.how2j.cn
             * Accept: text/html
             * Connection: keep-alive(这里应该是浏览器的原因，这里虽然指定是关闭，但不管怎么访问，都是保持连接)
             * User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36
             * User-Agent(用户代理)这里网页里显示的浏览器的版本 Chrome/103.0.0.0 ，这里并没有看到 how2j mini brower / java1.8 等内容，存疑！！！
             * */
            requestHeaders.put("Host", u.getHost() + ":" + port);
            requestHeaders.put("Accept", "text/html");
            requestHeaders.put("Connection", "close");
            requestHeaders.put("User-Agent", "how2j mini brower / java1.8");

            //gzip是确定客户端或服务器端是否支持压缩
            //Accept-Encoding: gzip, deflate
            if (gzip)
                requestHeaders.put("Accept-Encoding", "gzip");

            /**
             * 获取到path,其实就是/diytomcat.html,如果没有的话就默认是/
             * 主方法里  String url = "http://static.how2j.cn/diytomcat.html";
             * */
            String path = u.getPath();
            if (path.length() == 0)
                path = "/";

            if (null != params && isGet) {
                String paramsString = HttpUtil.toParams(params);
                path = path + "?" + paramsString;
            }

            //接着开始拼接请求的字符串,其实所谓的请求头和请求内容就是这么一串字符串拼接出来
            //firstLine:请求标头的第一行 GET /diytomcat.html HTTP/1.1 \r\n回车换行
            String firstLine = method + " " + path + " HTTP/1.1\r\n";

            StringBuffer httpRequestString = new StringBuffer();

            httpRequestString.append(firstLine);
            //把requestHeaders那个 Map<String, String> 集合的东西放在Set<String>集合中
            Set<String> headers = requestHeaders.keySet();
            //遍历header的那个map进行拼接
            for (String header : headers) {
                String headerLine = header + ":" + requestHeaders.get(header) + "\r\n";
                httpRequestString.append(headerLine);
            }

            if (null != params && !isGet) {
                String paramsString = HttpUtil.toParams(params);
                httpRequestString.append("\r\n");
                httpRequestString.append(paramsString);
            }

            /**
             *走到这的时候,httpRequestString已经拼接好了(请求标头),内容是:
             * GET /diytomcat.html HTTP/1.1
             * Accept:text/html
             * Connection:close（这里存疑，网页里显示的是 keep-alive）
             * User-Agent:how2j mini browser / java1.8 （这里也是，并没有相关的内容）
             * Host:static.how2j.cn:80
             * */
            //通过输出流,将这么一串字符串输出给连接的地址,后面的true是autoFlush,表示开始自动刷新
            PrintWriter pWriter = new PrintWriter(client.getOutputStream(), true);
            pWriter.println(httpRequestString);
            /**
             * 这时候你已经将需要的请求所需的字符串发给上面那个url了,其实所谓的http协议就是这样,你发给他这么一串符合规范的字符串,
             * 他就给你响应,接着他那边就给你返回数据，所以这时候我们开启一个输出流
             *
             * 这里刚好总结一下所有的请求标头（Request Headers）
             * 第一行一定是 GET /diytomcat.html HTTP/1.1
             * 然后就是：
             * Accept（接受）: text/html,application/xhtml+xml.......（后面还有很多）
             * Accept-Encoding（接受编码）: gzip, deflate
             * Accept-Language（接受语言）: zh-CN,zh;q=0.9
             * Cache-Control（缓存控制）: max-age=0
             * Connection（连接）: keep-alive
             * Cookie（曲奇，饼干，这里是指浏览网页后存储在计算机的缓存文件）: UM_distinctid=17f304eb4cfbbd-030558f1bfd497-a3e3164-144000-17f304eb4d02fe;。。。。。
             * Host（主机）: static.how2j.cn
             * If-Modified-Since（modify 修改；Since 自……以后；这里指的是修改时间）: Sun, 20 Jun 2021 13:21:14 GMT
             * If-None-Match（Match 匹配 这里不太理解，如果没有匹配？）: "BD907647812A5C4DDE266F4E8A599BDB"
             * Upgrade-Insecure-Requests（中译：升级不安全的请求）: 1
             * User-Agent（用户代理）: Mozilla/5.0 (Windows NT 10.0;。。。。。
             * 。。。。。。。。
             * */
            InputStream is = client.getInputStream();

            result = readBytes(is, true);
            client.close();

        } catch (Exception e) {
            e.printStackTrace();
            //这里是将返回的异常信息进行字节数组编码,其实就是兼容这个方法
            try {
                result = e.toString().getBytes("utf-8");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        }
        return result;
    }

    public static byte[] readBytes(InputStream is, boolean fully) throws IOException {

        /*准备一个 1024长度的缓存，不断地从输入流读取数据到这个缓存里面去。*/
        int buffer_size = 1024;
        byte buffer[] = new byte[buffer_size];
        /*这里初始化一个输出流,待会存取url返回给我们的数据用*/
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {

            /*1. 从输入流获取数据,调read方法存到buffer数组中，如果读取到的长度是 -1，那么就表示到头了，就停止循环*/
            int length = is.read(buffer);
            if (-1 == length)
                break;

            /*3. 把读取到的数据，根据实际长度，写出到 一个字节数组输出流里。*/
            baos.write(buffer, 0, length);

            /*2. 如果读取到的长度与buffer_size不一致的时候, 说明也读完了，
             * 举个例子,如果你的数据是1025字节,当你第二次循环的时候就是只有一个字节了,这时候就说明处理完这一个字节的数组就可以结束了,因为已经没数据了
             * */
            if (!fully && length != buffer_size)
                break;
        }
        /*把 ByteArrayOutputStream 里的直接数组导出来，方便后续使用,用这种方式，就可以达到应取尽取的效果了*/
        byte[] result = baos.toByteArray();
        return result;

    }


}
