package com.kingja.imagesselector;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private List<String> imgPathList;
    private List<Folder> folderList = new ArrayList<Folder>();
    private GridView gv_imgs;
    private TextView tv_dirCount;
    private TextView tv_dirName;
    private File currentFile;
    private int currentCount;
    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
            progressDialog.dismiss();
            //绑定数据到View中
            data2View();

        }
    };

    private void data2View() {
        if (currentFile==null){
            Toast.makeText(this,"未扫描到任何图片",Toast.LENGTH_SHORT).show();;
            return;
        }
        imgPathList = Arrays.asList(currentFile.list());
        ImageAdapter imageAdapter = new ImageAdapter(this, imgPathList, currentFile.getAbsolutePath());
        gv_imgs.setAdapter(imageAdapter);
    }

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
        initEvent();


    }

    private void initView() {
        gv_imgs = (GridView) findViewById(R.id.gv_imgs);
        tv_dirCount = (TextView) findViewById(R.id.tv_dirCount);
        tv_dirName = (TextView) findViewById(R.id.tv_dirName);

    }

    /**
     * 利用ContentProviders扫描图片
     */
    private void initData() {
        //判断SD卡是否存在
        //显示进度条对话框
        //开线程扫描
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "SD卡不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        progressDialog = ProgressDialog.show(this, null, "正在加载...");
        new Thread() {
            @Override
            public void run() {
                Uri mImgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver cr = MainActivity.this.getContentResolver();
                Cursor cursor = cr.query(mImgUri, null, MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?", new String[]{"image/jpeg", "image/png"}, MediaStore.Images.Media.DATE_MODIFIED);
                Set<String> mFolderSet = new HashSet<String>();
                Log.e("cursor", cursor.getCount() + "");
                while (cursor.moveToNext()) {
                    String path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    Log.e("path", path);
                    File parentFile = new File(path).getParentFile();
                    Log.i("parentFile",parentFile.getAbsolutePath().toString());
                    if (parentFile == null) {
                        continue;
                    }
                    String folderPath = parentFile.getAbsolutePath();

                    Folder folder = null;
                    if (mFolderSet.contains(folderPath)) {
                        continue;
                    } else {
                        mFolderSet.add(folderPath);
                        folder = new Folder();
                        folder.setFolderPath(folderPath);
                        folder.setFirstPath(path);
                    }
                    if (parentFile.list() == null) {
                        continue;
                    }
                    int picCount = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png")) {
                                return true;
                            }
                            return false;
                        }
                    }).length;
                    folder.setFileCount(picCount);
                    folderList.add(folder);
                    Log.i("picCount", picCount+"");
                    if (picCount>currentCount){
                        currentCount=picCount;
                        currentFile=parentFile;
                    }

                }
                cursor.close();
                mHandler.sendEmptyMessage(0);
            }
        }.start();

    }


    private void initEvent() {

    }
}
