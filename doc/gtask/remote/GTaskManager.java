/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


// 定义一个GTaskManager类
public class GTaskManager {
    // 声明一个TAG常量用于记录日志
    private static final String TAG = GTaskManager.class.getSimpleName();

    // 声明四个int类型的状态常量
    public static final int STATE_SUCCESS = 0;
    public static final int STATE_NETWORK_ERROR = 1;
    public static final int STATE_INTERNAL_ERROR = 2;
    public static final int STATE_SYNC_IN_PROGRESS = 3;
    public static final int STATE_SYNC_CANCELLED = 4;

    // 声明一个GTaskManager实例变量
    private static GTaskManager mInstance = null;

    // 声明Activity、Context、ContentResolver等变量
    private Activity mActivity;
    private Context mContext;
    private ContentResolver mContentResolver;

    // 声明标记同步状态和取消同步状态的变量
    private boolean mSyncing;
    private boolean mCancelled;

    // 声明三个HashMap，一个TaskList对象，一个HashSet和两个映射表
    private HashMap<String, TaskList> mGTaskListHashMap;
    private HashMap<String, Node> mGTaskHashMap;
    private HashMap<String, MetaData> mMetaHashMap;
    private TaskList mMetaList;
    private HashSet<Long> mLocalDeleteIdMap;
    private HashMap<String, Long> mGidToNid;
    private HashMap<Long, String> mNidToGid;

    // 构造函数，初始化上述变量
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    // 获取GTaskManager实例的静态方法
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    // 设置Activity上下文的方法
    public synchronized void setActivityContext(Activity activity) {
        // 用于获取authToken
        mActivity = activity;
    }
}


public int sync(Context context, GTaskASyncTask asyncTask) {
    // 检查是否正在同步任务，如果是，则返回“正在同步”的状态
    if (mSyncing) {
        Log.d(TAG, "Sync is in progress");
        return STATE_SYNC_IN_PROGRESS;
    }
    // 初始化变量
    mContext = context;
    mContentResolver = mContext.getContentResolver();
    mSyncing = true;
    mCancelled = false;
    mGTaskListHashMap.clear();
    mGTaskHashMap.clear();
    mMetaHashMap.clear();
    mLocalDeleteIdMap.clear();
    mGidToNid.clear();
    mNidToGid.clear();

    try {
        // 获取GTaskClient的实例
        GTaskClient client = GTaskClient.getInstance();
        // 重置更新数组
        client.resetUpdateArray();

        // 登录Google任务
        if (!mCancelled) {
            if (!client.login(mActivity)) {
                // 登录失败，抛出异常
                throw new NetworkFailureException("login google task failed");
            }
        }

        // 从Google获取任务列表
        asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
        initGTaskList();

        // 进行内容同步工作
        asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
        syncContent();
    } catch (NetworkFailureException e) {
        // 网络连接失败，返回网络错误状态
        Log.e(TAG, e.toString());
        return STATE_NETWORK_ERROR;
    } catch (ActionFailureException e) {
        // 执行任务失败，返回内部错误状态
        Log.e(TAG, e.toString());
        return STATE_INTERNAL_ERROR;
    } catch (Exception e) {
        // 发生其他异常，打印异常信息并返回内部错误状态
        Log.e(TAG, e.toString());
        e.printStackTrace();
        return STATE_INTERNAL_ERROR;
    } finally {
        // 清空所有变量，标记同步完成
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();
        mSyncing = false;
    }

    // 返回同步是否被取消的状态
    return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
}


