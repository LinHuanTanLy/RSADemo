package com.wzgiceman.rxretrofitlibrary.retrofit_rx.subscribers;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.trello.rxlifecycle.components.support.RxFragment;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.RxRetrofitApp;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.exception.HttpTimeException;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.http.cookie.CookieResulte;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.listener.OnNextListener;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.utils.AppUtil;
import com.wzgiceman.rxretrofitlibrary.retrofit_rx.utils.CookieDbUtil;

import java.lang.ref.SoftReference;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import rx.Observable;
import rx.Subscriber;

/**
 * Created by pc on 2018/11/26.
 */

public class FragmentProgressSubscriber<T> extends Subscriber<T> {
    /*是否弹框*/
    private boolean showPorgress = true;
    /* 软引用回调接口*/
    private SoftReference<OnNextListener> mSubscriberOnNextListener;
    /*软引用反正内存泄露*/
    private SoftReference<RxFragment> mActivity;
    /*加载框可自己定义*/
    private ProgressDialog pd;
    /*请求数据*/
    // private BaseApi api;
    private String cacheUrl;
    private boolean isCache;
    private int cookitNoNetWorkTime;
    private int netWorkTime;
    /**
     * 构造
     */
    public FragmentProgressSubscriber(RxFragment rxAppCompatActivity,String cacheUrl,boolean isCache,int cookieNoNetWorkTime,int netWorkTime,OnNextListener mSubscriberOnNextListener) {
        this.netWorkTime=netWorkTime;
        this.cookitNoNetWorkTime=cookieNoNetWorkTime;
        this.cacheUrl=cacheUrl;
        this.isCache=isCache;
        this.mSubscriberOnNextListener = new SoftReference<OnNextListener>(mSubscriberOnNextListener);

        this.mActivity = new SoftReference<>(rxAppCompatActivity);
        setShowPorgress(true);
        if (true) {
            initProgressDialog(true);
        }
    }

    /**
     * 构造
     *
     */
    public FragmentProgressSubscriber(String cacheUrl,boolean isCache,RxFragment rxAppCompatActivity,OnNextListener mSubscriberOnNextListener) {
        this.cacheUrl=cacheUrl;
        this.isCache=isCache;
        this.mSubscriberOnNextListener = new SoftReference<>(mSubscriberOnNextListener);

        this.mActivity = new SoftReference<>(rxAppCompatActivity);
        setShowPorgress(false);
    }
    /**
     * 初始化加载框
     */
    @SuppressLint("NewApi")
    private void initProgressDialog(boolean cancel) {
         Context context = mActivity.get().getContext();
        if (pd == null && context != null) {
            pd = new ProgressDialog(context);
            pd.setCancelable(cancel);
            if (cancel) {
                pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if (mSubscriberOnNextListener.get() != null) {
                            mSubscriberOnNextListener.get().onCancel();
                        }
                        onCancelProgress();
                    }
                });
            }
        }
    }


    /**
     * 显示加载框
     */
    @SuppressLint("NewApi")
    private void showProgressDialog() {
        if (!isShowPorgress()) return;
        Context context = mActivity.get().getContext();
        if (pd == null || context == null) return;
        if (!pd.isShowing()) {
            pd.show();
        }
    }


    /**
     * 隐藏
     */
    private void dismissProgressDialog() {
        if (!isShowPorgress()) return;
        if (pd != null && pd.isShowing()) {
            pd.dismiss();
        }
    }


    /**
     * 订阅开始时调用
     * 显示ProgressDialog
     */
    @Override
    public void onStart() {
        showProgressDialog();
        /*缓存并且有网*/
        if (isCache && AppUtil.isNetworkAvailable(RxRetrofitApp.getApplication())) {
             /*获取缓存数据*/
            CookieResulte cookieResulte = CookieDbUtil.getInstance().queryCookieBy(cacheUrl);
            if (cookieResulte != null) {
                long time = (System.currentTimeMillis() - cookieResulte.getTime()) / 1000;
                if (time < netWorkTime) {
                    if (mSubscriberOnNextListener.get() != null) {
                        mSubscriberOnNextListener.get().onCacheNext(cookieResulte.getResulte());
                    }
                    /*onCompleted();
                    unsubscribe();*/
                }
            }
        }
    }

    /**
     * 完成，隐藏ProgressDialog
     */
    @Override
    public void onCompleted() {
        dismissProgressDialog();
    }

    /**
     * 对错误进行统一处理
     * 隐藏ProgressDialog
     *
     * @param e
     */
    @Override
    public void onError(Throwable e) {
        dismissProgressDialog();
        /*需要緩存并且本地有缓存才返回*/
        if (isCache) {
            Observable.just(cacheUrl).subscribe(new Subscriber<String>() {
                @Override
                public void onCompleted() {

                }

                @Override
                public void onError(Throwable e) {
                    errorDo(e);
                }

                @Override
                public void onNext(String s) {
                    /*获取缓存数据*/
                    CookieResulte cookieResulte = CookieDbUtil.getInstance().queryCookieBy(s);
                    if (cookieResulte == null) {
                        throw new HttpTimeException("网络错误");
                    }
                    long time = (System.currentTimeMillis() - cookieResulte.getTime()) / 1000;
                    if (time < cookitNoNetWorkTime) {
                        if (mSubscriberOnNextListener.get() != null) {
                            mSubscriberOnNextListener.get().onCacheNext(cookieResulte.getResulte());
                        }
                    } else {
                        CookieDbUtil.getInstance().deleteCookie(cookieResulte);
                        throw new HttpTimeException("网络错误");
                    }
                }
            });
        }
    }

    /*错误统一处理*/
    @SuppressLint("NewApi")
    private void errorDo(Throwable e) {
        Context context = mActivity.get().getContext();
        if (context == null) return;
        if (e instanceof SocketTimeoutException) {
            Toast.makeText(context, "网络中断，请检查您的网络状态", Toast.LENGTH_SHORT).show();
        } else if (e instanceof ConnectException) {
            Toast.makeText(context, "网络中断，请检查您的网络状态", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "错误" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        if (mSubscriberOnNextListener.get() != null) {
            mSubscriberOnNextListener.get().onError(e);
        }
    }

    /**
     * 将onNext方法中的返回结果交给Activity或Fragment自己处理
     *
     * @param t 创建Subscriber时的泛型类型
     */
    @Override
    public void onNext(T t) {
        if (mSubscriberOnNextListener.get() != null) {
            mSubscriberOnNextListener.get().onNext(t);
        }
    }

    /**
     * 取消ProgressDialog的时候，取消对observable的订阅，同时也取消了http请求
     */
    public void onCancelProgress() {
        if (!this.isUnsubscribed()) {
            this.unsubscribe();
        }
    }


    public boolean isShowPorgress() {
        return showPorgress;
    }

    /**
     * 是否需要弹框设置
     *
     * @param showPorgress
     */
    public void setShowPorgress(boolean showPorgress) {
        this.showPorgress = showPorgress;
    }
}
