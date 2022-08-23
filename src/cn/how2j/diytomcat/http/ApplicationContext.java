/**
 * @Title: ApplicationContext
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/14 9:00
 */
package cn.how2j.diytomcat.http;

import cn.how2j.diytomcat.catalina.Context;

import java.io.File;
import java.util.*;

public class ApplicationContext extends BaseServletContext {

    /**
     * ApplicationContext 继承了 BaseServletContext
     * 创建一个 attributesMap 属性，用于存放 属性
     * 内置一个 context， ApplicationContext 的很多方法，其实就是调用的它。
     */
    private Map<String, Object> attributesMap;
    private Context context;

    public ApplicationContext(Context context) {
        this.attributesMap = new HashMap<>();
        this.context = context;
    }

    public void removeAttribute(String name) {
        attributesMap.remove(name);
    }

    public void setAttribute(String name, Object value) {
        attributesMap.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributesMap.get(name);
    }

    public Enumeration<String> getAttributeNames() {
        Set<String> keys = attributesMap.keySet();
        return Collections.enumeration(keys);
    }

    /*重写了 getRealPath , 来获取硬盘上的真实路径*/
    public String getRealPath(String path) {
        return new File(context.getDocBase(), path).getAbsolutePath();
    }

}
