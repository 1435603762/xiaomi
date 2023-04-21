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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    private static GTaskClient mInstance = null;

    private DefaultHttpClient mHttpClient;

    private String mGetUrl;

    private String mPostUrl;

    private long mClientVersion;

    private boolean mLoggedin;

    private long mLastLoginTime;

    private int mActionId;

    private Account mAccount;

    private JSONArray mUpdateArray;

// 定义一个GTaskClient类，用于管理Google任务API
public class GTaskClient {

    // 初始化GTaskClient类的私有构造函数
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    // 创建一个静态的GTaskClient实例，确保线程安全
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    public boolean login(Activity activity) {
        // 假设cookie在5分钟后会过期，所以需要重新登录
        final long interval = 1000 * 60 * 5;
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }
    
        // 如果帐户发生变化，则需要重新登录
        if (mLoggedin && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity.getSyncAccountName(activity))) {
            mLoggedin = false;
        }
    
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }
    
        mLastLoginTime = System.currentTimeMillis();
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }
    
        // 如果需要，使用自定义域名进行登录
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase().endsWith("googlemail.com"))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";
    
            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }
    
        // 尝试使用Google官方URL进行登录
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }
    
        mLoggedin = true;
        return true;
    }
    
    // 登录Google账户，获取授权令牌
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType("com.google");
    
        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }
    
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }
    
        // 获取授权令牌
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (invalidateToken) {
                // 使令牌无效，并重新获取令牌
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }
    
        return authToken;
    }
    
    // 尝试使用授权令牌进行Gtask登录
/**
 * 尝试使用指定的身份验证令牌登录到 Gtask 服务。
 * 
 * @param activity 调用此方法的 Activity
 * @param authToken Gtask 服务的身份验证令牌
 * @return 如果登录成功，则返回 true；否则返回 false。
 */    
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        // 如果无法成功登录 Gtask 服务，则进行以下处理。
        if (!loginGtask(authToken)) {
            // 可能是身份验证令牌过期了，现在我们需要将令牌作废并再次尝试登录。
            authToken = loginGoogleAccount(activity, true);
            // 如果登录谷歌帐号失败，则返回 false。
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }
    
            // 如果仍然无法成功登录 Gtask 服务，则返回 false。
            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        // 如果登录成功，则返回 true。
        return true;
    }

 /**
 * 使用给定的身份验证令牌登录到 Gtask 服务。
 *
 * @param authToken Gtask 服务的身份验证令牌
 * @return 如果登录成功，则返回 true；否则返回 false。
 */
private boolean loginGtask(String authToken) {
    // 配置 HTTP 连接参数。
    int timeoutConnection = 10000;
    int timeoutSocket = 15000;
    HttpParams httpParameters = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
    HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

    // 创建 HTTP 客户端。
    mHttpClient = new DefaultHttpClient(httpParameters);
    BasicCookieStore localBasicCookieStore = new BasicCookieStore();
    mHttpClient.setCookieStore(localBasicCookieStore);
    HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

    // 使用身份验证令牌登录 Gtask 服务。
    try {
        String loginUrl = mGetUrl + "?auth=" + authToken;
        HttpGet httpGet = new HttpGet(loginUrl);
        HttpResponse response = mHttpClient.execute(httpGet);

        // 获取 Cookie。
        List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
        boolean hasAuthCookie = false;
        for (Cookie cookie : cookies) {
            if (cookie.getName().contains("GTL")) {
                hasAuthCookie = true;
            }
        }
        if (!hasAuthCookie) {
            Log.w(TAG, "it seems that there is no auth cookie");
        }

        // 获取客户端版本。
        String resString = getResponseContent(response.getEntity());
        String jsBegin = "_setup(";
        String jsEnd = ")}</script>";
        int begin = resString.indexOf(jsBegin);
        int end = resString.lastIndexOf(jsEnd);
        String jsString = null;
        if (begin != -1 && end != -1 && begin < end) {
            jsString = resString.substring(begin + jsBegin.length(), end);
        }
        JSONObject js = new JSONObject(jsString);
        mClientVersion = js.getLong("v");
    } catch (JSONException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        return false;
    } catch (Exception e) {
        Log.e(TAG, "httpget gtask_url failed");
        return false;
    }

    return true;
}

/**
 * 获取下一个操作 ID。
 *
 * @return 下一个操作 ID。
 */
private int getActionId() {
    return mActionId++;
}

/**
 * 创建 HTTP POST 请求。
 *
 * @return 新的 HTTP POST 请求。
 */
private HttpPost createHttpPost() {
    HttpPost httpPost = new HttpPost(mPostUrl);
    httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
    httpPost.setHeader("AT", "1");
    return httpPost;
}


/**
 * 从HttpEntity中获取响应内容
 * @param entity HttpEntity对象
 * @return 响应内容
 * @throws IOException
 */
private String getResponseContent(HttpEntity entity) throws IOException {
    String contentEncoding = null;
    // 获取响应编码方式
    if (entity.getContentEncoding() != null) {
        contentEncoding = entity.getContentEncoding().getValue();
        Log.d(TAG, "encoding: " + contentEncoding);
    }

    InputStream input = entity.getContent();
    // 根据编码方式设置InputStream
    if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
        input = new GZIPInputStream(entity.getContent());
    } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
        Inflater inflater = new Inflater(true);
        input = new InflaterInputStream(entity.getContent(), inflater);
    }

    try {
        // 将InputStream转为字符流，并读取响应内容
        InputStreamReader isr = new InputStreamReader(input);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder sb = new StringBuilder();

        while (true) {
            String buff = br.readLine();
            if (buff == null) {
                return sb.toString();
            }
            sb = sb.append(buff);
        }
    } finally {
        // 关闭InputStream
        input.close();
    }
}

/**
 * 发送HttpPost请求，并返回JSONObject格式的响应内容
 * @param js JSONObject格式的请求内容
 * @return JSONObject格式的响应内容
 * @throws NetworkFailureException
 */
private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
    if (!mLoggedin) {
        Log.e(TAG, "please login first");
        throw new ActionFailureException("not logged in");
    }

    HttpPost httpPost = createHttpPost();
    try {
        // 创建请求参数并设置到HttpPost对象中
        LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
        list.add(new BasicNameValuePair("r", js.toString()));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
        httpPost.setEntity(entity);

        // 执行HttpPost请求
        HttpResponse response = mHttpClient.execute(httpPost);
        // 获取响应内容，并将其转为JSONObject对象
        String jsString = getResponseContent(response.getEntity());
        return new JSONObject(jsString);

    } catch (ClientProtocolException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new NetworkFailureException("postRequest failed");
    } catch (IOException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new NetworkFailureException("postRequest failed");
    } catch (JSONException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new ActionFailureException("unable to convert response content to jsonobject");
    } catch (Exception e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new ActionFailureException("error occurs when posting request");
    }
}

public void createTask(Task task) throws NetworkFailureException {
    commitUpdate(); // 提交更新
    try {
        JSONObject jsPost = new JSONObject(); // 创建一个JSON对象
        JSONArray actionList = new JSONArray(); // 创建一个JSON数组

        // action_list
        // 将Task对象的创建操作添加到JSON数组中
        actionList.put(task.getCreateAction(getActionId())); 
        // 将JSON数组添加到JSON对象中
        jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

        // client_version
        // 将客户端版本添加到JSON对象中
        jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

        // post
        // 发送post请求并接收响应
        JSONObject jsResponse = postRequest(jsPost);
        // 获取响应中的结果数组并获取第一个结果对象
        JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
        // 将新任务的ID设置为响应中的新ID
        task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

    } catch (JSONException e) {
        Log.e(TAG, e.toString()); // 输出异常日志
        e.printStackTrace(); // 打印异常堆栈信息
        throw new ActionFailureException("create task: handing jsonobject failed"); // 抛出自定义异常
    }
}

public void createTaskList(TaskList tasklist) throws NetworkFailureException {
    commitUpdate(); // 提交更新
    try {
        JSONObject jsPost = new JSONObject(); // 创建一个JSON对象
        JSONArray actionList = new JSONArray(); // 创建一个JSON数组

        // action_list
        // 将TaskList对象的创建操作添加到JSON数组中
        actionList.put(tasklist.getCreateAction(getActionId())); 
        // 将JSON数组添加到JSON对象中
        jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

        // client version
        // 将客户端版本添加到JSON对象中
        jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

        // post
        // 发送post请求并接收响应
        JSONObject jsResponse = postRequest(jsPost);
        // 获取响应中的结果数组并获取第一个结果对象
        JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
        // 将新任务列表的ID设置为响应中的新ID
        tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

    } catch (JSONException e) {
        Log.e(TAG, e.toString()); // 输出异常日志
        e.printStackTrace(); // 打印异常堆栈信息
        throw new ActionFailureException("create tasklist: handing jsonobject failed"); // 抛出自定义异常
    }
}

public void commitUpdate() throws NetworkFailureException {
    // 如果 mUpdateArray 不为空
    if (mUpdateArray != null) {
        try {
            // 新建一个 JSON 对象
            JSONObject jsPost = new JSONObject();

            // 将 mUpdateArray 放入 JSON 对象中的 action_list 字段
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

            // 将 mClientVersion 放入 JSON 对象中的 client_version 字段
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送 POST 请求
            postRequest(jsPost);

            // 清空 mUpdateArray
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();

            // 抛出异常
            throw new ActionFailureException("commit update: handing jsonobject failed");
        }
    }
}

