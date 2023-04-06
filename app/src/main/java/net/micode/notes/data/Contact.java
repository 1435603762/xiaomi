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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

// Contact�����ڲ�ѯ��ϵ����Ϣ
public class Contact {
    // ���ڻ����Ѳ�ѯ������ϵ������
    private static HashMap<String, String> sContactCache;
    // ��־��ǩ
    private static final String TAG = "Contact";

    // ��ѯ��ϵ�˵�ѡ������
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    // ���ݵ绰�����ȡ��ϵ�����Ƶķ���
    public static String getContact(Context context, String phoneNumber) {
        // �������Ϊ�գ��򴴽�һ���µ�HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // ����������Ѵ��ڸõ绰�������ϵ�ˣ���ֱ�ӷ���������
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // �滻CALLER_ID_SELECTION�е�"+"ΪphoneNumber����Сƥ����ʽ
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        // ��ѯ��ϵ����Ϣ
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // �����ѯ�����Ϊ�����н�������ȡ��ϵ������
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                // ����ϵ�����ƴ��뻺��
                sContactCache.put(phoneNumber, name);
                // ������ϵ������
                return name;
            } catch (IndexOutOfBoundsException e) {
                // ��ȡ��ϵ�����Ƴ�������¼��־������null
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // �ر��α�
                cursor.close();
            }
        } else {
            // ��ѯ���Ϊ�գ���¼��־������null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
/*�������Ҫ���ڸ��ݵ绰�����ѯ��ϵ�����ơ��ڲ�ѯ�����У��Ὣ��ѯ������ϵ�����ƻ�����һ��HashMap�У��Ա����´β�ѯʱֱ�Ӵӻ����л�ȡ��*/