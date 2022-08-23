/**
 * @Title: StandardFilterConfig
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/19 12:59
 */
package cn.how2j.diytomcat.catalina;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class StandardFilterConfig implements FilterConfig {

    private ServletContext servletContext;
    private Map<String, String> initParameters;
    private String filterName;

    public StandardFilterConfig(ServletContext servletContext, String filterName, Map<String, String> initParameters) {
        this.servletContext = servletContext;
        this.filterName = filterName;
        this.initParameters = initParameters;
        if (null == this.initParameters)
            this.initParameters = new HashMap<>();
    }


    @Override
    public String getFilterName() {
        return filterName;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public String getInitParameter(String name) {
        return initParameters.get(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(initParameters.keySet());
    }
}
