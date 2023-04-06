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

// Contact类用于查询联系人信息
public class Contact {
    // 用于缓存已查询到的联系人名称
    private static HashMap<String, String> sContactCache;
    // 日志标签
    private static final String TAG = "Contact";

    // 查询联系人的选择条件
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    // 根据电话号码获取联系人名称的方法
    public static String getContact(Context context, String phoneNumber) {
        // 如果缓存为空，则创建一个新的HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 如果缓存中已存在该电话号码的联系人，则直接返回其名称
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 替换CALLER_ID_SELECTION中的"+"为phoneNumber的最小匹配形式
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        // 查询联系人信息
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 如果查询结果不为空且有结果，则获取联系人名称
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                // 将联系人名称存入缓存
                sContactCache.put(phoneNumber, name);
                // 返回联系人名称
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 获取联系人名称出错，记录日志并返回null
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 关闭游标
                cursor.close();
            }
        } else {
            // 查询结果为空，记录日志并返回null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
/*这个类主要用于根据电话号码查询联系人名称。在查询过程中，会将查询到的联系人名称缓存在一个HashMap中，以便于下次查询时直接从缓存中获取。*/