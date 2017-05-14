Functify
--

This one-class library is a (working) proof of concept for a way to write a functional program flow in Android 
without over complicating your design with complex and subtle design patterns.

It enables you to write an asynchronous work to be run on a worker thread and register code to run on the UI 
thread once the async work completes, and to continue a flow of work on different threads as needed.

My goal was to make the most simple flow to interleave the main thread with operations that must operate on a background thread and call back 
to the main thread when done, so it could update the UI.
But the example written here can evolve to cover many use cases, and support more features, such as thread pools,
scheduling, filtering, etc.


The basic flow supported here:

1. A button click by the user (on the Main thread)
2. Post some code to run on a worker thread (not blocking the UI)
3. When the worker's work is done, run some code on the UI thread (such as UI update)
4. Post some other code to run on a worker thread (not blocking the UI)
5. When the worker's work is done, run some code on the UI thread (such as UI update)
6. etc

To use this flow in your code, all you need is to copy the "[Functify.java](app/src/main/java/com/mindtheapps/functify/Functify.java) " file to your code, and start using it
like in the example.


```java

    private void example1(final TextView text) {
        FuncFlow fb = Functify.newFlow();
        fb.setExceptionHandler(eh);
        fb.runAsync(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Will run on a worker thread.");
            }
        }).runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "We are back on the main thread.");
            }
        }).runAsync(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Will then run on a worker thread");
                // can also pass value to the next execution step
                b.putString("state", "complete");
                
                // can add debug log to verify on which thread you are, and check the contents 
                // of the bundle:
                Functify.printDebug();

            }
        }).runOnMain(new Functify.Func() {
            @Override
            public void onExecute(Bundle b) {
                Log.d(TAG, "Will run on the main thread");
                Log.d(TAG, "And the bundle should say 'complete'. got " + b.get("state"));
            }
        }).execute();

    }
        
```

### Implementation Notes

- An empty `Bundle` object is created when the `execute()` is called, and the
  same object is passed through all the calls in the flow - serving as a generic 
  way to pass data between stags.
  - You can use an existing Bundle and pass it to the execution flow using setBundle();
- Each step of the flow is presented as one `Functify.Func` Object, and `Functify` iterates over the `Func`
  list.
- Currently there's only one worked thread employed.   It's easy enough to extend Functify to employ more threads or a thread pool, though.
- The worker thread is created statically once, and lives throughout the life of the app (this is a best practice).
- In the Activity's onDestroy() you must call Functify.onDestroy() -- to prevent memory leaks.
  It will stop the flow after the current step finishes, prevent calling the next step.  
- If you need a background tasks to live longer than your Activity - isolate the call from the context, and don't call the onDestroy().
    - ie. Don't hold any reference (even implicit) to a View or an Activity, use static class or a separate class
- The exception handler is optional. If none defined, it will fail silently. 
  The `eh` FExceptionHandler omitted  from the above example can be
- A `FuncFlow` is also a `Runnable`, with all needed initialization done in its `run()` method. So and you can start it yourself like any other Runnable.  
    
```java
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
```
