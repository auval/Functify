package com.mindtheapps.functify;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.View;

import java.lang.annotation.Retention;
import java.util.ArrayList;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Functify
 * --------
 *
 * Enables you to write an asynchronous work on a worker thread and register code to run on the UI
 * thread once the async work completes.
 *
 * Current implementation uses one worker thread, and all work will be queued on this one thread.
 * If the work can be paralleled, for example - receive multiple files from a web server - consider
 * changing the code to use a thread pool.
 *
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
    static final CallingBackRunnable cbr = new CallingBackRunnable();
    private static final String TAG = Functify.class.getSimpleName();
    private static final Handler sAsyncHandler;
    private static final Handler sMainHandler;
    private static final HandlerThread sAsyncHandlerThread = new HandlerThread("WorkerThread");

    static {
        sAsyncHandlerThread.start();
        sAsyncHandler = new Handler(sAsyncHandlerThread.getLooper());
        sMainHandler = new Handler(Looper.getMainLooper());

    }

    public static FBuilder newFlow() {
        return new FBuilder();
    }

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
     *
     */
    public static void onDestroy() {
        sMainHandler.removeCallbacks(cbr);
        sAsyncHandler.removeCallbacks(cbr);

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

    public static class FBuilder {

        ArrayList<Func> runnables = new ArrayList<>();
        private FExceptionHandler fExceptionHandler;


        public void execute() {
            int index = 0;
            Bundle b = new Bundle();
            execute(b, index);
        }

        private void execute(final Bundle b, final int i) {
            // starting with an empty bundle
            if (i >= runnables.size()) {
                // flow is complete
                return;
            }
            cbr.func = runnables.get(i);
            cbr.b = b;
            cbr.cb = new FCallback() {
                @Override
                public void onComplete() {
                    // go to next
                    execute(b, i + 1);
                }

                @Override
                public void onFException(FException e) {
                    if (fExceptionHandler != null) {
                        fExceptionHandler.onFException(e);
                        // stop the flow (could it be useful to have other options, such as retry/backoff wait&retry/ignore and continue/...?)
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            };
            if (cbr.func.whereToRun == TH_WORKER) {
                sAsyncHandler.post(cbr);
            } else if (cbr.func.whereToRun == TH_UI) {
                sMainHandler.post(cbr);
            } else { //TH_ANY, pool
                // run on the current thread, or on a thread pool
                // todo
                throw new RuntimeException("todo");
            }

        }

        public FBuilder runAsync(Func func) {
            // run the runnable on a worker thread and return this FBuilder when done
            func.whereToRun = TH_WORKER;
            runnables.add(func);
            return this;
        }

        public FBuilder runAllAsync(Func... funcs) {
            // run the runnable on a worker thread and return this FBuilder when done
            for (Func f : funcs) {
                f.whereToRun = TH_POOL_WORKER;
                runnables.add(f);
            }
            return this;
        }

        public FBuilder runOnMain(Func func) {
            func.whereToRun = TH_UI;
            runnables.add(func);
            return this;
        }

        public FBuilder runOnAny(Func func) {
            func.whereToRun = TH_ANY;
            runnables.add(func);
            return this;
        }

        public void setExceptionHandler(FExceptionHandler eh) {
            this.fExceptionHandler = eh;
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
