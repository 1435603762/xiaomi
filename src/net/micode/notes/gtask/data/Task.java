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

    private boolean mCompleted;//��ʾ�����Ƿ����

    private String mNotes;//��ʾ����ע���ַ���

    private JSONObject mMetaInfo;//��ʾ����Ԫ��Ϣ��JSONObject����

    private Task mPriorSibling;//��ʾ����ǰһ���ֵ������Task����

    private TaskList mParent;//��ʾ�������������б��TaskList����

    public Task() {
        super();
        mCompleted = false;
        mNotes = null;
        mPriorSibling = null;
        mParent = null;
        mMetaInfo = null;
    }//��ʼ��

    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type ��ʾ��������Ϊ��������
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id ��ʾ�ö���ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index ��ʾTask������TaskList��λ��
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mParent.getChildTaskIndex(this));

            // entity_delta ��ʾ������Task�ľ�����Ϣ���������ƣ�������ID�����ͣ��ʼǵ�
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_TASK);
            if (getNotes() != null) {
                entity.put(GTaskStringUtils.GTASK_JSON_NOTES, getNotes());
            }
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

            // parent_id ��ʾ����TaskList��ID
            js.put(GTaskStringUtils.GTASK_JSON_PARENT_ID, mParent.getGid());

            // dest_parent_type ��ʾ����TaskList������
            js.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);

            // list_id ��ʾ����TaskList��ID
            js.put(GTaskStringUtils.GTASK_JSON_LIST_ID, mParent.getGid());

            // prior_sibling_id ��ʾ���ڸ�Task֮ǰ���ֵ�Task��ID
            if (mPriorSibling != null) {
                js.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, mPriorSibling.getGid());
            }

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate task-create jsonobject");
        }

        return js;
    }//����JSONObject���󣬱�ʾ���ڴ�����Task�Ķ���

    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type �������ͣ����²�����
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id ����ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id Ҫ���µ������ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta ���µ����ݡ���������������ơ���ע���Ƿ���ɾ������Ϣ��
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
    }//����JSONObject���󣬰�������Google Task���������

    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null){
            try {
                // id ���"JSON"�����Ƿ����id����
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }//����ǣ�����ֵ����Ϊ��������gid����

                // last_modified ���JSON�����Ƿ����last_modified����
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }//����ǣ�����ֵ����Ϊ��������lastModified����

                // name ���JSON�����Ƿ����name����
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }//����ǣ�����ֵ����Ϊ��������name����

                // notes �����JSON�����Ƿ����notes����
                if (js.has(GTaskStringUtils.GTASK_JSON_NOTES)) {
                    setNotes(js.getString(GTaskStringUtils.GTASK_JSON_NOTES));
                }//����ǣ�����ֵ����Ϊ��������notes����

                // deleted ���JSON�����Ƿ����deleted����
                if (js.has(GTaskStringUtils.GTASK_JSON_DELETED)) {
                    setDeleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_DELETED));
                }//����ǣ�����ֵ����Ϊ��������deleted����

                // completed ���JSON�����Ƿ����completed����
                if (js.has(GTaskStringUtils.GTASK_JSON_COMPLETED)) {
                    setCompleted(js.getBoolean(GTaskStringUtils.GTASK_JSON_COMPLETED));
                }//����ǣ�����ֵ����Ϊ��������completed����
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get task content from jsonobject");//�������JSON����ʱ�����쳣�������׳�һ��ActionFailureException�쳣
            }
        }
    }//��Զ��JSON�����л�ȡ����ĸ������Բ��������õ����������

    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)
                || !js.has(GTaskStringUtils.META_HEAD_DATA)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }//�жϴ����JSONObject�����Ƿ�Ϊ���Լ��Ƿ��������ļ���,��������������ӡ������־��ֱ�ӷ���

        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
            //��META_HEAD_NOTE����Ӧ��JSON�����л�ȡ����
            if (note.getInt(NoteColumns.TYPE) != Notes.TYPE_NOTE) {
                Log.e(TAG, "invalid type");
                return;
            }//���Ͳ�ΪNotes.TYPE_NOTE�����ӡ������־������

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject data = dataArray.getJSONObject(i);
                if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                    setName(data.getString(DataColumns.CONTENT));
                    break;
                }
            }//����Ԫ��,��MIME_TYPE��ֵΪDataConstants.NOTE������CONTENT����Ϊ�ö����name����ֵ��
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }//�ӱ��ش洢��JSON�����ж�ȡ���ݣ������õ��ö����������

    public JSONObject getLocalJSONFromContent() {
        String name = getName();//����������л�ȡ���ƣ�����Ƿ���� mMetaInfo JSON ����
        try {
            if (mMetaInfo == null) {
                // new task created from web �����ڣ���˵����������Ǵ� Web ������
                if (name == null) {
                    Log.w(TAG, "the note seems to be an empty one");
                    return null;
                }
                //����һ���µ� JSON ���󲢷���
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
                // synced task ������� mMetaInfo JSON ������˵�����������ͬ��
                JSONObject note = mMetaInfo.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                JSONArray dataArray = mMetaInfo.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    if (TextUtils.equals(data.getString(DataColumns.MIME_TYPE), DataConstants.NOTE)) {
                        data.put(DataColumns.CONTENT, getName());
                        break;
                    }
                }
                //�����е� JSON �����и������ݲ����ء�
                note.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
                return mMetaInfo;
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }//��������������ת��Ϊ���� JSON ����

    public void setMetaInfo(MetaData metaData) {
        if (metaData != null && metaData.getNotes() != null) {//���metaData�Ƿ�Ϊ������notes�����Ƿ����
            try {
                mMetaInfo = new JSONObject(metaData.getNotes());//���Խ�notes���Ե�ֵת��Ϊһ��JSONObject���󣬲����丳ֵ�����Ա����mMetaInfo
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                mMetaInfo = null;//��¼һ��������־������mMetaInfo����Ϊnull
            }
        }
    }//���metaData

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
                    return SYNC_ACTION_NONE;//���غ�Զ�̶�û�и��£�����ͬ��
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;//Զ���и��£���Ҫ��Զ�̵ĸ���ͬ��������
                }
            } else {
                // validate gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;//�����쳣
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;//�����и��£���Ҫ�����صĸ���ͬ����Զ��
                } else {
                    return SYNC_ACTION_UPDATE_CONFLICT;//���غ�Զ�̶��и��£���Ҫ�����ͻ��ͬ������
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }//��ȡͬ������

    public boolean isWorthSaving() {
        return mMetaInfo != null || (getName() != null && getName().trim().length() > 0)
                || (getNotes() != null && getNotes().trim().length() > 0);
    }//�ж������Ƿ�ֵ�ñ���

    public void setCompleted(boolean completed) {
        this.mCompleted = completed;
    }//// ������������״̬

    public void setNotes(String notes) {
        this.mNotes = notes;
    }//��note��ֵ���ô��������mNotes��Ա����

    public void setPriorSibling(Task priorSibling) {
        this.mPriorSibling = priorSibling;
    }//����һ��Task������Ϊ��������������Ϊ��ǰTask�����ǰһ���ֵܽڵ�

    public void setParent(TaskList parent) {
        this.mParent = parent;
    }//���õ�ǰTask�ĸ������б�

    public boolean getCompleted() {
        return this.mCompleted;
    }//��ȡ����ġ���ɡ�״̬

    public String getNotes() {
        return this.mNotes;
    }//��ȡ����ı�ע��Ϣ

    public Task getPriorSibling() {
        return this.mPriorSibling;
    }//���ص�ǰ�����ǰһ���ֵ�����

    public TaskList getParent() {
        return this.mParent;
    }//��ʾ��ǰ����ĸ������б��÷�����δ�����κβ���

}//ͬ�����񣬽����������º�ͬ��������װ��JSON�����ñ��غ�Զ�̵�JSON�Խ�����ݽ������û�ȡͬ����Ϣ�����б��غ�Զ�̵�ͬ��
