/**
 * @Title: Host
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/11 8:31
 */
package cn.how2j.diytomcat.catalina;

import cn.how2j.diytomcat.util.Constant;
import cn.how2j.diytomcat.util.ServerXMLUtil;
import cn.how2j.diytomcat.watcher.WarFileWatcher;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Host {
    private String name;
    private Map<String, Context> contextMap;
    private Engine engine;

    public Host(String name, Engine engine) {
        this.contextMap = new HashMap<>();
        this.name = name;
        this.engine = engine;

        scanContextsOnWebAppsFolder();
        scanContextsInServerXML();
        scanWarOnWebAppsFolder();
        new WarFileWatcher(this).start();

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //创建scanContextsInServerXML， 通过 ServerXMLUtil 获取 context, 放进 contextMap里。
    private void scanContextsInServerXML() {
        List<Context> contexts = ServerXMLUtil.getContexts(this);
        for (Context context : contexts) {
            contextMap.put(context.getPath(), context);
        }
    }

    //创建 scanContextsOnWebAppsFolder 方法，用于扫描 webapps 文件夹下的目录，对这些目录调用 loadContext 进行加载。
    private void scanContextsOnWebAppsFolder() {
        File[] folders = Constant.webappsFolder.listFiles();
        for (File folder : folders) {
            if (!folder.isDirectory())
                continue;
            loadContext(folder);
        }
    }

    /**
     * 加载这个目录成为 Context 对象。
     * 如果是 ROOT，那么path 就是 "/", 如果是 a, 那么path 就是 "/a", 然后根据 path 和 它们所处于的路径创建 Context 对象。
     * 然后把这些对象保存进 contextMap，方便后续使用。
     */
    private void loadContext(File folder) {

        String path = folder.getName();

        if ("ROOT".equals(path))
            path = "/";
        else
            path = "/" + path;

        String docBase = folder.getAbsolutePath();
        Context context = new Context(path, docBase, this, true);

        contextMap.put(context.getPath(), context);

    }

    public Context getContext(String path) {
        return contextMap.get(path);
    }

    /**
     * 1. 先保存 path, docBase, relodable 这些基本信息
     * 2. 调用 context.stop() 来暂停
     * 3. 把它从 contextMap 里删掉
     * 4. 根据刚刚保存的信息，创建一个新的context
     * 5. 设置到 contextMap 里
     * 6. 开始和结束打印日志
     */
    public void reload(Context context) {
        LogFactory.get().info("Reloading Context with name [{}] has started", context.getPath());
        String path = context.getPath();
        String docBase = context.getDocBase();
        boolean reloadable = context.isReloadable();
        //stop
        context.stop();
        //remove
        contextMap.remove(path);
        // allocate new context(分配新的上下文)
        Context newContext = new Context(path, docBase, this, reloadable);
        // assign it to map(将它分配给映射)
        contextMap.put(newContext.getPath(), newContext);
        LogFactory.get().info("Reloading Context with name [{}] has completed", context.getPath());

    }

    public void load(File folder) {
        String path = folder.getName();
        if ("ROOT".equals(path))
            path = "/";
        else
            path = "/" + path;

        String docBase = folder.getAbsolutePath();
        Context context = new Context(path, docBase, this, false);
        contextMap.put(context.getPath(), context);
    }

    public void loadWar(File warFile) {
        String fileName = warFile.getName();
        String folderName = StrUtil.subBefore(fileName, ".", true);
        //看看是否已经有对应的 Context了
        Context context = getContext("/" + folderName);
        if (null != context)
            return;

        //先看是否已经有对应的文件夹
        File folder = new File(Constant.webappsFolder, folderName);
        if (folder.exists())
            return;

        //移动war文件，因为jar 命令只支持解压到当前目录下
        File tempWarFile = FileUtil.file(Constant.webappsFolder, folderName, fileName);
        File contextFolder = tempWarFile.getParentFile();
        contextFolder.mkdir();
        FileUtil.copyFile(warFile, tempWarFile);
        //解压
        String command = "jar xvf " + fileName;
        System.out.println(command);
        Process p = RuntimeUtil.exec(null, contextFolder, command);
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //解压之后删除临时war
        tempWarFile.delete();
        //然后创建新的 Context
        load(contextFolder);
    }

    private void scanWarOnWebAppsFolder() {
        File folder = FileUtil.file(Constant.webappsFolder);
        File[] files = folder.listFiles();
        for (File file : files) {
            if (!file.getName().toLowerCase().endsWith(".war"))
                continue;
            loadWar(file);
        }
    }
}
