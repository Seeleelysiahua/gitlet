package gitlet;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** 代表 gitlet 的 commit 对象。
 *  <p>保存一次提交的项目快照，以及提交元数据（提交信息、提交时间、父提交、文件名到 blob ID 的映射）。
 *  @author seelHUAelysia
 */
public class Commit implements Serializable {

    /** 序列化版本号，固定 1L，防止字段演进导致旧数据反序列化失败。 */
    private static final long serialVersionUID = 1L;

    /** 提交信息文本。 */
    private final String message;

    /** 提交时刻。 */
    private final Date timestamp;

    /** first parent 的 ID。 */
    private final String firstParent;

    /** second parent 的 ID。 */
    private final String secondParent;

    /** 文件名到 blob ID 的映射。 */
    private final Map<String, String> filenameToBlob;

    /** 根据五个参数构造一个 commit 对象。
     *  <p>对 timestamp 与映射做防御性处理，保证构造后 commit 的不变性。
     *  @param message 提交信息文本。
     *  @param timestamp 提交时刻，内部保存其毫秒数副本。
     *  @param firstParent first parent 的 ID；initial commit 传 null。
     *  @param secondParent second parent 的 ID；非 merge commit 传 null。
     *  @param filenameToBlob 文件名到 blob ID 的映射，内部复制并封只读。
     */
    public Commit(String message, Date timestamp,
                  String firstParent, String secondParent,
                  Map<String, String> filenameToBlob) {
        this.message = message;

        // Date 可变，直接引用会让外部经 setTime 改到内部，故构造时新建独立副本，不直接赋值。
        this.timestamp = new Date(timestamp.getTime());

        this.firstParent = firstParent;
        this.secondParent = secondParent;

        // 原因同上，构造时新建独立副本，不直接赋值。
        Map<String, String> copy = new HashMap<>(filenameToBlob);

        // 包成不可修改视图，防止外部经 put/remove 修改内部映射。
        this.filenameToBlob = Collections.unmodifiableMap(copy);
    }

    /** @return 提交信息文本。 */
    public String getMessage() {
        return message;
    }

    /** @return 提交时刻。 */
    public Date getTimestamp() {
        // 复制一份副本，调用方对拿到的副本进行修改，都不会影响内部。
        Date copy = new Date(timestamp.getTime());
        return copy;
    }

    /** @return first parent 的 ID；initial commit 为 null。 */
    public String getFirstParent() {
        return firstParent;
    }

    /** @return second parent 的 ID；仅 merge commit 非 null。 */
    public String getSecondParent() {
        return secondParent;
    }

    /** @return 文件名到 blob ID 的只读映射。 */
    public Map<String, String> getFilenameToBlob() {
        return filenameToBlob;
    }
}
