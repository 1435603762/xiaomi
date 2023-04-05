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
    //��ʾTaskList���丸�ڵ��µ�����λ�á�
    private ArrayList<Task> mChildren;
    //��ʾTaskList���������б�
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }//��ʼ��

    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type ��������Ϊ "create"
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id �ɲ��� actionId ָ���Ĳ��� ID
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index Google �����嵥������ֵ
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta ʵ�����ݵ� JSON ����
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());//Google �����嵥������
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");//�������嵥���û��� ID
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);//�����嵥
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }//����Google�����嵥��JSON����

    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type �������ͣ��˴�Ϊ��update����ʾ���²���
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id ����ID�����ڱ�ʶ�˴β���
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);
             
            // id �����б�ID
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta ʵ�������������µľ�������
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());//name�������б�����
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());//deleted���Ƿ���ɾ��
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }//����һ��Google�����б�ĸ��²�����JSON����

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
                //���Խ�����id��last_modified��name����,��Ӧ�����������б��gid��lastModified��name����
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }//�����쳣�����¼������־���׳�ActionFailureException�쳣
        }
    }//�����ӷ��������ص�JSON����������Ӧ�������б�����

    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }//�жϴ���� JSON �Ƿ�Ϊ�ջ����Ƿ����ͷ����Ϣ��������������ӡ������־������

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            }//���ļ������ļ��е���������Ϊ��ǰ�ʼǱ������ƣ�����ǰ����ǰ׺
            else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            }//��ϵͳ���͵ıʼǣ�����ݱʼǵ� ID �ж���Ĭ���ļ��л���ͨ����¼�ļ��У���������Ӧ������
            else {
                Log.e(TAG, "error type");
            }//�����ļ���Ҳ����ϵͳ���ͣ����ӡ������־
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }//�������еıʼ���Ϣ�������õ���ǰ������

    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();//��ȡTaskList���������
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))//�ж��Ƿ���ָ��ǰ׺GTaskStringUtils.MIUI_FOLDER_PREFFIX��ͷ
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());//�ǣ���ǰ׺��ȥ���õ�����������
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)//����ΪĬ������
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);//��folder���������ΪNotes.TYPE_SYSTEM
            else//Ϊ������±�����
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);//����folder���������ΪNotes.TYPE_FOLDER

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }//���쳣���������ӡ������־������null
    }//��TaskList���������ת���ɱ���JSON��ʽ

    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {//
                // there is no local update ��ʾû�б��ظ���
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side û���κθ���
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local ��ʾ��Ҫ��Զ�̵ĸ���Ӧ�õ�����
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id ��֤ GTASK_ID_COLUMN �е�ֵ�Ƿ���� getGid() �������ص�ֵ
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;//�쳣
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only ��ʾֻ�б������޸ģ���Ҫ����ͬ����Զ��
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // for folder conflicts, just apply local modification ��Ҫ����Ӧ�ñ��ص��޸�
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }//����� Cursor �����е�ĳЩ����ֵ����ȷ����Ҫִ����Щͬ������

    public int getChildTaskCount() {
        return mChildren.size();
    }//����˽�б��� mChildren �б�Ĵ�С��

    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {//�жϴ����������� task �Ƿ�Ϊ�գ��Լ���ǰ������������б� mChildren �Ƿ��Ѱ���������
            ret = mChildren.add(task);//����������ӵ� mChildren �б���
            if (ret) {
                // need to set prior sibling and parent
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }//����ӳɹ�������Ҫ�Ը��������������ǰһ��ͬ������͸�������
        }
        return ret;
    }//��ǰ�������һ��������Ĳ����Ƿ�ɹ�

    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }//�жϴ���� index �Ƿ��ںϷ���Χ��

        int pos = mChildren.indexOf(task);//���������б����Ƿ��Ѱ��������������� task
        if (task != null && pos == -1) {//δ����������
            mChildren.add(index, task);//������뵽 index ָ����λ��

            // update the task list ���²��������ǰһ��ͬ������ͺ�һ��ͬ�����������
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }//���� true ��ʾ�������ɹ������򷵻� false��

        return true;
    }//��ʾ��ǰ������������б�ָ��λ�����һ������Ĳ����Ƿ�ɹ�

    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);//���Ҵ����������� task ���������б��е�����λ��
        if (index != -1) {//���������������б��д���
            ret = mChildren.remove(task);//�Ƴ�������

            if (ret) {
                // reset prior sibling and parent ���±��Ƴ������ǰһ��ͬ������͸������������
                task.setPriorSibling(null);
                task.setParent(null);

                // update the task list
                if (index != mChildren.size()) {//���������������б�����һ������
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }//�������һ��ͬ�������ǰһ��ͬ�����������
            }
        }
        return ret;
    }//��ʾ�ӵ�ǰ������������б����Ƴ�һ��ָ������Ĳ����Ƿ�ɹ�

    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }//�жϴ���� index �Ƿ��ںϷ���Χ��

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }//�����������б����Ƿ���������������� task

        if (pos == index)//�������������б��е�λ���Ѿ��� index
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }//��ʾ����ǰ�����������б��е�һ�������ƶ���ָ��λ�õĲ����Ƿ�ɹ�

    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {//������ǰ�����������б��е���������
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {//����� gid �����봫��� gid ��ͬ
                return t;
            }
        }
        return null;
    }//���ݴ���� gid �ڵ�ǰ������������б��в��Ҳ�������Ӧ���������

    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }//��ʾ��ǰ�����������б��д���������� task ������λ��

    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {//�жϴ���� index �Ƿ��ںϷ���Χ��
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }//��ʾ��ǰ�����������б���ָ������λ�� index ���������

    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {//����
            if (task.getGid().equals(gid))//������� gid �����봫��� gid ��ͬ���򷵻ظ��������
                return task;
        }
        return null;
    }//��ʾ���ݴ���� gid �ڵ�ǰ������������б��в��Ҳ�������Ӧ���������

    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }//��ʾ��ǰ������������б�

    public void setIndex(int index) {
        this.mIndex = index;
    }//���õ�ǰ���������λ��

    public int getIndex() {
        return this.mIndex;
    }//���ص�ǰ�������丸�����е�����λ��
}
