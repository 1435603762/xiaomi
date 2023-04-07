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
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class Task extends Node {
    private static final String TAG = Task.class.getSimpleName();

    private boolean mCompleted;//表示任务是否完成

    private String mNotes;//表示任务备注的字符串

    private JSONObject mMetaInfo;//表示任务元信息的JSONObject对象

    private Task mPriorSibling;//表示任务前一个兄弟任务的Task对象

    private TaskList mParent;//表示任务所属任务列表的TaskList对象

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }//初始化

    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type 表示动作类型为“创建”
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id 表示该动作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index 表示Task在所属TaskList的位置
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // entity_delta 表示创建的Task的具体信息，包括名称，创建者ID，类型，笔记等
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // parent_id 表示所属TaskList的ID
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // dest_parent_type 表示所属TaskList的类型
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // list_id 表示所属TaskList的ID
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // prior_sibling_id 表示排在该Task之前的兄弟Task的ID
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }//返回JSONObject对象，表示用于创建该Task的动作

    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type 操作类型（更新操作）
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id 操作ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id 要更新的任务的ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta 更新的内容。包含了任务的名称、备注、是否已删除等信息。
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-update jsonobject");
        }

        return js;
    }//返回JSONObject对象，包含更新Google Task的相关数据

    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null){
            try {
                // id 检查"JSON"对象是否包含id属性
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }//如果是，则将其值设置为任务对象的gid属性

                // last_modified 检查JSON对象是否包含last_modified属性
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }//如果是，将其值设置为任务对象的lastModified属性

                // name 检查JSON对象是否包含name属性
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }//如果是，则将其值设置为任务对象的name属性

                // notes 它检查JSON对象是否包含notes属性
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }//如果是，则将其值设置为任务对象的notes属性

                // deleted 检查JSON对象是否包含deleted属性
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }//如果是，则将其值设置为任务对象的deleted属性

                // completed 检查JSON对象是否包含completed属性
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }//如果是，则将其值设置为任务对象的completed属性
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");//如果解析JSON对象时出现异常，将会抛出一个ActionFailureException异常
            }
        }
    }//从远程JSON对象中获取任务的各种属性并将其设置到任务对象中

    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }//判断传入的JSONObject对象是否为空以及是否包含所需的键名,如果不包含，则打印警告日志并直接返回

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
            //从META_HEAD_NOTE键对应的JSON对象中获取类型
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }//类型不为Notes.TYPE_NOTE，则打印错误日志并返回

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }//遍历元素,若MIME_TYPE键值为DataConstants.NOTE，将其CONTENT设置为该对象的name属性值。
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }//从本地存储的JSON对象中读取内容，并设置到该对象的属性中

    public JSONObject getLocalJSONFromContent() {
        String name = getName();//从任务对象中获取名称，检查是否存在 mMetaInfo JSON 对象
        try {
            if (mMetaInfo == null) {
                // new task created from web 不存在，则说明任务对象是从 Web 创建的
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }
                //创建一个新的 JSON 对象并返回
                JSONObject js = new JSONObject();
                JSONObject note = new JSONObject();
                JSONArray dataArray = new JSONArray();
                JSONObject data = new JSONObject();
                data.put(DataColumns.CONTENT, name);
                dataArray.put(data);
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
                return js;
            } else {
                // synced task 如果存在 mMetaInfo JSON 对象，则说明任务对象是同步
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }
                //从现有的 JSON 对象中更新数据并返回。
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }//将任务对象的内容转换为本地 JSON 对象

    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {//检查metaData是否为空且其notes属性是否存在
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());//尝试将notes属性的值转换为一个JSONObject对象，并将其赋值给类成员变量mMetaInfo
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;//记录一个警告日志，并将mMetaInfo设置为null
            }
        }
    }//检查metaData

    public int getSyncAction(Cursor c) {
        try {
            JSONObject noteInfo = null;
            if (mMetaInfo != null && mMetaInfo.has(GTaskStringUtils.META_HEAD_NOTE)) {
                noteInfo = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            }

            if (noteInfo == null) {
                Log.w(TAG, "it seems that note meta has been deleted");
                return SYNC_ACTION_UPDATE_REMOTE;
            }

            if (!noteInfo.has(NoteColumns.ID)) {
                Log.w(TAG, "remote note id seems to be deleted");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            // validate the note id now
            if (c.getLong(SqlNote.ID_COLUMN) != noteInfo.getLong(NoteColumns.ID)) {
                Log.w(TAG, "note id doesn't match");
                return SYNC_ACTION_UPDATE_LOCAL;
            }

            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return SYNC_ACTION_NONE;//本地和远程都没有更新，无需同步
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;//远程有更新，需要将远程的更新同步到本地
                }
            } else {
                // validate gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;//发生异常
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;//本地有更新，需要将本地的更新同步到远程
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT;//本地和远程都有更新，需要解决冲突并同步更新
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }//获取同步操作

    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }//判断任务是否值得保存

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }//// 设置任务的完成状态

    public void setNotes(String notes) {
        this.mNotes = notes;
    }//将note赋值给该待办事项的mNotes成员变量

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }//给定一个Task对象作为参数，设置它作为当前Task对象的前一个兄弟节点

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }//设置当前Task的父任务列表

    public boolean getCompleted() {
        return this.mCompleted;
    }//获取任务的“完成”状态

    public String getNotes() {
        return this.mNotes;
    }//获取任务的备注信息

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }//返回当前任务的前一个兄弟任务

    public TaskList getParent() {
        return this.mParent;
    }//表示当前任务的父任务列表。该方法并未接收任何参数

}//同步任务，将创建、更新和同步动作包装成JSON对象，用本地和远程的JSON对结点内容进行设置获取同步信息，进行本地和远程的同步