private void initGTaskList() throws NetworkFailureException {
    // 检查任务是否已被取消
    if (mCancelled)
        return;

    // 获取 GTaskClient 实例
    GTaskClient client = GTaskClient.getInstance();
    try {
        // 获取任务列表的 JSON 数组
        JSONArray jsTaskLists = client.getTaskLists();

        // 初始化元列表
        mMetaList = null;
        // 遍历任务列表
        for (int i = 0; i < jsTaskLists.length(); i++) {
            // 获取任务列表中的 JSON 对象
            JSONObject object = jsTaskLists.getJSONObject(i);
            // 获取任务列表的 ID 和名称
            String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
            String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

            // 如果任务列表名称为元列表，则初始化元列表
            if (name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                mMetaList = new TaskList();
                // 将元列表的内容设置为远程 JSON 数据
                mMetaList.setContentByRemoteJSON(object);

                // 加载元数据
                JSONArray jsMetas = client.getTaskList(gid);
                for (int j = 0; j < jsMetas.length(); j++) {
                    object = (JSONObject) jsMetas.getJSONObject(j);
                    MetaData metaData = new MetaData();
                    metaData.setContentByRemoteJSON(object);
                    if (metaData.isWorthSaving()) {
                        mMetaList.addChildTask(metaData);
                        if (metaData.getGid() != null) {
                            mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                        }
                    }
                }
            }
        }

        // 如果元列表不存在，则创建元列表
        if (mMetaList == null) {
            mMetaList = new TaskList();
            mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META);
            GTaskClient.getInstance().createTaskList(mMetaList);
        }

        // 初始化任务列表
        for (int i = 0; i < jsTaskLists.length(); i++) {
            JSONObject object = jsTaskLists.getJSONObject(i);
            String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
            String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

            // 如果任务列表名称以 MIUI_FOLDER_PREFFIX 开头且不是元列表，则初始化任务列表
            if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX) && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                TaskList tasklist = new TaskList();
                tasklist.setContentByRemoteJSON(object);
                mGTaskListHashMap.put(gid, tasklist);
                mGTaskHashMap.put(gid, tasklist);

                // 加载任务
                JSONArray jsTasks = client.getTaskList(gid);
                for (int j = 0; j < jsTasks.length(); j++) {
                    object = (JSONObject) jsTasks.getJSONObject(j);
                    gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                    Task task = new Task();
                    task.setContentByRemoteJSON(object);
                    if (task.isWorthSaving()) {
                        task.setMetaInfo(mMetaHashMap.get(gid));
                        tasklist.addChildTask(task);
                        mGTaskHashMap.put(gid, task);
                    }
                }
            }
        }
    } catch (JSONException e) {
        Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    pprivate void syncContent() throws NetworkFailureException {
        int syncType; // 同步类型
        Cursor c = null; // 游标对象
        String gid; // GTasks ID
        Node node; // 节点对象
    
        mLocalDeleteIdMap.clear(); // 清空本地删除ID Map
    
        if (mCancelled) { // 如果已取消，则返回
            return;
        }
    
        // 处理本地已删除的笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid); // 获取对应的节点对象
                    if (node != null) {
                        mGTaskHashMap.remove(gid); // 从 HashMap 中移除对应的节点
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c); // 执行同步
                    }
    
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN)); // 将本地删除 ID 加入 Map 中
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    
        // 先同步文件夹
        syncFolder();
    
        // 处理存在于数据库中的笔记
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid); // 获取对应的节点对象
                    if (node != null) {
                        mGTaskHashMap.remove(gid); // 从 HashMap 中移除对应的节点
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN)); // 将 GTasks ID 与本地 ID 对应
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid); // 将本地 ID 与 GTasks ID 对应
                        syncType = node.getSyncAction(c); // 获取同步类型
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 本地新增
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 远程删除
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c); // 执行同步
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }
    
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    
        // 处理剩余的项目
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node>     entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // clear local delete table
        // 检查操作是否已取消，如果未取消则继续执行
if (!mCancelled) {
    // 如果没有取消且无法批量删除本地已删除的笔记，则抛出一个异常
    if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
        throw new ActionFailureException("failed to batch-delete local deleted notes");
    }
}

// 如果操作未取消，则执行以下操作
if (!mCancelled) {
    // 更新 GTaskClient 实例，并刷新本地同步 ID
    GTaskClient.getInstance().commitUpdate();
    refreshLocalSyncId();
}


