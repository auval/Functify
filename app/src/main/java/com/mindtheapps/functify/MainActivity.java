package com.mindtheapps.functify;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.mindtheapps.functify.Functify.FuncFlow;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    Functify.FExceptionHandler eh = new Functify.FExceptionHandler() {
        @Override
        public void onFException(Throwable e) {
            if (BuildConfig.DEBUG) {
                // let the app crash
                throw new RuntimeException(e);
            } else {
                // fail nicely
                // send to your bug tracking utility
                Log.e(TAG, "Something went wrong during execution", e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView text = (TextView) findViewById(R.id.text);

        text.setText("Starting flow");

        example1(text);

    }

    /**
     * automatic flow (no user interaction)
     * @param text
     */
    private void example1(final TextView text) {
        FuncFlow fb = Functify.newFlow();
        fb.setExceptionHandler(eh);
        fb.runAsync(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                try {
                    Thread.sleep(1000); // simulate lots of work
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Supposed to run on a worker thread:");
                Functify.printDebug();
                b.putString("hello", "there");

            }
        }).runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on the main thread.");
                Functify.printDebug();
                Log.d(TAG, "And the bundle should say 'there'. got " + b.get("hello"));
                b.putString("state", "ok");
                text.setText("(1) Back to main: got " + b.get("hello"));
            }
        }).runAsync(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on a worker thread again:");
                Functify.printDebug();
                try {
                    Thread.sleep(2000); // simulate lots of work
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "And the bundle should say 'ok'. got " + b.get("state"));
                b.putString("state", "complete");

            }
        }).runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on the main thread.");
                Functify.printDebug();
                Log.d(TAG, "And the bundle should say 'complete'. got " + b.get("state"));
                text.setText("(2) Back to main: got state " + b.get("state"));
            }
        }).execute();

    }

    /**
     * trigger with user interaction
     * @param text
     */
    private void example2(final Button btn) {
        Functify.FuncFlow fb = Functify.newFlow();
        fb.setExceptionHandler(eh);
        fb.runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on the main thread.");
                Functify.printDebug();
                b.putString("state", "ok");
                btn.setText("(1) Back to main: got " + b.get("hello"));
            }
        }).runAsync(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on a worker thread again:");
                Functify.printDebug();
                try {
                    Thread.sleep(2000); // lots and lots of work
                    throw new RuntimeException("boom");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "And the bundle should say 'ok'. got " + b.get("state"));
                b.putString("state", "complete");

            }
        }).runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Supposed to run on the main thread.");
                Functify.printDebug();
                Log.d(TAG, "And the bundle should say 'complete'. got " + b.get("state"));
                btn.setText("(2) Back to main: got state " + b.get("state"));
            }
        }).runOnClick(btn);

    }

    @Override
    protected void onDestroy() {
        Functify.onDestroy();
    }
}
