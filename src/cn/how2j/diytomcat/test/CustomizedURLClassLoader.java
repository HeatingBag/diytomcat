/**
 * @Title: CustomizedURLClassLoader
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/13 9:45
 */
package cn.how2j.diytomcat.test;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/*1. 创建类 CustomizedURLClassLoader 继承了 URLClassLoader*/
public class CustomizedURLClassLoader extends URLClassLoader {

    /* 2. 提供带参构造方法，表示这个加载器会到 urls 对应的这些文件里去找类文件。
     * url: universal resources locator(通用资源定位器)
     * */
    public CustomizedURLClassLoader(URL[] urls) {
        super(urls);
    }

    public static void main(String[] args) throws Exception {
        /*3. 准备 url, 指向 URLClassLoader 准备工作 里准备的 jar。 注意 url 前面需要写上 file:*/
        URL url = new URL("file:e:/project/diytomcat/jar_4_test/test.jar");

        /*4. 基于 url 创建 CustomizedURLClassLoader 类*/
        URL[] urls = new URL[]{url};
        CustomizedURLClassLoader loader = new CustomizedURLClassLoader(urls);

        Class<?> how2jClass = loader.loadClass("cn.how2j.diytomcat.test.HOW2J");

        Object o = how2jClass.newInstance();
        Method m = how2jClass.getMethod("hello");
        m.invoke(o);

        System.out.println(how2jClass.getClassLoader());
    }
}
