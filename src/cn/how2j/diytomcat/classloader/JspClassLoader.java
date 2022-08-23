/**
 * @Title: JspClassLoader
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/18 18:04
 */
package cn.how2j.diytomcat.classloader;

import cn.how2j.diytomcat.catalina.Context;
import cn.how2j.diytomcat.util.Constant;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class JspClassLoader extends URLClassLoader {

    //map 属性就是用来 做 jsp 文件和 JspClassLoader 的映射的
    private static Map<String, JspClassLoader> map = new HashMap<>();

    //让Jsp和JspClassLoader 取消关联
    public static void invalidJspClassLoader(String uri, Context context) {
        String key = context.getPath() + "/" + uri;
        map.remove(key);
    }

    //获取jsp 对应的 JspClassLoader , 如果没有就新创建一个
    public static JspClassLoader getJspClassLoader(String uri, Context context) {
        String key = context.getPath() + "/" + uri;
        JspClassLoader loader = map.get(key);
        if (null == loader) {
            loader = new JspClassLoader(context);
            map.put(key, loader);
        }
        return loader;
    }

    /**
     * 构造方法， JspClassLoader 会基于 WebClassLoader 来创建。
     * 然后根据 context 的信息获取到 %TOMCAT_HOME%/work 目录下对应的目录， 并且把这个目录作为 URL 加入到当前 ClassLoader 里，
     * 这样通过当前 JspClassLoader 加载jsp类的时候，就可以找到对应的类文件了。
     */
    private JspClassLoader(Context context) {
        super(new URL[]{}, context.getWebClassLoader());

        try {
            String subFolder;
            String path = context.getPath();
            if ("/".equals(path))
                subFolder = "_";
            else
                subFolder = StrUtil.subAfter(path, '/', false);

            File classesFolder = new File(Constant.workFolder, subFolder);
            URL url = new URL("file:" + classesFolder.getAbsolutePath() + "/");
            this.addURL(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
