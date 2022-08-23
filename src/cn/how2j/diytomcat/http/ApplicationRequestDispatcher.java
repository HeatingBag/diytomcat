/**
 * @Title: ApplicationRequestDispatcher
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/18 19:23
 */
package cn.how2j.diytomcat.http;

import cn.how2j.diytomcat.catalina.HttpProcessor;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

public class ApplicationRequestDispatcher implements RequestDispatcher {

    private String uri;

    public ApplicationRequestDispatcher(String uri) {
        if (!uri.startsWith("/"))
            uri = "/" + uri;
        this.uri = uri;
    }

    //修改 request的uri, 然后通过 HttpProcessor 的 execute 再执行一次。相当于在服务器内部再次访问了某个页面。
    @Override
    public void forward(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {

        Request request = (Request) servletRequest;
        Response response = (Response) servletResponse;

        request.setUri(uri);

        HttpProcessor processor = new HttpProcessor();
        processor.execute(request.getSocket(), request, response);
        request.setForwarded(true);
    }

    @Override
    public void include(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {

    }
}