private void syncFolder() throws NetworkFailureException {
    Cursor c = null;
    String gid;
    Node node;
    int syncType;

    // 如果任务已经取消了，就直接退出
    if (mCancelled) {
        return;
    }

    // 处理根文件夹
    try {
        c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
        if (c != null) {
            c.moveToNext();
            gid = c.getString(SqlNote.GTASK_ID_COLUMN);
            node = mGTaskHashMap.get(gid);
            if (node != null) {
                mGTaskHashMap.remove(gid);
                mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                // 对于系统文件夹，仅在必要时更新远程名称
                if (!node.getName().equals(
                        GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                    doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
            } else {
                doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
            }
        } else {
            Log.w(TAG, "failed to query root folder");
        }
    } finally {
        if (c != null) {
            c.close();
            c = null;
        }
    }

    // 处理通话记录文件夹
    try {
        c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                new String[] {
                    String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                }, null);
        if (c != null) {
            if (c.moveToNext()) {
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);
                if (node != null) {
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                    mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                    // 对于系统文件夹，仅在必要时更新远程名称
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                    + GTaskStringUtils.FOLDER_CALL_NOTE))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            }
        } else {
            Log.w(TAG, "failed to query call note folder");
        }
    } finally {
        if (c != null) {
            c.close();
            c = null;
        }
    }

    // 处理本地现有文件夹
    try {
        // 查询note表中非垃圾桶下的所有笔记，并按类型降序排列
        // 查询条件为：类型为folder且父文件夹不为垃圾桶
        c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                "(type=? AND parent_id<>?)", new String[] {
                        String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                }, NoteColumns.TYPE + " DESC");
    
        // 如果查询结果非空
        if (c != null) {
            while (c.moveToNext()) {
                // 获取笔记的GTasks ID
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                // 从GTasks哈希表中获取笔记节点
                node = mGTaskHashMap.get(gid);
                // 如果节点非空
                if (node != null) {
                    // 从哈希表中移除该节点
                    mGTaskHashMap.remove(gid);
                    // 将该笔记的GTasks ID与本地ID建立映射关系
                    mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                    // 将本地ID与该笔记的GTasks ID建立映射关系
                    mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                    // 获取节点的同步操作类型
                    syncType = node.getSyncAction(c);
                } else {
                    // 如果该笔记在GTasks中不存在
                    if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                        // 本地新增
                        syncType = Node.SYNC_ACTION_ADD_REMOTE;
                    } else {
                        // 远程删除
                        syncType = Node.SYNC_ACTION_DEL_LOCAL;
                    }
                }
                // 执行内容同步
                doContentSync(syncType, node, c);
            }
        } else {
            Log.w(TAG, "failed to query existing folder");
        }
    } finally {
        // 关闭游标
        if (c != null) {
            c.close();
            c = null;
        }
    }
    
    // 遍历GTasks列表哈希表，对于每一个远程新增的文件夹，执行本地新增操作
    Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
    while (iter.hasNext()) {
        Map.Entry<String, TaskList> entry = iter.next();
        gid = entry.getKey();
        node = entry.getValue();
        if (mGTaskHashMap.containsKey(gid)) {
            mGTaskHashMap.remove(gid);
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }
    }
    
    // 提交更新操作
    if (!mCancelled)
        GTaskClient.getInstance().commitUpdate();
    

    /**
 * 这个方法用于执行同步操作。
 *
 * @param syncType 同步操作的类型。
 * @param node 要同步的节点。
 * @param c 光标，用于查询本地数据库。
 * @throws NetworkFailureException 如果同步失败，则抛出 NetworkFailureException 异常。
 */
private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
    // 如果已经取消了同步，则直接返回。
    if (mCancelled) {
        return;
    }

    MetaData meta;
    // 根据不同的同步操作类型，执行不同的同步操作。
    switch (syncType) {
        case Node.SYNC_ACTION_ADD_LOCAL:
            // 将本地节点添加到云端。
            addLocalNode(node);
            break;
        case Node.SYNC_ACTION_ADD_REMOTE:
            // 将云端节点添加到本地。
            addRemoteNode(node, c);
            break;
        case Node.SYNC_ACTION_DEL_LOCAL:
            // 删除本地节点。
            meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
            if (meta != null) {
                GTaskClient.getInstance().deleteNode(meta);
            }
            mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
            break;
        case Node.SYNC_ACTION_DEL_REMOTE:
            // 删除云端节点。
            meta = mMetaHashMap.get(node.getGid());
            if (meta != null) {
                GTaskClient.getInstance().deleteNode(meta);
            }
            GTaskClient.getInstance().deleteNode(node);
            break;
        case Node.SYNC_ACTION_UPDATE_LOCAL:
            // 更新本地节点。
            updateLocalNode(node, c);
            break;
        case Node.SYNC_ACTION_UPDATE_REMOTE:
            // 更新云端节点。
            updateRemoteNode(node, c);
            break;
        case Node.SYNC_ACTION_UPDATE_CONFLICT:
            // 合并本地和云端的修改。
            // 目前只是简单地使用本地的修改。
            updateRemoteNode(node, c);
            break;
        case Node.SYNC_ACTION_NONE:
            // 没有任何同步操作需要执行。
            break;
        case Node.SYNC_ACTION_ERROR:
        default:
            // 同步操作类型未知，抛出 ActionFailureException 异常。
            throw new ActionFailureException("unkown sync action type");
    }
}


