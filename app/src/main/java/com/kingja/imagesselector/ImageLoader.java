package com.kingja.imagesselector;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Shinelon on 2015/11/14.
 */
public class ImageLoader {
    //默认线程数
    private static final int DEFAULT_THREAD_COUNT=1;
    private ImageLoader mImageLoader;
    //线程池
    private ExecutorService mThreadPool;
    //图片缓冲对象
    private LruCache<String,Bitmap> mLruCache;
    //消息队列
    private LinkedList<Runnable> mTaskQueue;
    //轮询线程
    private Thread mPoolThread;
    //给轮询线程发消息
    private Handler mPoolThreadHandler;
    //给UI发消息
    private Handler mUiHandler;
    //加载策略方式
    private Type mType=Type.LIFO;

    private ImageLoader(int threadCount,Type type) {
        init(threadCount,type);
    }

    private void init(int threadCount,Type type) {
        //初始化轮询线程
        mPoolThread=new Thread(){
            @Override
            public void run() {
                Looper.prepare();
                mPoolThreadHandler=new Handler(){

                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute(getTask());
                        /**
                         * 从消息队列中取消息
                         * 根据策略获取消息
                         * 任务里加载图片，图片压缩(获取图片尺寸)
                         */
                    }
                };
                Looper.loop();
            }
        };
        mPoolThread.start();
    //初始化缓存
        int maxMemory= (int) Runtime.getRuntime().maxMemory();
        int cacheMemory=maxMemory/8;
        mLruCache=new LruCache<String,Bitmap>(cacheMemory){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };
        //初始化线程池
        mThreadPool= Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT);
        //初始化消息队列
        mTaskQueue=new LinkedList<Runnable>();
        mType=Type.LIFO;
    }

    private  Runnable getTask() {
        if (mType==Type.FIFO){
            return mTaskQueue.removeFirst();
        }else if(mType==Type.LIFO){
            return mTaskQueue.removeLast();
        }
        return null;
    }

    public enum Type{
        FIFO,LIFO;
    }

    /**
     * 单例模式
     * @return
     */
    public ImageLoader getInstance(){
        if (mImageLoader==null){
            synchronized (ImageLoader.class){
                if(mImageLoader==null){
                    mImageLoader=new ImageLoader(DEFAULT_THREAD_COUNT,Type.FIFO);
                }
            }
        }
        return mImageLoader;
    }
    /**
     * 设置标志
     * 开启UIhandler
     * 加载图片
     *
     * 获取缓存中的图片
     */

    public void loadImage(String path, final ImageView imageView){
        imageView.setTag(path);
        if (mUiHandler==null){
            mUiHandler=new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    //设置图片
                    ImageBean  imageBean = (ImageBean) msg.obj;
                    String path = imageBean.path;
                    ImageView imageView = imageBean.imageView;
                    Bitmap bitmap = imageBean.bitmap;
                        //检验tag防止图片错位
                    if (imageView.getTag().toString().equals(path)){
                        imageView.setImageBitmap(bitmap);
                    }

                }
            };
        }
        Bitmap bitmap = getBitmapFromLruCache(path);
        if (bitmap!=null){
            ImageBean imageBean = new ImageBean();
            imageBean.path=path;
            imageBean.imageView=imageView;
            imageBean.bitmap=bitmap;
            Message msg = Message.obtain();
            msg.obj=imageBean;
            mUiHandler.handleMessage(msg);
        }else{
            /**
             * 加入消息队列
             * 通知轮询线程
             */

            addTask(new Runnable(){
                @Override
                public void run() {
                    //加载图片
                    //图片压缩
                    ImageSize imageSize=getImageViewSize(imageView);

                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        int width=imageView.getWidth();
        if (width<=0){
            //父类提供的宽度
            width=lp.width;
        }
        if (width<=0){
            //允许的最大宽度
            width=imageView.getMaxWidth();
        }
        if (width<=0){
            width=displayMetrics.widthPixels;
        }   int height=imageView.getHeight();
        if (height<=0){
            //父类提供的宽度
            height=lp.height;
        }
        if (height<=0){
            //允许的最大宽度
            height=imageView.getMaxHeight();
        }
        if (height<=0){
            height=displayMetrics.heightPixels;
        }
        imageSize.width=width;
        imageSize.height=width;

        return imageSize;
    }

    private class ImageSize{
        int width;
        int height;
    }

    private void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0);
    }

    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);

    }
    class ImageBean{
        String path;
        Bitmap bitmap;
        ImageView imageView;

    }

}
