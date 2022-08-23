/**
 * @Title: Context
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/8 15:53
 */
package cn.how2j.diytomcat.catalina;

import cn.how2j.diytomcat.classloader.WebappClassLoader;
import cn.how2j.diytomcat.exception.WebConfigDuplicatedException;
import cn.how2j.diytomcat.http.ApplicationContext;
import cn.how2j.diytomcat.http.StandardServletConfig;
import cn.how2j.diytomcat.util.ContextXMLUtil;
import cn.how2j.diytomcat.watcher.ContextFileChangeWatcher;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.log.LogFactory;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import java.io.File;
import java.util.*;

public class Context {

    /**
     * 热加载的流程图：
     * 1. 首先创建 Context
     * 2. 创建 Context 之后，创建个专属的监听器，用于监听当前 docBase 下文件的变化
     * 3. 开始持续进行监听中
     * 4. 判断发生变化的文件的后缀名
     * 4.1 如果不是 class, jar 或者 xml 就不管它，继续监听
     * 4.2 如果是，那么就先关闭监听，然后重载Context, 并且刷新 Host 里面的 contextMap
     * 5. 重载其实就是重新创建一个新的 Context, 就又回到 1了
     * */

    /**
     * path 表示访问的路径
     * docBase 表示对应在文件系统中的位置
     * contextWebXmlFile对应web.xml
     */
    private String path;
    private String docBase;
    private File contextWebXmlFile;

    /**
     * <servlet>
     * <servlet-name>HelloServlet</servlet-name> -----servletName
     * <servlet-class>cn.how2j.diytomcat.webappservlet.HelloServlet</servlet-class>-----servletClassName className
     * </servlet>
     * <p>
     * <servlet-mapping>
     * <servlet-name>HelloServlet</servlet-name>-----servletName
     * <url-pattern>/hello</url-pattern>-----url
     * </servlet-mapping>
     */
    private Map<String, String> url_servletClassName;
    private Map<String, String> url_servletName;
    private Map<String, String> servletName_className;
    private Map<String, String> className_servletName;


    private Map<String, List<String>> url_filterClassName;
    private Map<String, List<String>> url_FilterNames;
    private Map<String, String> filterName_className;
    private Map<String, String> className_filterName;
    private Map<String, Map<String, String>> filter_className_init_params;

    //声明 servlet_className_init_params 用于存放初始化信息
    private Map<String, Map<String, String>> servlet_className_init_params;

    private List<String> loadOnStartupServletClassNames;

    private WebappClassLoader webappClassLoader;

    /*声明一个 Host 和 reloadable 属性*/
    private Host host;
    private boolean reloadable;

    private ContextFileChangeWatcher contextFileChangeWatcher;

    private ServletContext servletContext;

    /*准备个 map 作为存放 servlet 池子*/
    private Map<Class<?>, HttpServlet> servletPool;

    private Map<String, Filter> filterPool;

    private List<ServletContextListener> listeners;

    public Context(String path, String docBase, Host host, boolean reloadable) {
        TimeInterval timeInterval = DateUtil.timer();
        this.host = host;
        this.reloadable = reloadable;

        this.path = path;
        this.docBase = docBase;
        this.contextWebXmlFile = new File(docBase, ContextXMLUtil.getWatchedResource());

        this.url_servletClassName = new HashMap<>();
        this.url_servletName = new HashMap<>();
        this.servletName_className = new HashMap<>();
        this.className_servletName = new HashMap<>();

        this.url_filterClassName = new HashMap<>();
        this.url_FilterNames = new HashMap<>();
        this.filterName_className = new HashMap<>();
        this.className_filterName = new HashMap<>();
        this.filter_className_init_params = new HashMap<>();

        this.servlet_className_init_params = new HashMap<>();

        this.loadOnStartupServletClassNames = new ArrayList<>();

        this.servletContext = new ApplicationContext(this);

        this.servletPool = new HashMap<>();

        this.filterPool = new HashMap<>();

        listeners = new ArrayList<ServletContextListener>();

        /**
         * 在构造方法中初始化它，这里的 Thread.currentThread().getContextClassLoader() 就可以获取到 Bootstrap 里通过
         * Thread.currentThread().setContextClassLoader(commonClassLoader); 设置的 commonClassLoader.
         * 然后 根据 Tomcat 类加载器体系 commonClassLoader 作为 WebappClassLoader 父类存在。
         * */
        ClassLoader commonClassLoader = Thread.currentThread().getContextClassLoader();
        this.webappClassLoader = new WebappClassLoader(docBase, commonClassLoader);

        deploy();
    }

