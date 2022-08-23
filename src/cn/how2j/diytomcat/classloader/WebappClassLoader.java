/**
 * @Title: WebappClassLoader
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/13 16:49
 */
package cn.how2j.diytomcat.classloader;

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class WebappClassLoader extends URLClassLoader {

    public WebappClassLoader(String docBase, ClassLoader commonClassLoader) {
        super(new URL[]{}, commonClassLoader);

        /**
         * 1. 扫描 Context 对应的 docBase 下的 classes 和 lib
         * 2. 把 jar 通过 addURL 加进去
         * 3. 把 classes 目录，通过 addURL 加进去。 注意，因为是目录，所以加的时候，要在结尾跟上 "/" , URLClassLoader 才会把它当作目录来处理
         * */
        try {
            File webinfFolder = new File(docBase, "WEB-INF");
            File classesFolder = new File(webinfFolder, "classes");
            File libFolder = new File(webinfFolder, "lib");
            URL url;
            url = new URL("file:" + classesFolder.getAbsolutePath() + "/");
            this.addURL(url);
            List<File> jarFiles = FileUtil.loopFiles(libFolder);
            for (File file : jarFiles) {
                url = new URL("file:" + file.getAbsolutePath());
                this.addURL(url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
