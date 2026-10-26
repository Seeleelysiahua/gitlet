package gitlet;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** 代表 gitlet 的暂存区
 * <p>暂存区：记录下一次 commit 相对 current commit 的两组更新
 *  @author seelHUAelysia
 */
public class StagingArea implements Serializable {

    /** 序列化版本号，固定 1L，防止字段演进导致旧数据反序列化失败。 */
    private static final long serialVersionUID = 1L;

    /** 文件名 → blob ID 映射，记录待新增或更新的文件。 */
    final Map<String, String> addition = new TreeMap<>();

    /** 待移除文件名集合。 */
    final Set<String> removal = new TreeSet<>();

    /** 无参构造：造出来 addition / removal 皆空。 */
    public StagingArea() {
    }
}
