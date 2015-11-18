package com.kingja.imagesselector;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by Shinelon on 2015/11/14.
 */
public class ImageLoader {
    //默认线程数
    private static final int DEFAULT_THREAD_COUNT=1;
    private static ImageLoader mImageLoader;
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
    //轮询线程信号量
    private Semaphore mSemaphorePoolThreadHandler=new Semaphore(0);
    //线程池信号量
    private Semaphore mSemaphoreThreadPool;

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
                        try {
                            //超过threadCount则阻塞
                            mSemaphoreThreadPool.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                //释放一个信号量
                mSemaphorePoolThreadHandler.release();
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
        mThreadPool= Executors.newFixedThreadPool(threadCount);
        //初始化消息队列
        mTaskQueue=new LinkedList<Runnable>();
        mType=type;
        mSemaphoreThreadPool=new Semaphore(threadCount);
    }

    private  synchronized Runnable getTask() {
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
    public static ImageLoader getInstance(int threadCount,Type type){
        if (mImageLoader==null){
            synchronized (ImageLoader.class){
                if(mImageLoader==null){
                    mImageLoader=new ImageLoader(threadCount,type);
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

    public void loadImage(final String path, final ImageView imageView){
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
            refreshBitmap(path, imageView, bitmap);
        }else{
            addTask(new Runnable(){
                @Override
                public void run() {
                    /**
                     * 加载图片
                     */
                    //获取图片控件尺寸
                    ImageSize imageSize=getImageViewSize(imageView);
                    //图片压缩
                    Bitmap compressBitmap=compressBitmapFromPath(path,imageSize.width,imageSize.height);
                    //加入缓存
                    AddBitmapToLruCache(path,compressBitmap);
                    refreshBitmap(path, imageView, compressBitmap);
                    //释放一个信号量
                    mSemaphoreThreadPool.release();

                }
            });
        }
    }

    /**
     * 加入缓存
     * @param path
     * @param compressBitmap
     */
    private void AddBitmapToLruCache(String path, Bitmap compressBitmap) {
        if (getBitmapFromLruCache(path)==null){
            if (compressBitmap!=null){
                mLruCache.put(path,compressBitmap);
            }
        }
    }

    private void refreshBitmap(String path, ImageView imageView, Bitmap bitmap) {
        ImageBean imageBean = new ImageBean();
        imageBean.path=path;
        imageBean.imageView=imageView;
        imageBean.bitmap=bitmap;
        Message msg = Message.obtain();
        msg.obj=imageBean;
        mUiHandler.sendMessage(msg);
    }

    /**
     * 压缩图片
     * @param path
     * @param reWidth
     * @param reHeight
     * @return
     */
    private Bitmap compressBitmapFromPath( String path,int reWidth, int reHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(path,options);
        int compressRatio=getCompressRatio(options, reWidth, reHeight);
        options.inJustDecodeBounds=false;
        Bitmap compressBitmpa = BitmapFactory.decodeFile(path, options);
        return compressBitmpa;
    }

    /**
     * 根据控件大小获取压缩比例
     * @param options
     * @param reWidth
     * @param reHeight
     * @return
     */

    private int getCompressRatio(BitmapFactory.Options options, int reWidth, int reHeight) {
        int width=options.outWidth;
        int height=options.outHeight;
        int compressRatio=1;
        if (width>reWidth||height>reHeight){
            int widthRatio= (int) Math.round(width*1.0/reWidth);
            int heightRatio= (int) Math.round(height*1.0/reHeight);
             compressRatio=Math.min(widthRatio,height);
        }
        return compressRatio;
    }

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
            width=getImageViewFieldValue(imageView,"mMaxWidth");
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
            height=getImageViewFieldValue(imageView, "mMaxHeight");
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

    private synchronized void addTask(Runnable runnable) {

        try {
            if (mPoolThreadHandler==null){
                mSemaphorePoolThreadHandler.acquire();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mTaskQueue.add(runnable);
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    private Bitmap getBitmapFromLruCache(String key) {
        return mLruCache.get(key);

    }
    class ImageBean{
        String path;
        Bitmap bitmap;
        ImageView imageView;

    }

    /**
     * 通过反射获得长宽mMax值
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object,String fieldName){
        int value=0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = field.getInt(object);
            if (fieldValue>0&&fieldValue<Integer.MAX_VALUE){
                value=fieldValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;

    }

}