private void addLocalNode(Node node) throws NetworkFailureException {
    if (mCancelled) {  // 检查任务是否被取消，如果被取消，则返回
        return;
    }

    SqlNote sqlNote;  // 声明SqlNote对象
    if (node instanceof TaskList) {  // 如果节点为任务列表
        if (node.getName().equals(
                GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {  // 如果节点名称是默认文件夹
            sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);  // 设置SqlNote对象的mId为root_folder
        } else if (node.getName().equals(
                GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {  // 如果节点名称是通话记录文件夹
            sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);  // 设置SqlNote对象的mId为call_record_folder
        } else {
            sqlNote = new SqlNote(mContext);
            sqlNote.setContent(node.getLocalJSONFromContent());  // 设置SqlNote对象的内容为节点的JSON内容
            sqlNote.setParentId(Notes.ID_ROOT_FOLDER);  // 设置SqlNote对象的父节点ID为root_folder
        }
    } else {  // 如果节点不是任务列表
        sqlNote = new SqlNote(mContext);
        JSONObject js = node.getLocalJSONFromContent();  // 获取节点的JSON内容
        try {
            if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {  // 如果JSON内容中包含“meta_head_note”字段
                JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);  // 获取“meta_head_note”字段对应的JSONObject
                if (note.has(NoteColumns.ID)) {  // 如果JSONObject中包含“id”字段
                    long id = note.getLong(NoteColumns.ID);  // 获取“id”字段对应的值
                    if (DataUtils.existInNoteDatabase(mContentResolver, id)) {  // 如果本地数据库中已经存在该“id”
                        // the id is not available, have to create a new one
                        note.remove(NoteColumns.ID);  // 则删除该“id”字段
                    }
                }
            }

            if (js.has(GTaskStringUtils.META_HEAD_DATA)) {  // 如果JSON内容中包含“meta_head_data”字段
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);  // 获取“meta_head_data”字段对应的JSONArray
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);  // 获取JSONArray中对应位置的JSONObject
                    if (data.has(DataColumns.ID)) {  // 如果JSONObject中包含“id”字段
                        long dataId = data.getLong(DataColumns.ID);  // 获取“id”字段对应的值
                        if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {  // 如果本地数据库中已经存在该“id”
                            // the data id is not available, have to create a new one
                            data.remove(DataColumns.ID);  // 则删除该“id”字段
                        }
                    }
                }

            }
        } catch (JSONException e) {
            Log.w(TAG, e.toString());
            e.printStackTrace();
        }
        sqlNote.setContent(js);  // 设置SqlNote对象的内容为节点的JSON内容

            // 从node对象中获取其父任务的gid，再根据gid到本地gid-nid映射表中查找其对应的nid
Long parentId = mGidToNid.get(((Task) node).getParent().getGid());

// 如果nid为null，则打印日志并抛出ActionFailureException异常
if (parentId == null) {
    Log.e(TAG, "cannot find task's parent id locally");
    throw new ActionFailureException("cannot add local node");
}

// 将获取到的nid设置为sqlNote的parentId属性值
sqlNote.setParentId(parentId.longValue());

// 将node的gid设置为sqlNote的GtaskId属性值
sqlNote.setGtaskId(node.getGid());

// 提交sqlNote的修改到数据库中，但不包括提交事务
sqlNote.commit(false);

// 将node的gid和sqlNote的id映射关系加入本地gid-nid和nid-gid映射表中
mGidToNid.put(node.getGid(), sqlNote.getId());
mNidToGid.put(sqlNote.getId(), node.getGid());

// 更新远程meta数据
updateRemoteMeta(node.getGid(), sqlNote);


