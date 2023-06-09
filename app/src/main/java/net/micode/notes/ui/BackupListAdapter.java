package net.micode.notes.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;
import net.micode.notes.R;
import net.micode.notes.data.Notes;

import java.util.ArrayList;

public class BackupListAdapter extends CursorAdapter {

    private static final String TAG = "chenqy";
    private ArrayList<Long> mSelectedList;
    private OnAllCheckedListener mCallback;

    public BackupListAdapter(Context context, Cursor c, OnAllCheckedListener callback) {
        super(context, c, 0);
        mSelectedList = new ArrayList<>();
        this.mCallback = callback;
    }

    @Override
    public int getCount() {
        return super.getCount();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.note_menu_list_item, parent, false);
    }

    @SuppressLint("Range")
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView tv = view.findViewById(R.id.note_menu_list_item_tv);
        tv.setText(cursor.getString(cursor.getColumnIndex(Notes.NoteColumns.SNIPPET)));
        NoteCheckBox cb = view.findViewById(R.id.note_menu_list_item_cb);
        cb.setNoteId(cursor.getLong(cursor.getColumnIndex(Notes.NoteColumns.ID)));
        view.setOnClickListener(v -> {
            Log.e(TAG, "getCount" + getCount());
            if (cb.isChecked()) {
                mSelectedList.remove(cb.getNoteId());
                cb.setChecked(false);
            } else {
                mSelectedList.add(cb.getNoteId());
                cb.setChecked(true);
            }
            if (mSelectedList.size() == getCount()) {
                mCallback.onAllChecked(true);
            }else{
                mCallback.onAllChecked(false);
            }

        });
        cb.setChecked(mSelectedList.contains(cb.getNoteId()));
    }

    public ArrayList<Long> getmSelectedIndex() {
        return mSelectedList;
    }

    @SuppressLint("Range")
    public void changeAll(boolean isChecked) {
        Log.e(TAG, "changeAll");
        mSelectedList.clear();
        if (isChecked) {
            Cursor cursor = getCursor();
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    mSelectedList.add(cursor.getLong(cursor.getColumnIndex(Notes.NoteColumns.ID)));
                    cursor.moveToNext();
                }
            }
        }
        Log.e(TAG, "selectedList" + mSelectedList.toString());
        notifyDataSetChanged();
    }

    interface OnAllCheckedListener {
        void onAllChecked(boolean isChecked);
    }

}