    /*重载方法，通过它的父对象来重载它*/
    public void reload() {
        host.reload(this);
    }

    private void deploy() {

        loadListeners();

        TimeInterval timeInterval = DateUtil.timer();

        /*Deploying web application directory:部署web应用程序目录*/
        LogFactory.get().info("Deploying web application directory {}", this.docBase);

        init();

        /*web应用目录{}部署已在{}ms中完成*/
        LogFactory.get().info("Deployment of web application directory {} has finished in {} ms", this.getDocBase(), timeInterval.intervalMs());

        /*在deploy 方法中初始化contextFileChangeWatcher ，并启动*/
        if (reloadable) {
            contextFileChangeWatcher = new ContextFileChangeWatcher(this);
            contextFileChangeWatcher.start();
        }

        JspC c = new JspC();
        new JspRuntimeContext(servletContext, c);
    }

    private void init() {
        /**
         * 初始化方法
         * 先判断是否有 web.xml 文件，如果没有就返回了
         * 然后判断是否重复
         * 接着进行 web.xml 的解析
         * */
        if (!contextWebXmlFile.exists())
            return;

        try {
            checkDuplicated();
        } catch (WebConfigDuplicatedException e) {
            e.printStackTrace();
            return;
        }

        String xml = FileUtil.readUtf8String(contextWebXmlFile);
        Document d = Jsoup.parse(xml);
        parseServletMapping(d);
        parseFilterMapping(d);
        parseServletInitParams(d);
        parseFilterInitParams(d);

        initFilter();

        parseLoadOnStartup(d);
        handleLoadOnStartup();

        fireEvent("init");
    }

    private void parseServletMapping(Document d) {
        // url_ServletName
        Elements mappingurlElements = d.select("servlet-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {
            String urlPattern = mappingurlElement.text();
            String servletName = mappingurlElement.parent().select("servlet-name").first().text();
            url_servletName.put(urlPattern, servletName);
        }

        // servletName_className / className_servletName
        Elements servletNameElements = d.select("servlet servlet-name");
        for (Element servletNameElement : servletNameElements) {
            String servletName = servletNameElement.text();
            String servletClass = servletNameElement.parent().select("servlet-class").first().text();
            servletName_className.put(servletName, servletClass);
            className_servletName.put(servletClass, servletName);
        }

        // url_servletClassName
        Set<String> urls = url_servletName.keySet();
        for (String url : urls) {
            String servletName = url_servletName.get(url);
            String servletClassName = servletName_className.get(servletName);
            url_servletClassName.put(url, servletClassName);
        }
    }

    private void checkDuplicated(Document d, String mapping, String desc) throws WebConfigDuplicatedException {
        Elements elements = d.select(mapping);
        // 判断逻辑是放入一个集合，然后把集合排序之后看两临两个元素是否相同
        List<String> contents = new ArrayList<>();
        for (Element e : elements) {
            contents.add(e.text());
        }

        Collections.sort(contents);

        for (int i = 0; i < contents.size() - 1; i++) {
            String contentPre = contents.get(i);
            String contentNext = contents.get(i + 1);
            if (contentPre.equals(contentNext)) {
                throw new WebConfigDuplicatedException(StrUtil.format(desc, contentPre));
            }
        }
    }

    private void checkDuplicated() throws WebConfigDuplicatedException {
        String xml = FileUtil.readUtf8String(contextWebXmlFile);
        Document d = Jsoup.parse(xml);

        checkDuplicated(d, "servlet-mapping url-pattern", "servlet url 重复,请保持其唯一性:{} ");
        checkDuplicated(d, "servlet servlet-name", "servlet 名称重复,请保持其唯一性:{} ");
        checkDuplicated(d, "servlet servlet-class", "servlet 类名重复,请保持其唯一性:{} ");
    }

