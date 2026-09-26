package com.termux.app.session;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.widget.TextView;
import com.termux.terminal.TerminalSessionProcess;
import java.util.concurrent.CopyOnWriteArrayList;
import rikka.shizuku.Shizuku;

/** Required normal-session backend. Recovery is an explicit user action, never a command retry. */
public final class SessionManager {
    private static final int REQUEST_PERMISSION = 7341;
    private static SessionManager instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Shizuku.UserServiceArgs args;
    private volatile ISessionService service;
    private boolean connecting;
    private int attempt;
    private java.lang.ref.WeakReference<AlertDialog> prompt = new java.lang.ref.WeakReference<>(null);
    private String message = "Start Shizuku, then connect to open a terminal session.";

    public static boolean required(Context context) {
        return Build.VERSION.SDK_INT >= 29 && context.getApplicationInfo().targetSdkVersion >= 29;
    }

    public static synchronized SessionManager get(Context context) {
        if (instance == null) instance = new SessionManager(context.getApplicationContext());
        return instance;
    }

    private SessionManager(Context context) {
        this.context = context;
        args = new Shizuku.UserServiceArgs(new ComponentName(context, SessionUserService.class))
            .daemon(false).processNameSuffix("aether_sessions").debuggable(false).version(2);
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(() -> connect(false)));
        Shizuku.addBinderDeadListener(() -> main.post(() -> {
            connecting = false;
            message = "Shizuku stopped. Start it again to open new sessions.";
            notifyChanged();
        }));
        Shizuku.addRequestPermissionResultListener((code, result) -> {
            if (code == REQUEST_PERMISSION) main.post(() -> {
                if (result == PackageManager.PERMISSION_GRANTED) connect(false);
                else { message = "Shizuku access was denied. Grant access in Shizuku, then reconnect."; notifyChanged(); }
            });
        });
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                connecting = false;
                service = ISessionService.Stub.asInterface(binder);
                try { binder.linkToDeath(() -> main.post(() -> disconnected(binder)), 0); }
                catch (RemoteException error) { disconnected(binder); return; }
                message = "Connected.";
                notifyChanged();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.post(() -> disconnected(null));
        }
    };

    private void disconnected(IBinder binder) {
        if (binder != null && service != null && service.asBinder() != binder) return;
        service = null;
        connecting = false;
        message = "Session service disconnected. Reconnect to open a new session.";
        notifyChanged();
    }

    public boolean isReady() {
        ISessionService current = service;
        try {
            return current != null && current.asBinder().isBinderAlive() && Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException unavailable) { return false; }
    }

    private void notifyChanged() { for (Runnable listener : listeners) listener.run(); }

    private void connect(boolean requestPermission) {
        if (isReady() || connecting) { notifyChanged(); return; }
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            message = "This APK cannot use Android run-as. Install a debuggable Aether build.";
            notifyChanged(); return;
        }
        try {
            if (!Shizuku.pingBinder()) {
                message = "Shizuku is not running. Start it, then tap Connect.";
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                message = "Allow Aether to use Shizuku to start terminal sessions.";
                if (requestPermission) Shizuku.requestPermission(REQUEST_PERMISSION);
            } else {
                connecting = true;
                final int currentAttempt = ++attempt;
                message = "Connecting to the session service...";
                Shizuku.bindUserService(args, connection);
                main.postDelayed(() -> {
                    if (connecting && currentAttempt == attempt && !isReady()) {
                        connecting = false;
                        try { Shizuku.unbindUserService(args, connection, false); } catch (RuntimeException ignored) {}
                        message = "Session service did not connect within 15 seconds. Retry Connect.";
                        notifyChanged();
                    }
                }, 15000);
            }
        } catch (RuntimeException error) {
            connecting = false;
            message = "Cannot connect to Shizuku: " + error.getMessage();
        }
        notifyChanged();
    }

    /** Return true when the caller may create its session now. */
    public boolean ensureReady(Activity activity, Runnable retry, Runnable recovery) {
        if (isReady()) return true;
        AlertDialog showing = prompt.get();
        if (showing != null && showing.isShowing()) return false;
        TextView status = new TextView(activity);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        status.setPadding(padding, padding, padding, padding);
        status.setText(message);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Connect Shizuku")
            .setView(status).setPositiveButton("Connect", null)
            .setNeutralButton("Open Shizuku", null)
            .setNegativeButton("Recovery shell", (d, which) -> recovery.run()).create();
        Runnable changed = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) { dialog.dismiss(); return; }
            status.setText(message);
            if (isReady()) { dialog.dismiss(); retry.run(); }
        };
        listeners.add(changed);
        prompt = new java.lang.ref.WeakReference<>(dialog);
        dialog.setOnDismissListener(d -> {
            listeners.remove(changed);
            if (prompt.get() == dialog) prompt.clear();
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> connect(true));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (intent == null) status.setText("Install the Shizuku app, start its service, then return here.");
            else activity.startActivity(intent);
        });
        connect(false);
        return false;
    }

    public void dismissPrompt() {
        AlertDialog dialog = prompt.get();
        if (dialog != null) dialog.dismiss();
    }

    public TerminalSessionProcess.Factory factory() {
        return (executable, cwd, argv, environment, rows, columns, width, height) -> {
            ISessionService current = service;
            if (current == null || !isReady())
                throw new IllegalStateException("Shizuku session service is unavailable");
            return new SessionProcess(current, executable, SessionStorage.workingDirectory(cwd), argv,
                environment, rows, columns, width, height);
        };
    }

    public com.termux.shared.shell.command.runner.app.AppShellProcess.Factory backgroundFactory() {
        return (command, environment, cwd) -> {
            ISessionService current = service;
            if (current == null || !isReady())
                throw new java.io.IOException("Shizuku is required. Open Termux and connect Shizuku before starting a background job.");
            final String workingDirectory;
            try {
                workingDirectory = SessionStorage.workingDirectory(cwd);
            } catch (Exception error) {
                throw new java.io.IOException("Cannot prepare background working directory", error);
            }
            return new BackgroundProcess(current, command, workingDirectory, environment);
        };
    }
}
