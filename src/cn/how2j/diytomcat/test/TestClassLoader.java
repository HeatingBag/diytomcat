/**
 * @Title: TestClassLoader
 * @Auther: zhang
 * @Version: 1.0
 * @create: 2022/7/13 9:18
 */
package cn.how2j.diytomcat.test;

public class TestClassLoader {

    public static void main(String[] args) {
        Object o = new Object();

        System.out.println(o);

        Class<?> clazz = o.getClass();

        System.out.println(clazz);

        System.out.println();

        System.out.println(Object.class.getClassLoader());
        System.out.println(TestClassLoader.class.getClassLoader());

    }
}
