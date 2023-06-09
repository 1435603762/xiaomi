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

package net.micode.notes.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.service.BackupBoundService;
import net.micode.notes.tool.AnimUtils;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;
import org.json.JSONException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;


public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;

    private static final int FOLDER_LIST_QUERY_TOKEN = 1;

    private static final int MENU_FOLDER_DELETE = 0;

    private static final int MENU_FOLDER_VIEW = 1;

    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER, NOTE_MENU, NOTE_BTN_GROUP
    }

    private ListEditState mState;

    private BackgroundQueryHandler mBackgroundQueryHandler;

    private NotesListAdapter mNotesListAdapter;

    private ListView mNotesListView;

    private Button btn_new_note;

    private boolean mDispatch;

    private int mOriginY;

    private int mDispatchY;

    private TextView mTitleBar;

    private long mCurrentFolderId;

    private ContentResolver mContentResolver;

    private ModeCallback mModeCallBack;

    private static final String TAG = "chenqy";

    private NoteItemData mFocusNoteDataItem;

    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";

    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE = 103;


    //    -------------------------------------   C Q Y   ---------------------------------------------------------
    //Customized by chenqy

    //弹出菜单
    private long mExitTime = 0;
    //注册广播
    public static final String BACKUP_ACTION = "net.micode.notes.backup";
    public static final String SIGN_OUT_ACTION = "net.micode.notes.action.SIGN_OUT";

    private Button btn_note_main;
    private NoteMenuButton note_menu_btn;
    private View mask_view;

    //绑定状态
    private boolean is_bind_backup_service = false;
    //服务对象
    private BackupBoundService backupBoundService;
    //服务连接对象
    private ServiceConnection backupServiceConn;


    private void bindBackupService() {
        //创建服务连接对象
        backupServiceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                BackupBoundService.LocalBinder backupBinder = (BackupBoundService.LocalBinder) service;
                backupBoundService = backupBinder.getService();
                is_bind_backup_service = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                is_bind_backup_service = false;
            }
        };
    }


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BACKUP_ACTION)) {//TODO 备份
                try {
                    startBackup(intent);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    };

    //执行备份服务
    public void startBackup(Intent it) throws JSONException {
        if (is_bind_backup_service) {
            List<Long> noteData = getNoteData(it);
            backupBoundService.backupNotes(noteData);
        }
    }

    //TODO 获取note数据
    private List<Long> getNoteData(Intent intent) {
        Bundle args = intent.getExtras();
        long[] long_list = (long[]) args.get(NoteMenuListFragment.SELECTED_ID_LIST_KEY);
        ArrayList<Long> selected_list;
        selected_list = Arrays.stream(long_list).boxed().collect(Collectors.toCollection(ArrayList::new));
        return selected_list;
    }


//    --------------------------------------  C Q Y   ---------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        hiddenTheStatusBar();
        initResources();
        initCustom();
        //Insert an introduction when user firstly use this application
        setAppInfoFromRawRes();
    }

    private void hiddenTheStatusBar(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            getWindow().setStatusBarColor(getResources().getColor(R.color.cutePink));
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private void setAppInfoFromRawRes() {
        // 获取默认的 SharedPreferences 对象
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        // 如果用户之前没有看过介绍信息，则添加该信息
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                // 从应用程序的资源中读取 introduction 文件
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char[] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    // 如果读取文件失败，则记录日志并退出方法
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                // 如果出现异常，则记录日志并退出方法
                e.printStackTrace();
                return;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // 如果出现异常，则记录日志并继续执行
                        e.printStackTrace();
                    }
                }
            }

            // 创建一个新的 WorkingNote 对象，并将读取到的文本内容设置为其正文
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());

            // 如果保存笔记成功，则将添加介绍信息的标志设置为 true
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).apply();
            } else {
                // 如果保存笔记失败，则记录日志并退出方法
                Log.e(TAG, "Save introduction note error");
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();

        //注册服务
        Intent intent = new Intent(this, BackupBoundService.class);
        bindService(intent, backupServiceConn, Context.BIND_AUTO_CREATE);

        //注册广播
        IntentFilter filter = new IntentFilter(BACKUP_ACTION);
        registerReceiver(receiver, filter);

    }

    @Override
    protected void onStop() {
        super.onStop();

        //如果已经绑定,解绑服务
        if (is_bind_backup_service) {
            unbindService(backupServiceConn);
            is_bind_backup_service = false;
        }

        //注销广播接收器
        unregisterReceiver(receiver);
    }

    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mNotesListView = findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        btn_new_note = findViewById(R.id.btn_new_note);
        btn_new_note.setOnClickListener(this);
        btn_new_note.setOnTouchListener(new NewNoteOnTouchListener());
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
    }


    private void initCustom() {
        //TODO initCustom
        btn_note_main = findViewById(R.id.btn_note_main);
        note_menu_btn = findViewById(R.id.menu_button);
        mask_view = findViewById(R.id.empty_view);
        mask_view.setAlpha(0f);
        mask_view.setOnClickListener(this);
        btn_note_main.setOnClickListener(this);
        note_menu_btn.setNoteMenuMainFragment(new NoteMenuMainFragment());
        note_menu_btn.setMaskView(R.id.mask_view);
        note_menu_btn.setNoteMenu(R.id.note_menu);
        note_menu_btn.setToggleMneuComponentListener(new NoteMenuButton.ToggleMneuComponentListener() {
            @Override
            public void show(Context context) {
                ((Activity)context).findViewById(R.id.note_menu_container).setVisibility(View.VISIBLE);
            }
            @Override
            public void hide(Context context) {
                ((Activity)context).findViewById(R.id.note_menu_container).setVisibility(View.GONE);
            }
            @Override
            public void replace(Context context, Fragment menuFragment) {
                FragmentManager fm = ((Activity) context).getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.replace(R.id.note_menu_container, menuFragment).commit();
            }
        });
        note_menu_btn.setOnClickNoteMenuButton(() -> mState = ListEditState.NOTE_MENU);
        bindBackupService();
    }


    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;
        private ActionMode mActionMode;

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            Log.e(TAG, "onCreateActionMode");
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            MenuItem mMoveMenu = menu.findItem(R.id.move);
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            btn_new_note.setVisibility(View.GONE);

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(item -> {
                mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                updateMenu();
                return true;
            });
            return true;
        }

        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
            Log.e(TAG, "onDestroyActionMode");
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
//            btn_new_note.setVisibility(View.VISIBLE);
        }

        public void finishActionMode() {
            mActionMode.finish();
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            switch (item.getItemId()) {
                case R.id.delete:
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.alert_title_delete));
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setMessage(getString(R.string.alert_message_delete_notes,
                            mNotesListAdapter.getSelectedCount()));
                    builder.setPositiveButton(android.R.string.ok,
                            (dialog, which) -> batchDelete());
                    builder.setNegativeButton(android.R.string.cancel, null);
                    builder.show();
                    break;
                case R.id.move:
                    startQueryDestinationFolders();
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = btn_new_note.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    //Minus TitleBar's height
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    /**
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94（Unit:pixel）
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }

    }


    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[]{
                        String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
            }
        }
    }

    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, (dialog, which) -> {
            DataUtils.batchMoveToFolder(mContentResolver,
                    mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
            Toast.makeText(
                    NotesListActivity.this,
                    getString(R.string.format_move_notes_to_folder,
                            mNotesListAdapter.getSelectedCount(),
                            adapter.getFolderName(NotesListActivity.this, which)),
                    Toast.LENGTH_SHORT).show();
            mModeCallBack.finishActionMode();
        });
        builder.show();
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    // 通过注解 @SuppressLint("StaticFieldLeak") 告知编译器对静态成员变量的访问，防止出现 Android Lint 的警告
    @SuppressLint("StaticFieldLeak")
    private void batchDelete() {
        // 新建一个异步任务对象
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            // 在后台进行删除操作
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                // 获取当前选中的 Note Widget
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 如果不在同步模式下，直接通过 ContentResolver 删除所有选中的 Note
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter.getSelectedItemIds())) {
                        // 删除成功则不做操作
                    } else {
                        // 删除失败则将错误信息记录到日志中
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // 如果在同步模式下，则将所有选中的 Note 移动到垃圾箱文件夹中
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter.getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        // 移动失败则将错误信息记录到日志中
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                // 返回当前选中的 Note Widget
                return widgets;
            }

            // 在操作执行完毕后执行的方法
            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    // 遍历所有选中的 Note Widget
                    for (AppWidgetAttribute widget : widgets) {
                        // 如果当前的 Note Widget 是有效的，则更新该 Widget
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                // 结束 ActionMode 操作
                mModeCallBack.finishActionMode();
            }
        }.execute(); // 执行异步任务
    }


    private void deleteFolder(long folderId) {
        Log.e(TAG, "deleteFolder");
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    private void openNode(NoteItemData data) {
        Log.e(TAG, "openNode");
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        Log.e(TAG, "openFolder");
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            btn_new_note.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    public void onClick(View v) {
        Log.e(TAG, "notelistActiviy custem onClick" );
        int id = v.getId();
        ObjectAnimator lfAlphaAnimator;
        ObjectAnimator lfTranslationAnimator;
        ObjectAnimator rtTranslationAnimator;
        AnimatorSet lfSet;
        AnimatorSet rtSet;
        float x = btn_note_main.getX();
        float y = btn_note_main.getY();
        float lf_endX = x - convertDpToPx(70);
        float lf_endY = y - convertDpToPx(30);
        float rt_endX = x + convertDpToPx(70);
        float rt_endY = y - convertDpToPx(30);
        if (id == R.id.btn_note_main){
            mState = ListEditState.NOTE_BTN_GROUP;
            btn_note_main.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        btn_note_main.setVisibility(View.GONE);
                    })
                    .start();
            mask_view.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .withStartAction(()->{
                        mask_view.setVisibility(View.VISIBLE);
                        mask_view.setElevation(1);
                    })
                    .start();

            // 左侧按钮可见性和位移动画
            btn_new_note.setVisibility(View.VISIBLE);
            lfAlphaAnimator = ObjectAnimator.ofFloat(btn_new_note, "alpha", 0f, 1f);


//                lfTranslationAnimator = createConcaveCurve(btn_new_note,x, y, lf_endX, lf_endY);
            lfTranslationAnimator = createStraghtLine(btn_new_note,x, y, lf_endX, y);


            lfSet = AnimUtils.playAnimations(200, null, lfAlphaAnimator, lfTranslationAnimator);

            // 右侧按钮可见性和位移动画
            note_menu_btn.setVisibility(View.VISIBLE);
            ObjectAnimator rtAlphaAnimator = ObjectAnimator.ofFloat(note_menu_btn, "alpha", 0f, 1f);
//                rtTranslationAnimator = createConcaveCurve(btn_menu_btn,x, y, rt_endX, rt_endY);
            rtTranslationAnimator = createStraghtLine(note_menu_btn,x, y, rt_endX, y);

            rtSet = AnimUtils.playAnimations(200, null, rtAlphaAnimator, rtTranslationAnimator);
            lfSet.start();
            rtSet.start();
        }else if (id == R.id.btn_new_note){
            ObjectAnimator rotateAnimator = ObjectAnimator.ofFloat(btn_new_note, "rotation", 0f, -90f);
            AnimatorSet animatorSet = AnimUtils.playAnimations(200,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            Log.e(TAG, "onAnimationEnd");
//                            createNewNote_new();
                            createNewNote();
                        }
                    }
                    , rotateAnimator);
            animatorSet.start();
        }else if (id == R.id.menu_button){
            mState = ListEditState.NOTE_BTN_GROUP;
        }else if (id == R.id.empty_view){
            Log.e(TAG, "onClick empty_view ");
            mState = ListEditState.NOTE_LIST;
            btn_note_main.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .withStartAction(() -> {
                        btn_note_main.setVisibility(View.VISIBLE);
                    })
                    .start();
            mask_view.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(()->{
                        mask_view.setVisibility(View.GONE);
                    })
                    .start();


            lfAlphaAnimator = ObjectAnimator.ofFloat(btn_new_note, "alpha", 1f, 0f);
//                lfTranslationAnimator = createConvexCurve(btn_new_note,lf_endX, lf_endY, x, y);
            lfTranslationAnimator = createStraghtLine(btn_new_note,lf_endX, y, x, y);
            lfSet = AnimUtils.playAnimations(200,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            Log.e(TAG, "onAnimationEnd");
                            btn_new_note.setVisibility(View.GONE);
                        }
                    }
                    , lfAlphaAnimator, lfTranslationAnimator);

            ObjectAnimator rtAlphaAnimator = ObjectAnimator.ofFloat(note_menu_btn, "alpha", 1f, 0f);
//                rtTranslationAnimator = createConvexCurve(btn_menu_btn,rt_endX, rt_endY, x, y);
            rtTranslationAnimator = createStraghtLine(note_menu_btn,rt_endX, y, x, y);
            rtSet = AnimUtils.playAnimations(200,
                    new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            Log.e(TAG, "onAnimationEnd");
                            note_menu_btn.setVisibility(View.GONE);
                        }
                    }
                    , rtAlphaAnimator, rtTranslationAnimator);
            lfSet.start();
            rtSet.start();
        }
    }
    private ObjectAnimator createStraghtLine(Button btn, float sx, float sy, float ex, float ey) {
        // 创建贝塞尔曲线
        Path path = new Path();
        path.moveTo(sx, sy);
        path.lineTo(ex, ey);
        return ObjectAnimator.ofFloat(btn, View.X, View.Y, path);
    }

    private ObjectAnimator createConvexCurve(Button btn, float sx, float sy, float ex, float ey) {
        // 创建贝塞尔曲线
        Path path = new Path();
        path.moveTo(sx, sy);
        path.quadTo(sx, ey, ex, ey);
        return ObjectAnimator.ofFloat(btn, View.X, View.Y, path);
    }

    private ObjectAnimator createConcaveCurve(Button btn, float sx, float sy, float ex, float ey) {
        // 创建贝塞尔曲线
        Path path = new Path();
        path.moveTo(sx, sy);
        path.quadTo(ex, sy, ex, ey);
        return ObjectAnimator.ofFloat(btn, View.X, View.Y, path);
    }


    private float convertDpToPx(float dp) {
        float scale = getResources().getDisplayMetrics().density;
        return dp * scale + 0.5f;
    }


    private void showSoftInput() {
        Log.e(TAG, "showSoftInput");
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideSoftInput(View view) {
        Log.e(TAG, "hideSoftInput: ");
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> hideSoftInput(etName));

        final Dialog dialog = builder.setView(view).show();
        final Button positive = dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(v -> {
            hideSoftInput(etName);
            String name = etName.getText().toString();
            if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                        Toast.LENGTH_LONG).show();
                etName.setSelection(0, etName.length());
                return;
            }
            if (!create) {
                if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);
                    mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                            + "=?", new String[]{
                            String.valueOf(mFocusNoteDataItem.getId())
                    });
                }
            } else if (!TextUtils.isEmpty(name)) {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.SNIPPET, name);
                values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
            }
            dialog.dismiss();
        });

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        //When the name edit text is null, disable the positive button
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                positive.setEnabled(!TextUtils.isEmpty(etName.getText()));
            }

            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "onBackPressed");
        switch (mState) {
            case SUB_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                btn_new_note.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                if (System.currentTimeMillis() - mExitTime > 2000) {
                    Toast.makeText(this, R.string.press_again_exit, Toast.LENGTH_SHORT).show();
                    mExitTime = System.currentTimeMillis();
                    return;
                } else {
                    super.onBackPressed();
                }
                break;
            case NOTE_MENU:
                mState = ListEditState.NOTE_BTN_GROUP;
                note_menu_btn.hiddenMenu();
                break;
            case NOTE_BTN_GROUP:
                mState = ListEditState.NOTE_LIST;
                mask_view.performClick();
                break;
            default:
                break;
        }
    }

    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{
                appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            Log.e(TAG, "onCreateContextMenu");
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    public void onContextMenuClosed(Menu menu) {
        Log.e(TAG, "onContextMenuClosed");
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Log.e(TAG, "onContextItemSelected");
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> deleteFolder(mFocusNoteDataItem.getId()));
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.e(TAG, "准备选项菜单");
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_folder: {
                showCreateOrModifyFolderDialog(true);
                break;
            }
            case R.id.menu_export_text: {
                exportNoteToText();
                break;
            }
            case R.id.menu_sync: {
                if (isSyncMode()) {
                    if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                        GTaskSyncService.startSync(this);
                    } else {
                        GTaskSyncService.cancelSync(this);
                    }
                } else {
                    startPreferenceActivity();
                }
                break;
            }
            case R.id.menu_setting: {
                startPreferenceActivity();
                break;
            }
            case R.id.menu_new_note: {
                createNewNote();
                break;
            }
            case R.id.menu_search:
                onSearchRequested();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    @SuppressLint("StaticFieldLeak")
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {
        Log.e(TAG, "前往 PreferenceActivity");
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.e(TAG, "短按");
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection :
                "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[]{
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Log.e(TAG, "长按");
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
//                view.findViewById(R.id.is_top).setVisibility(View.VISIBLE);
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }

}
