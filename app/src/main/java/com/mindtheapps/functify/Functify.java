package com.mindtheapps.functify;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Functify
 * --------
 * <p>
 * Enables you to write any series of operation to execute one after the other.
 * An asynchronous func will work on a worker thread
 * And UI - on the main thread.
 * "ANY" means that it will use the last thread used.
 * <p>
 * Current implementation uses one worker thread, and all work will be queued on this one thread.
 * If the work can be paralleled, for example - receive multiple files from a web server - consider
 * changing the code to use a thread pool.
 * <p>
 * <p>
 * License is granted to use and/or modify this class in any project, commercial or not.
 * Keep this attribution here and in any credit page you have in your app
 * (c) Amir Uval amir@mindtheapps.com
 */
public final class Functify {

    /**
     * @THREAD_TYPE
     */
    public static final int TH_ANY = 0;
    public static final int TH_UI = 1;
    public static final int TH_WORKER = 2;
    public static final int TH_POOL_WORKER = 3;
    //    static final CallingBackRunnable cbr = new CallingBackRunnable();
    private static final String TAG = Functify.class.getSimpleName();
    private static final Handler sAsyncHandler;
    private static final Handler sMainHandler;
    private static final HandlerThread sAsyncHandlerThread = new HandlerThread("WorkerThread");

    private static ArrayList<WeakReference<FuncFlow>> mDesroyables = new ArrayList<>();

    static {
        sAsyncHandlerThread.start();
        sAsyncHandler = new Handler(sAsyncHandlerThread.getLooper());
        sMainHandler = new Handler(Looper.getMainLooper());
    }

    public static FuncFlow newFlow() {
        return new FuncFlow();
    }

    /**
     * just a useful method
     *
     * @return
     */
    public static
    @THREAD_TYPE
    int getThreadType() {
        return Looper.myLooper().equals(Looper.getMainLooper()) ? TH_UI : TH_WORKER;
    }

    public static void printDebug() {
        Log.d(TAG, ">> [running on " + (getThreadType() == TH_UI ? " the main thread]:" : " a worker thread @" + Looper.myLooper().hashCode() +
                "]:"));
    }

    /**
     * To prevent memory leaks, call this in your onDestroy(), and all pending work will be canceled.
     */
    public static void onDestroy() {
        for (WeakReference<FuncFlow> fb : mDesroyables) {
            FuncFlow funcFlow = fb.get();
            if (funcFlow != null)
                funcFlow.clean();
        }

        mDesroyables.clear();
    }

    /**
     * quick way to run async any runnable now.
     *
     * @param r
     */
    public static void justRunAsync(Runnable r) {
        sAsyncHandler.post(r);
    }

    /**
     * Session wrapper id
     */
    @Retention(SOURCE)
    @IntDef({
            TH_ANY,//0
            TH_UI,//1
            TH_WORKER,//2
            TH_POOL_WORKER, //3
    })
    public @interface THREAD_TYPE {
    }

    private interface FCallback {
        void onComplete();

        void onFException(FException e);
    }

    public interface FExceptionHandler {
        void onFException(Throwable e);
    }

    public static class FuncFlow implements Runnable {

        // one per flow
        final CallingBackRunnable cbr = new CallingBackRunnable();
        ArrayList<Func> runnables = new ArrayList<>();
        private WeakReference<FExceptionHandler> fExceptionHandler;
        private boolean mDestroyable = true;
        private Bundle bundle = null;


        /**
         * called once a flow is complete
         */
        private void clean() {
            // just stay on the safe side
            sMainHandler.removeCallbacks(cbr);
            if (mDestroyable) {
                runnables.clear();
                sAsyncHandler.removeCallbacks(cbr);
            } // if marked not to be destroyed: keep it. We're not even supposed to be here
            if (fExceptionHandler != null) {
                fExceptionHandler.clear();
            }
        }

        /**
         * Optional call: if not called, it's like calling with 'true'.
         * <p>
         * when onDestroyed() is called (you must call it in your onDestry()), all the destroyable flows will be cleared.
         * call this with 'true' only if you need a task to outlive the Activity, and that has no reference to Context.
         * <p>
         *
         * @param b
         */
        public FuncFlow setIsDestroyable(boolean b) {
            mDestroyable = b;
            return this;
        }

        /**
         * same as executeDelayed(0); (no delay)
         */
        public void execute() {
            executeDelayed(0);
        }

        private boolean isDestroyable() {
            return mDestroyable;
        }