private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
    if (mCancelled) {
        return;
    }

    SqlNote sqlNote;

    // 将节点的内容从JSON字符串转换为本地数据库中的SqlNote对象
    sqlNote = new SqlNote(mContext, c);
    sqlNote.setContent(node.getLocalJSONFromContent());

    // 获取节点的父节点ID
    Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
            : new Long(Notes.ID_ROOT_FOLDER);

    // 如果无法找到父节点ID，则抛出ActionFailureException异常
    if (parentId == null) {
        Log.e(TAG, "cannot find task's parent id locally");
        throw new ActionFailureException("cannot update local node");
    }

    // 将SqlNote对象的父ID设置为获取到的父节点ID
    sqlNote.setParentId(parentId.longValue());

    // 将SqlNote对象保存到本地数据库中
    sqlNote.commit(true);

    // 更新远程节点的元数据信息
    updateRemoteMeta(node.getGid(), sqlNote);
}

private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
    // 如果任务已被取消，则直接返回，不执行任务
    if (mCancelled) {
        return;
    }

    // 创建 SqlNote 对象
    SqlNote sqlNote = new SqlNote(mContext, c);
    Node n;

    // 如果是任务类型的注释
    if (sqlNote.isNoteType()) {
        // 创建 Task 对象
        Task task = new Task();
        task.setContentByLocalJSON(sqlNote.getContent());

        // 获取任务的父 ID，并创建父任务列表的 GID 映射
        String parentGid = mNidToGid.get(sqlNote.getParentId());
        if (parentGid == null) {
            // 如果无法找到任务的父任务列表，则抛出异常
            Log.e(TAG, "cannot find task's parent tasklist");
            throw new ActionFailureException("cannot add remote task");
        }
        // 将任务添加到父任务列表中
        mGTaskListHashMap.get(parentGid).addChildTask(task);

        // 使用 GTaskClient 创建任务
        GTaskClient.getInstance().createTask(task);
        n = (Node) task;

        // 更新任务的元数据
        updateRemoteMeta(task.getGid(), sqlNote);
    } else {
        // 如果是任务列表类型的注释

        TaskList tasklist = null;

        // 设置文件夹名
        String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
        if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
            folderName += GTaskStringUtils.FOLDER_DEFAULT;
        else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
            folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
        else
            folderName += sqlNote.getSnippet();

        // 遍历已存在的任务列表
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            String gid = entry.getKey();
            TaskList list = entry.getValue();

            // 如果任务列表的名称与文件夹名称匹配，则将任务列表设置为当前列表
            if (list.getName().equals(folderName)) {
                tasklist = list;
                // 如果任务列表的 GID 已经存在，则从 mGTaskHashMap 中移除该 GID
                if (mGTaskHashMap.containsKey(gid)) {
                    mGTaskHashMap.remove(gid);
                }
                break;
            }
        }

        // 如果任务列表不存在，则创建新任务列表并添加到 mGTaskListHashMap
        if (tasklist == null) {
            tasklist = new TaskList();
            tasklist.setContentByLocalJSON(sqlNote.getContent());
            GTaskClient.getInstance().createTaskList(tasklist);
            mGTaskListHashMap.put(tasklist.getGid(), tasklist);
        }
        n = (Node) tasklist;
    }

    // 更新本地 SqlNote 对象的 GID，并提交更改
    sqlNote.setGtaskId(n.getGid());
    sqlNote.commit(false);
    sqlNote.resetLocalModified();
    sqlNote.commit(true);

    // 添加 GID 和 ID 的映射
    mGidToNid.put(n.getGid(), sqlNote.getId());
    mNidToGid.put(sqlNote.getId(), n.getGid());
}


