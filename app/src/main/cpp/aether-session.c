#define _GNU_SOURCE
#include <jni.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

static void fail(JNIEnv *env, const char *message) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), message);
}

static char *copy_string(JNIEnv *env, jbyteArray text) {
    if (!text) return NULL;
    jsize length = (*env)->GetArrayLength(env, text);
    char *copy = malloc((size_t) length + 1);
    if (!copy) return NULL;
    (*env)->GetByteArrayRegion(env, text, 0, length, (jbyte *) copy);
    copy[length] = '\0';
    return copy;
}

JNIEXPORT jintArray JNICALL Java_com_termux_app_session_SessionNative_startUtf8(
        JNIEnv *env, jclass type, jbyteArray package, jbyteArray script, jobjectArray environment,
        jint rows, jint cols, jint cell_width, jint cell_height, jboolean terminal) {
    (void) type;
    char *pkg = copy_string(env, package), *command = copy_string(env, script);
    jsize count = (*env)->GetArrayLength(env, environment);
    char **envp = calloc((size_t) count + 1, sizeof(char *));
    int master = -1, slave = -1;
    int pipes[3][2] = {{-1, -1}, {-1, -1}, {-1, -1}};
    jintArray result = NULL;
    if (!pkg || !command || !envp) goto error;
    for (jsize i = 0; i < count; ++i) {
        jbyteArray value = (*env)->GetObjectArrayElement(env, environment, i);
        envp[i] = copy_string(env, value);
        (*env)->DeleteLocalRef(env, value);
        if (!envp[i]) goto error;
    }
    if (terminal) {
        master = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
        char name[128];
        if (master < 0 || grantpt(master) || unlockpt(master) || ptsname_r(master, name, sizeof(name))) goto error;
        slave = open(name, O_RDWR | O_NOCTTY | O_CLOEXEC);
        if (slave < 0) goto error;
        struct termios term;
        if (tcgetattr(slave, &term)) goto error;
        term.c_iflag |= IUTF8;
        term.c_iflag &= ~(IXON | IXOFF);
        if (tcsetattr(slave, TCSANOW, &term)) goto error;
        struct winsize size = {0};
        size.ws_row = (unsigned short) rows;
        size.ws_col = (unsigned short) cols;
        size.ws_xpixel = (unsigned short) (cols * cell_width);
        size.ws_ypixel = (unsigned short) (rows * cell_height);
        if (ioctl(slave, TIOCSWINSZ, &size)) goto error;
    } else {
        for (int i = 0; i < 3; i++) {
            if (pipe2(pipes[i], O_CLOEXEC)) goto error;
            for (int j = 0; j < 2; j++) {
                // Keep all pipe ends above stdio so dup2 cannot clobber a later source.
                if (pipes[i][j] < 3) {
                    int fd = fcntl(pipes[i][j], F_DUPFD_CLOEXEC, 3);
                    if (fd < 0) goto error;
                    close(pipes[i][j]);
                    pipes[i][j] = fd;
                }
            }
        }
    }
    // Allocate before fork so an allocation failure cannot abandon an unreported shell.
    result = (*env)->NewIntArray(env, terminal ? 2 : 4);
    if (!result) goto error;
    long max_fd = sysconf(_SC_OPEN_MAX);
    if (max_fd < 0) max_fd = 65536;
    char *argv[] = {"run-as", pkg, "/system/bin/sh", "-c", command, NULL};
    pid_t pid = fork();
    if (pid < 0) goto error;
    if (pid == 0) {
        sigset_t signals;
        sigfillset(&signals);
        sigprocmask(SIG_UNBLOCK, &signals, NULL);
        signal(SIGPIPE, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        signal(SIGTERM, SIG_DFL);
        if (setsid() < 0) _exit(126);
        if (terminal) {
            if (ioctl(slave, TIOCSCTTY, 0) < 0) _exit(126);
            if (dup2(slave, 0) < 0 || dup2(slave, 1) < 0 || dup2(slave, 2) < 0) _exit(126);
        } else {
            if (dup2(pipes[0][0], 0) < 0 || dup2(pipes[1][1], 1) < 0 || dup2(pipes[2][1], 2) < 0) _exit(126);
        }
        for (int fd = 3; fd < max_fd; ++fd) close(fd);
        execve("/system/bin/run-as", argv, envp);
        static const char message[] = "Cannot execute Android run-as.\r\n";
        write(2, message, sizeof(message) - 1);
        _exit(126);
    }
    if (terminal) {
        jint values[] = {(jint) pid, master};
        (*env)->SetIntArrayRegion(env, result, 0, 2, values);
        close(slave);
    } else {
        jint values[] = {(jint) pid, pipes[0][1], pipes[1][0], pipes[2][0]};
        (*env)->SetIntArrayRegion(env, result, 0, 4, values);
        close(pipes[0][0]); close(pipes[1][1]); close(pipes[2][1]);
    }
    for (jsize i = 0; i < count; ++i) free(envp[i]);
    free(envp); free(pkg); free(command);
    return result;
error:
    if (master >= 0) close(master);
    if (slave >= 0) close(slave);
    for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++)
        if (pipes[i][j] >= 0) close(pipes[i][j]);
    if (envp) { for (jsize i = 0; i < count; ++i) free(envp[i]); free(envp); }
    free(pkg); free(command);
    if (!(*env)->ExceptionCheck(env)) fail(env, terminal ? "Cannot create session PTY" : "Cannot create background pipes");
    return NULL;
}

// The session leader remains unreaped while inspecting its session, preventing PID/SID reuse.
static void stop_members(pid_t leader, int signal_number) {
    DIR *proc = opendir("/proc");
    if (!proc) return;
    struct dirent *entry;
    while ((entry = readdir(proc))) {
        char *end;
        long value = strtol(entry->d_name, &end, 10);
        if (*end || value < 1 || value > INT_MAX || value == leader) continue;
        pid_t pid = (pid_t) value;
        // Pin the process identity before inspecting it. Never signal a recycled numeric PID.
#if defined(__NR_pidfd_open) && defined(__NR_pidfd_send_signal)
        int pidfd = (int) syscall(__NR_pidfd_open, pid, 0);
        if (pidfd >= 0) {
            if (getsid(pid) == leader) syscall(__NR_pidfd_send_signal, pidfd, signal_number, NULL, 0);
            close(pidfd);
        }
#else
        (void) pid;
        (void) signal_number;
#endif
    }
    closedir(proc);
}

JNIEXPORT void JNICALL Java_com_termux_app_session_SessionNative_awaitExit(JNIEnv *env, jclass type, jint pid) {
    (void) env; (void) type;
    siginfo_t info;
    while (waitid(P_PID, (id_t) pid, &info, WEXITED | WNOWAIT) < 0 && errno == EINTR) {}
}

JNIEXPORT jint JNICALL Java_com_termux_app_session_SessionNative_finish(JNIEnv *env, jclass type, jint pid) {
    (void) env; (void) type;
    int status;
    pid_t result;
    do { result = waitpid(pid, &status, 0); } while (result < 0 && errno == EINTR);
    if (result < 0) return 127;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return WIFSIGNALED(status) ? -WTERMSIG(status) : 127;
}

JNIEXPORT jint JNICALL Java_com_termux_app_session_SessionNative_signal(JNIEnv *env, jclass type, jint pid, jboolean terminate) {
    (void) env; (void) type;
    stop_members(pid, terminate ? SIGKILL : SIGHUP);
    // The owning service keeps the leader unreaped until this helper returns.
    if (terminate && kill(pid, SIGKILL) < 0) return errno;
    return 0;
}
