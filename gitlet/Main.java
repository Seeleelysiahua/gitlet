package gitlet;

/** 程序总入口：只把命令行参数原样转交给 Repository.run。
 *  @author seeleHUAelysia
 */
public class Main {

    /** @param args 命令行参数；args[0] 为命令名，其余为该命令的操作数。 */
    public static void main(String[] args) {
        Repository.run(args);
    }
}