public void addUpdateNode(Node node) throws NetworkFailureException {
    // 如果 node 不为空
    if (node != null) {
        // 如果 mUpdateArray 不为空且长度超过 10，提交更新
        if (mUpdateArray != null && mUpdateArray.length() > 10) {
            commitUpdate();
        }

        // 如果 mUpdateArray 为空，新建一个 JSON 数组
        if (mUpdateArray == null)
            mUpdateArray = new JSONArray();

        // 将 node 对象的更新操作添加到 mUpdateArray 中
        mUpdateArray.put(node.getUpdateAction(getActionId()));
    }
}


public void moveTask(Task task, TaskList preParent, TaskList curParent)
throws NetworkFailureException {
commitUpdate(); // 提交之前所有的更新操作

try {
JSONObject jsPost = new JSONObject(); // 创建一个 JSON 对象
JSONArray actionList = new JSONArray(); // 创建一个 JSON 数组，用于存放操作列表
JSONObject action = new JSONObject(); // 创建一个操作对象

// action_list
action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE, // 设置操作类型
        GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId()); // 设置操作 ID
action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid()); // 设置要移动的任务 ID

if (preParent == curParent && task.getPriorSibling() != null) {
    // 如果任务在同一个任务列表中移动且不是第一个任务，则添加其前一个任务的 ID
    action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
}

action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid()); // 设置原任务列表的 ID
action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid()); // 设置目标任务列表的 ID

if (preParent != curParent) {
    // 如果任务在不同的任务列表中移动，则添加目标任务列表的 ID
    action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
}

actionList.put(action); // 将操作添加到操作列表中
jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList); // 将操作列表添加到 JSON 对象中

// client_version
jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion); // 添加客户端版本信息

postRequest(jsPost); // 发送请求
} catch (JSONException e) {
Log.e(TAG, e.toString());
e.printStackTrace();
throw new ActionFailureException("move task: handing jsonobject failed");
}
}

public void deleteNode(Node node) throws NetworkFailureException {
commitUpdate(); // 提交之前所有的更新操作

try {
JSONObject jsPost = new JSONObject(); // 创建一个 JSON 对象
JSONArray actionList = new JSONArray(); // 创建一个 JSON 数组，用于存放操作列表

// action_list
node.setDeleted(true); // 将节点标记为已删除
actionList.put(node.getUpdateAction(getActionId())); // 将更新操作添加到操作列表中
jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList); // 将操作列表添加到 JSON 对象中

// client_version
jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion); // 添加客户端版本信息

postRequest(jsPost); // 发送请求
mUpdateArray = null; // 重置更新操作数组
} catch (JSONException e) {
Log.e(TAG, e.toString());
e.printStackTrace();
throw new ActionFailureException("delete node: handing jsonobject failed");
}
}


public JSONArray getTaskLists() throws NetworkFailureException {
    // 如果用户没有登录，则抛出异常
    if (!mLoggedin) {
        Log.e(TAG, "please login first");
        throw new ActionFailureException("not logged in");
    }

    try {
        // 发送 HTTP GET 请求，获取任务列表
        HttpGet httpGet = new HttpGet(mGetUrl);
        HttpResponse response = null;
        response = mHttpClient.execute(httpGet);

        // 从响应中提取 JSON 字符串
        String resString = getResponseContent(response.getEntity());
        String jsBegin = "_setup(";
        String jsEnd = ")}</script>";
        int begin = resString.indexOf(jsBegin);
        int end = resString.lastIndexOf(jsEnd);
        String jsString = null;
        if (begin != -1 && end != -1 && begin < end) {
            jsString = resString.substring(begin + jsBegin.length(), end);
        }

        // 解析 JSON 字符串，返回任务列表
        JSONObject js = new JSONObject(jsString);
        return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
    } catch (ClientProtocolException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new NetworkFailureException("gettasklists: httpget failed");
    } catch (IOException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new NetworkFailureException("gettasklists: httpget failed");
    } catch (JSONException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new ActionFailureException("get task lists: handing jasonobject failed");
    }
}

public JSONArray getTaskList(String listGid) throws NetworkFailureException {
    // 提交未提交的更改
    commitUpdate();
    try {
        JSONObject jsPost = new JSONObject();
        JSONArray actionList = new JSONArray();
        JSONObject action = new JSONObject();

        // action_list
        action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
        action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
        action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
        action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
        actionList.put(action);
        jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

        // client_version
        jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

        // 发送 POST 请求，获取指定任务列表的任务
        JSONObject jsResponse = postRequest(jsPost);
        return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
    } catch (JSONException e) {
        Log.e(TAG, e.toString());
        e.printStackTrace();
        throw new ActionFailureException("get task list: handing jsonobject failed");
    }
}

public Account getSyncAccount() {
    // 获取同步账户
    return mAccount;
}

public void resetUpdateArray() {
    // 重置未提交的更改
    mUpdateArray = null;
}

