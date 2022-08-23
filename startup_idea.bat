REM 先删除(del) bootstrap.jar /q 表示不会弹出是否要删除的提示,默认删除
del /q bootstrap.jar
REM 把 out/production/diytomcat 目录下的 Bootstrap 类和 CommonClassLoader 类打入到 bootstrap.jar 包
REM 因为启动只需要这两个类就足够了，也就是只有这两个类的类加载器是 AppClassLoader, 其余的类加载器都应该是 CommonClassLoader
jar cvf0 bootstrap.jar -C out/production/diytomcat cn/how2j/diytomcat/Bootstrap.class -C out/production/diytomcat cn/how2j/diytomcat/classloader/CommonClassLoader.class
REM 创建之前，先删除 lib 目录下的 diytomcat, 我们项目的所有类，都放在这个 diytomcat.jar 里，以供运行
del /q lib/diytomcat.jar
cd out
cd production
cd diytomcat
REM 把 diytomcat 目录下所有的类和资源都打包进 lib/diytomcat这里
jar cvf0 ../../../lib/diytomcat.jar *
cd ..
cd ..
cd ..
REM 启动 Bootstrap 类 -cp bootstrap.jar 是指以 bootstrap.jar 里的类为依赖，启动 Bootstrap类
java -cp bootstrap.jar cn.how2j.diytomcat.Bootstrap 
pause
REM pause:暂停一下的目的是如果启动失败了，多半是一位内端口号被占用了，倘若不暂停的话，就会屏幕一闪而过，暂停的目的是为了清楚地看到为什么启动会失败
REM 解释下参数 cvf0
REM c 表示创建文档
REM v 表示显示明细
REM f 表示指定jar 的文件名
REM 0 表示不压缩