        /**
         * execute the flow later.
         * <p>
         * beware:
         * it will not fire while in deep sleep. (as handler depends on
         * uptimeMillis = not advancing while off).
         * deep sleep will postpone post.
         * If you must fire even in deep sleep - use the AlarmManager
         * <p>
         * also note:
         * calling Fuctify.onDestroy() will cancel this, if not executed yet. (Unless marked as "setIsDestroyable(false)")
         *
         * @param delay
         */
        public void executeDelayed(long delay) {
            if (runnables.size() == 0) {
                return;
            }
            int whereToRun = runnables.get(0).whereToRun;
            if (whereToRun == TH_ANY || whereToRun == TH_WORKER) {
                sAsyncHandler.postDelayed(this, delay);
            } else {
                sMainHandler.postDelayed(this, delay);
            }
        }

        /**
         * can be safely called as a simple method call from any thread
         * but it's best to use execute() or executeDelayed() because they take into account the thread of the first function
         */
        @Override
        public void run() {
            if (runnables.size() == 0) {
                // empty execution list, nothing to do here
                return;
            }
            int index = 0;
            if (bundle == null) {
                bundle = new Bundle();
            }
            if (isDestroyable()) {
                mDesroyables.add(new WeakReference<>(this));
            }
            operateOnLoop(bundle, index);
        }

        /**
         * a pseudo-recursive call
         * pseudo - because it doesn't actually enter the call stack
         * recursive - because it calls itself if there's more functions in queue).
         *
         * @param b
         * @param i
         */
        private void operateOnLoop(final Bundle b, final int i) {
            // starting with an empty bundle
            if (i >= runnables.size()) {
                // flow is complete
                clean();
                return;
            }
            cbr.func = runnables.get(i);
            cbr.b = b;
            cbr.cb = new MyFCallback(i + 1);

            if (cbr.func.whereToRun == TH_ANY) {
                // just run on the current thread
                cbr.run();
            } else if (cbr.func.whereToRun == TH_WORKER) {
                sAsyncHandler.post(cbr);
            } else if (cbr.func.whereToRun == TH_UI) {
                sMainHandler.post(cbr);
            } else { // pool
                // run on a thread pool
                // todo
                throw new RuntimeException("todo");
            }
        }

        public FuncFlow runAsync(Func func) {
            // run the runnable on a worker thread and return this FuncFlow when done
            func.whereToRun = TH_WORKER;
            runnables.add(func);
            return this;
        }

        public FuncFlow runAllAsync(Func... funcs) {
            // run the runnable on a worker thread and return this FuncFlow when done
            for (Func f : funcs) {
                f.whereToRun = TH_POOL_WORKER;
                runnables.add(f);
            }
            return this;
        }

        public FuncFlow runOnMain(Func func) {
            if (!mDestroyable) {
                // avoid that: this will lead to memory leaks
                throw new IllegalStateException("Trying to run an un-destroyable flow on the main thread");
            }

            func.whereToRun = TH_UI;
            runnables.add(func);
            return this;
        }

        public FuncFlow runOnAny(Func func) {
            func.whereToRun = TH_ANY;
            runnables.add(func);
            return this;
        }

        public void setExceptionHandler(FExceptionHandler eh) {
            this.fExceptionHandler = new WeakReference<>(eh);
        }

        /**
         * will start the execution flow upon a user click (using setOnClickListener on the View passed
         * as argument.
         *
         * @param btn
         */
        public void runOnClick(View btn) {
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    execute();
                }
            });
        }

        public Bundle getBundle() {
            return bundle;
        }

        public void setBundle(Bundle bundle) {
            this.bundle = bundle;
        }

        class MyFCallback implements FCallback {
            Bundle b;
            int i;

            public MyFCallback(int i) {
                this.i = i;
            }

            @Override
            public void onComplete() {

                // go to next
                operateOnLoop(b, i);
            }

            @Override
            public void onFException(FException e) {
                if (fExceptionHandler != null && fExceptionHandler.get() != null) {
                    fExceptionHandler.get().onFException(e);
                    // stop the flow (could it be useful to have other options, such as retry/backoff wait&retry/ignore and continue/...?)
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public abstract static class Func {
        private
        @THREAD_TYPE
        int whereToRun;

        public abstract void onExecute(Bundle b);

    }

    private static class CallingBackRunnable implements Runnable {
        Func func;
        Bundle b;
        FCallback cb;

        @Override
        public void run() {
            try {
                // The Bundle b is there as the data link between the logic flow
                // it can be modified inside the onExecute
                func.onExecute(b);
                cb.onComplete();
            } catch (Exception e) {
                cb.onFException(new FException(e, func, b));
            }
        }
    }

    public static class FException extends Exception {
        private final Func f;
        private final Bundle b;

        public FException(Exception e, Func f, Bundle b) {
            super(e);
            this.f = f;
            this.b = b;
        }

        @Override
        public String toString() {
            return super.toString() + " (On Func:" + f + ", With Bundle:" + b + ")";
        }
    }
}
