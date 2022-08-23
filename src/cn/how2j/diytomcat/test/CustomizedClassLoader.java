/**
 * @Title: CustomizedClassLoader
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/13 9:26
 */
package cn.how2j.diytomcat.test;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.lang.reflect.Method;

/*1. 创建一个类 CustomizedClassLoader ，继承自 ClassLoader*/
public class CustomizedClassLoader extends ClassLoader {

    /*2. 定义属性 classesFolder 其值是当前项目下的 classes_4_test 目录*/
    private File classesFolder = new File(System.getProperty("user.dir"), "classes_4_test");

    /*4. 重写 findClass 方法，把这个字节数组通过调用 defineClass 方法，就转换成 HOW2J 这个类对应的 Class 对象了。*/
    protected Class<?> findClass(String QualifiedName) throws ClassNotFoundException {
        byte[] data = loadClassData(QualifiedName);
        return defineClass(QualifiedName, data, 0, data.length);
    }

    /* 3. 重写 loadClassData 方法，它会把传进去的全限定类名，匹配到文件：
     * E:\project\diytomcat\classes_4_test\cn\how2j\diytomcat\test\HOW2J.class
     * 并返回改文件的字节数组。
     * */
    private byte[] loadClassData(String fullQualifiedName) throws ClassNotFoundException {
        String fileName = StrUtil.replace(fullQualifiedName, ".", "/") + ".class";
        File classFile = new File(classesFolder, fileName);
        if (!classFile.exists())
            throw new ClassNotFoundException(fullQualifiedName);
        return FileUtil.readBytes(classFile);
    }

    public static void main(String[] args) throws Exception {
        CustomizedClassLoader loader = new CustomizedClassLoader();

        /*cn.how2j.diytomcat.test.HOW2J就是全限定类名*/
        Class<?> how2jClass = loader.loadClass("cn.how2j.diytomcat.test.HOW2J");

        /*5. 拿到这个类对象之后，通过反射机制，调用其 hello 方法，就能看到如字符串：hello, this is how2j saying "Hello!"*/
        Object o = how2jClass.newInstance();

        Method m = how2jClass.getMethod("hello");

        m.invoke(o);

        /* 6. 打印这个类对象的 ClassLoader，就会发现既不是 null ( 启动类加载器)， 也不是 sun.misc.Launcher$AppClassLoader@e2f2a (应用类加载器)，
         * 而是当前的这个 自定义类加载器： CustomizedClassLoader
         * */
        System.out.println(how2jClass.getClassLoader());

    }

}