    public String getServletClassName(String uri) {
        return url_servletClassName.get(uri);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDocBase() {
        return docBase;
    }

    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    public WebappClassLoader getWebappClassLoader() {
        return webappClassLoader;
    }

    public void stop() {
        webappClassLoader.stop();
        contextFileChangeWatcher.stop();

        destroyServlets();

        fireEvent("destroy");
    }

    public boolean isReloadable() {
        return reloadable;
    }

    public void setReloadable(boolean reloadable) {
        this.reloadable = reloadable;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    /*提供 getServlet 方法，根据类对象来获取 servlet 对象。*/
    public synchronized HttpServlet getServlet(Class<?> clazz)
            throws InstantiationException, IllegalAccessException, ServletException {

        /**
         * 1.根据传进来的类对象clazz去取实例
         * 2.第一次肯定是空的，就newInstance一个新的，并放进池里
         * 3.池里有了实例，这样以后再来取的时候就还是第一次创建那个，就保证了单例
         * */
        HttpServlet servlet = servletPool.get(clazz);
        if (null == servlet) {
            servlet = (HttpServlet) clazz.newInstance();
            ServletContext servletContext = this.getServletContext();

            String className = clazz.getName();
            String servletName = className_servletName.get(className);
            Map<String, String> initParameters = servlet_className_init_params.get(className);
            ServletConfig servletConfig = new StandardServletConfig(servletContext, servletName, initParameters);

            servlet.init(servletConfig);
            servletPool.put(clazz, servlet);
        }
        return servlet;
    }

    //parseServletInitParams 方法从 web.xml 中解析初始化参数
    private void parseServletInitParams(Document d) {
        Elements servletClassNameElements = d.select("servlet-class");
        for (Element servletClassNameElement : servletClassNameElements) {
            String servletClassName = servletClassNameElement.text();

            Elements initElements = servletClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;

            Map<String, String> initParams = new HashMap<>();

            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }
            servlet_className_init_params.put(servletClassName, initParams);
        }
        System.out.println("class_name_init_params:" + servlet_className_init_params);
    }

    private void destroyServlets() {
        Collection<HttpServlet> servlets = servletPool.values();
        for (HttpServlet servlet : servlets) {
            servlet.destroy();
        }
    }

    public void parseLoadOnStartup(Document d) {
        Elements es = d.select("load-on-startup");
        for (Element e : es) {
            String loadOnStartupServletClassName = e.parent().select("servlet-class").text();
            loadOnStartupServletClassNames.add(loadOnStartupServletClassName);
        }
    }

    public void handleLoadOnStartup() {
        for (String loadOnStartupServletClassName : loadOnStartupServletClassNames) {
            try {
                Class<?> clazz = webappClassLoader.loadClass(loadOnStartupServletClassName);
                getServlet(clazz);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | ServletException e) {
                e.printStackTrace();
            }
        }
    }

    public WebappClassLoader getWebClassLoader() {
        return webappClassLoader;
    }

    public void parseFilterMapping(Document d) {
        //filter_url_name
        Elements mappingurlElements = d.select("filter-mapping url-pattern");
        for (Element mappingurlElement : mappingurlElements) {

            String urlPattern = mappingurlElement.text();
            String filterName = mappingurlElement.parent().select("filter-name").first().text();

            List<String> filterNames = url_FilterNames.get(urlPattern);
            if (null == filterNames) {
                filterNames = new ArrayList<>();
                url_FilterNames.put(urlPattern, filterNames);
            }
            filterNames.add(filterName);
        }
        // class_name_filter_name
        Elements filterNameElements = d.select("filter filter-name");
        for (Element filterNameElement : filterNameElements) {
            String filterName = filterNameElement.text();
            String filterClass = filterNameElement.parent().select("filter-class").first().text();
            filterName_className.put(filterName, filterClass);
            className_filterName.put(filterClass, filterName);
        }
        // url_filterClassName
        Set<String> urls = url_FilterNames.keySet();
        for (String url : urls) {
            List<String> filterNames = url_FilterNames.get(url);
            if (null == filterNames) {
                filterNames = new ArrayList<>();
                url_FilterNames.put(url, filterNames);
            }
            for (String filterName : filterNames) {
                String filterClassName = filterName_className.get(filterName);
                List<String> filterClassNames = url_filterClassName.get(url);
                if (null == filterClassNames) {
                    filterClassNames = new ArrayList<>();
                    url_filterClassName.put(url, filterClassNames);
                }
                filterClassNames.add(filterClassName);
            }
        }
    }

    private void parseFilterInitParams(Document d) {
        Elements filterClassNameElements = d.select("filter-class");
        for (Element filterClassNameElement : filterClassNameElements) {
            String filterClassName = filterClassNameElement.text();

            Elements initElements = filterClassNameElement.parent().select("init-param");
            if (initElements.isEmpty())
                continue;

            Map<String, String> initParams = new HashMap<>();

            for (Element element : initElements) {
                String name = element.select("param-name").get(0).text();
                String value = element.select("param-value").get(0).text();
                initParams.put(name, value);
            }
            filter_className_init_params.put(filterClassName, initParams);
        }
    }

    private void initFilter() {
        Set<String> classNames = className_filterName.keySet();
        for (String className : classNames) {
            try {
                Class clazz = this.getWebClassLoader().loadClass(className);
                Map<String, String> initParameters = filter_className_init_params.get(className);
                String filterName = className_filterName.get(className);

                FilterConfig filterConfig = new StandardFilterConfig(servletContext, filterName, initParameters);

                Filter filter = filterPool.get(clazz);
                if (null == filter) {
                    filter = (Filter) ReflectUtil.newInstance(clazz);
                    filter.init(filterConfig);
                    filterPool.put(className, filter);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    //获取匹配了的过滤器集合
    public List<Filter> getMatchedFilters(String uri) {
        List<Filter> filters = new ArrayList<>();
        Set<String> patterns = url_filterClassName.keySet();
        Set<String> matchedPatterns = new HashSet<>();

        for (String pattern : patterns) {
            if (match(pattern, uri)) {
                matchedPatterns.add(pattern);
            }
        }

        Set<String> matchedFilterClassNames = new HashSet<>();
        for (String pattern : matchedPatterns) {
            List<String> filterClassName = url_filterClassName.get(pattern);
            matchedFilterClassNames.addAll(filterClassName);
        }
        for (String filterClassName : matchedFilterClassNames) {
            Filter filter = filterPool.get(filterClassName);
            filters.add(filter);
        }
        return filters;
    }

    private boolean match(String pattern, String uri) {
        // 完全匹配
        if (StrUtil.equals(pattern, uri))
            return true;

        // /* 通配符模式
        if (StrUtil.equals(pattern, "/*"))
            return true;

        // 后缀名 /*.jsp
        if (StrUtil.startWith(pattern, "/*.")) {
            String patternExtName = StrUtil.subAfter(pattern, '.', false);
            String uriExtName = StrUtil.subAfter(uri, '.', false);
            if (StrUtil.equals(patternExtName, uriExtName))
                return true;
        }
        return false;
    }

    public void addListener(ServletContextListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServletContextListener listener) {
        listeners.remove(listener);
    }

    private void loadListeners() {
        try {
            if (!contextWebXmlFile.exists())
                return;

            String xml = FileUtil.readUtf8String(contextWebXmlFile);
            Document d = Jsoup.parse(xml);

            Elements es = d.select("listener listener-class");
            for (Element e : es) {
                String listenerClassName = e.text();

                Class<?> clazz = this.getWebClassLoader().loadClass(listenerClassName);
                ServletContextListener listener = (ServletContextListener) clazz.newInstance();
                addListener(listener);
            }
        } catch (IORuntimeException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void fireEvent(String type) {
        ServletContextEvent event = new ServletContextEvent(servletContext);
        for (ServletContextListener servletContextListener : listeners) {
            if ("init".equals(type))
                servletContextListener.contextInitialized(event);
            if ("destroy".equals(type))
                servletContextListener.contextDestroyed(event);
        }
    }
}
