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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();
    
    private int mIndex;
    //表示TaskList在其父节点下的索引位置。
    private ArrayList<Task> mChildren;
    //表示TaskList的子任务列表
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }//初始化

    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type 操作类型为 "create"
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id 由参数 actionId 指定的操作 ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index Google 任务清单的索引值
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta 实体数据的 JSON 对象
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());//Google 任务清单的名称
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");//创建该清单的用户的 ID
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);//任务清单
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }//创建Google任务清单的JSON对象

    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type 动作类型，此处为“update”表示更新操作
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id 动作ID，用于标识此次操作
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
             
            // id 任务列表ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta 实体增量，即更新的具体内容
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());//name：任务列表名称
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());//deleted：是否已删除
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }//生成一个Google任务列表的更新操作的JSON对象

    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }
                //尝试解析其id、last_modified和name属性,相应地设置任务列表的gid、lastModified和name属性
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }//发生异常，则记录错误日志并抛出ActionFailureException异常
        }
    }//解析从服务器返回的JSON对象并设置相应的任务列表属性

    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }//判断传入的 JSON 是否为空或者是否包含头部信息，如果不包含则打印警告日志并返回

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            }//是文件夹则将文件夹的名称设置为当前笔记本的名称，名称前加上前缀
            else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            }//是系统类型的笔记，则根据笔记的 ID 判断是默认文件夹还是通话记录文件夹，并设置相应的名称
            else {
                Log.e(TAG, "error type");
            }//不是文件夹也不是系统类型，则打印错误日志
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }//解析其中的笔记信息，并设置到当前对象中

    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();//获取TaskList对象的名称
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))//判断是否以指定前缀GTaskStringUtils.MIUI_FOLDER_PREFFIX开头
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());//是，则将前缀截去，得到真正的名称
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)//名称为默认名称
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);//置folder对象的类型为Notes.TYPE_SYSTEM
            else//为来电记事本名称
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);//设置folder对象的类型为Notes.TYPE_FOLDER

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }//有异常发生，会打印错误日志并返回null
    }//将TaskList对象的名称转换成本地JSON格式

    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {//
                // there is no local update 表示没有本地更新
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side 没有任何更新
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local 表示需要将远程的更新应用到本地
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id 验证 GTASK_ID_COLUMN 列的值是否等于 getGid() 方法返回的值
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;//异常
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only 表示只有本地有修改，需要将其同步到远程
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // for folder conflicts, just apply local modification 需要优先应用本地的修改
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }//检查了 Cursor 对象中的某些属性值，以确定需要执行哪些同步操作

    public int getChildTaskCount() {
        return mChildren.size();
    }//返回私有变量 mChildren 列表的大小。

    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {//判断传入的任务对象 task 是否为空，以及当前对象的子任务列表 mChildren 是否已包含该任务
            ret = mChildren.add(task);//将该任务添加到 mChildren 列表中
            if (ret) {
                // need to set prior sibling and parent
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }//若添加成功，则需要对该任务进行设置其前一个同级任务和父级任务。
        }
        return ret;
    }//向当前对象添加一个子任务的操作是否成功

    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }//判断传入的 index 是否在合法范围内

        int pos = mChildren.indexOf(task);//查找任务列表中是否已包含传入的任务对象 task
        if (task != null && pos == -1) {//未包含该任务
            mChildren.add(index, task);//将其插入到 index 指定的位置

            // update the task list 更新插入任务的前一个同级任务和后一个同级任务的引用
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }//返回 true 表示添加任务成功，否则返回 false。

        return true;
    }//表示向当前对象的子任务列表指定位置添加一个任务的操作是否成功

    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);//查找传入的任务对象 task 在子任务列表中的索引位置
        if (index != -1) {//该任务在子任务列表中存在
            ret = mChildren.remove(task);//移除该任务

            if (ret) {
                // reset prior sibling and parent 更新被移除任务的前一个同级任务和父级任务的引用
                task.setPriorSibling(null);
                task.setParent(null);

                // update the task list
                if (index != mChildren.size()) {//该任务不是子任务列表的最后一个任务
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }//更新其后一个同级任务的前一个同级任务的引用
            }
        }
        return ret;
    }//表示从当前对象的子任务列表中移除一个指定任务的操作是否成功

    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }//判断传入的 index 是否在合法范围内

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }//查找子任务列表中是否包含传入的任务对象 task

        if (pos == index)//任务在子任务列表中的位置已经是 index
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }//表示将当前对象子任务列表中的一个任务移动到指定位置的操作是否成功

    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {//遍历当前对象子任务列表中的所有任务
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {//任务的 gid 属性与传入的 gid 相同
                return t;
            }
        }
        return null;
    }//根据传入的 gid 在当前对象的子任务列表中查找并返回相应的任务对象

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }//表示当前对象子任务列表中传入任务对象 task 的索引位置

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {//判断传入的 index 是否在合法范围内
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }//表示当前对象子任务列表中指定索引位置 index 的任务对象

    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {//遍历
            if (task.getGid().equals(gid))//若任务的 gid 属性与传入的 gid 相同，则返回该任务对象
                return task;
        }
        return null;
    }//表示根据传入的 gid 在当前对象的子任务列表中查找并返回相应的任务对象

    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }//表示当前对象的子任务列表

    public void setIndex(int index) {
        this.mIndex = index;
    }//设置当前任务的索引位置

    public int getIndex() {
        return this.mIndex;
    }//返回当前任务在其父任务中的索引位置
}