private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
    if (mCancelled) { // 如果任务被取消，停止更新远程节点
        return;
    }

    // 创建 SqlNote 对象
    SqlNote sqlNote = new SqlNote(mContext, c);

    // 更新远程节点
    node.setContentByLocalJSON(sqlNote.getContent());
    GTaskClient.getInstance().addUpdateNode(node);

    // 更新元数据
    updateRemoteMeta(node.getGid(), sqlNote);

    // 如果是 Note 类型，移动任务
    if (sqlNote.isNoteType()) {
        Task task = (Task) node; // 将节点转换为 Task 类型
        TaskList preParentList = task.getParent(); // 获取任务的旧父任务列表

        String curParentGid = mNidToGid.get(sqlNote.getParentId()); // 获取任务的新父任务列表的 gid
        if (curParentGid == null) {
            Log.e(TAG, "cannot find task's parent tasklist"); // 如果找不到新父任务列表，输出错误日志
            throw new ActionFailureException("cannot update remote task"); // 抛出异常
        }
        TaskList curParentList = mGTaskListHashMap.get(curParentGid); // 获取新父任务列表

        if (preParentList != curParentList) { // 如果任务的父任务列表发生了变化
            preParentList.removeChildTask(task); // 在旧的父任务列表中移除该任务
            curParentList.addChildTask(task); // 在新的父任务列表中添加该任务
            GTaskClient.getInstance().moveTask(task, preParentList, curParentList); // 在 Google 任务中移动任务
        }
    }

    // 清除本地修改标志
    sqlNote.resetLocalModified();
    sqlNote.commit(true); // 提交更改
}

/**
 * 在远程更新元数据
 *
 * @param gid GTask 任务 ID
 * @param sqlNote 笔记的 SQL 数据对象
 * @throws NetworkFailureException 网络连接异常
 */
private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
    // 检查是否为笔记类型
    if (sqlNote != null && sqlNote.isNoteType()) {
        // 获取指定 gid 的 MetaData 对象
        MetaData metaData = mMetaHashMap.get(gid);
        if (metaData != null) {
            // 更新 MetaData 对象中指定 gid 的元数据
            metaData.setMeta(gid, sqlNote.getContent());
            // 向 GTask 服务器提交更新请求
            GTaskClient.getInstance().addUpdateNode(metaData);
        } else {
            // 如果 MetaData 对象不存在，则新建 MetaData 对象
            metaData = new MetaData();
            metaData.setMeta(gid, sqlNote.getContent());
            // 添加 MetaData 对象到 MetaList 中
            mMetaList.addChildTask(metaData);
            // 将 MetaData 对象存储到 HashMap 中
            mMetaHashMap.put(gid, metaData);
            // 向 GTask 服务器提交创建请求
            GTaskClient.getInstance().createTask(metaData);
        }
    }
}

/**
 * 刷新本地同步 ID
 *
 * @throws NetworkFailureException 网络连接异常
 */
private void refreshLocalSyncId() throws NetworkFailureException {
    // 检查是否已取消同步
    if (mCancelled) {
        return;
    }

    // 清空哈希表
    mGTaskHashMap.clear();
    mGTaskListHashMap.clear();
    mMetaHashMap.clear();
    // 初始化 GTaskList
    initGTaskList();

    Cursor c = null;
    try {
        // 查询本地未被删除且非系统笔记的 SQL 数据对象
        c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                "(type<>? AND parent_id<>?)", new String[] {
                        String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                }, NoteColumns.TYPE + " DESC");
        if (c != null) {
            while (c.moveToNext()) {
                String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                // 获取指定 gid 的 GTask 任务节点
                Node node = mGTaskHashMap.get(gid);
                if (node != null) {
                    // 移除哈希表中的 GTask 任务节点
                    mGTaskHashMap.remove(gid);
                    ContentValues values = new ContentValues();
                    // 更新本地 SQL 数据对象的同步 ID
                    values.put(NoteColumns.SYNC_ID, node.getLastModified());
                    mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                            c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                } else {
                    Log.e(TAG, "something is missed");
                    // 抛出 ActionFailureException 异常
                    throw new ActionFailureException(
                            "some local items don't have gid after sync");
                }
            }
        } else {
            Log.w(TAG, "failed to query local note to refresh sync id");
        }
    } finally {
        if (c != null) {
            c.close();
            c = null;
        }
    }
}

  /**
 * 该方法用于获取同步账户名
 * @return 返回一个字符串，表示同步账户名
 */
public String getSyncAccount() {
    // 通过调用 GTaskClient 类的 getInstance() 方法获取 GTaskClient 实例，并调用该实例的 getSyncAccount() 方法，获取同步账户
    return GTaskClient.getInstance().getSyncAccount().name;
}

/**
 * 该方法用于取消同步
 */
public void cancelSync() {
    // 将 mCancelled 标志设置为 true，表示取消同步
    mCancelled = true;
}

}
