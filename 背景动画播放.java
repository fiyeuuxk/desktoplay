import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.sun.jna.*;
import com.sun.jna.win32.*;
import com.sun.jna.ptr.*;

/**
 * 背景动画播放器 - 使用JNA实现真正的桌面背景播放
 * 通过调用Windows API将视频嵌入桌面图标后面
 */
public class 背景动画播放 extends JFrame {
    
    /**
     * mpv客户端 - 通过input-file方式与mpv通信
     * 实现真正的播放控制和时间获取
     */
    class MpvClient {
        private Process process;
        private File commandFile;
        private Thread listenerThread;
        private boolean connected = false;
        private BufferedReader logReader;
        
        // 播放状态
        private volatile long lastKnownTimeMs = 0;
        private volatile long durationMs = -1;
        private volatile boolean isPaused = false;
        private volatile boolean videoLoaded = false;
        private volatile long lastActivityTime = 0; // 最后活动时间
        
        // 启动mpv - 简化版，直接使用传统模式
        boolean start(String videoPath, int wid) {
            String mpvPath = "C:\\Users\\Administrator\\Documents\\02-软件\\05-动画\\mpv\\mpv.exe";
            
            
            
            
            
            // 构建mpv命令
            ProcessBuilder pb = new ProcessBuilder(
                "\"" + mpvPath + "\"",
                "--no-border",
                "--no-osd-bar", 
                "--loop=inf",
                "--mute=no",
                "--vo=gpu",
                "--wid=" + wid,
                "--msg-level=all=v",
                videoPath
            );
            
            pb.redirectErrorStream(true);
            
            try {
                process = pb.start();
                System.out.println("✓ mpv进程已启动，PID: " + process.pid());
                
                // 启动日志监听线程
                startLogListener();
                
                // 等待一下让mpv启动
                Thread.sleep(3000);
                
                // 检查进程状态
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    
                    
                    printStream(process.getInputStream());
                    return false;
                }
                
                connected = true;
                lastActivityTime = System.currentTimeMillis();
                
                return true;
                
            } catch (Exception e) {
                System.err.println("启动mpv失败: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        // 打印流内容
        private void printStream(InputStream stream) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = r.readLine()) != null) {
                    
                }
            } catch (Exception e) {}
        }
        
        // 启动日志监听线程
        private void startLogListener() {
            logReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            listenerThread = new Thread(() -> {
                try {
                    String line;
                    int lineNum = 0;
                    while (connected && (line = logReader.readLine()) != null) {
                        lineNum++;
                        lastActivityTime = System.currentTimeMillis(); // 更新活动时间
                        
                        // 打印所有日志（调试用）- 始终打印前20行
                        if (lineNum <= 20 || line.contains("error") || line.contains("Error") || line.contains("WARNING") || line.contains("AV:")) {
                            
                        }
                        
                        // 解析播放时间 - 多种格式
                        parseTimeFromLog(line);
                        
                        // 检测视频加载完成
                        if (line.contains("Video:") && !videoLoaded) {
                            videoLoaded = true;
                            
                        }
                        
                        // 检测音频加载完成
                        if (line.contains("Audio:") && videoLoaded) {
                            
                        }
                        
                        // 检测播放状态变化
                        if (line.contains("pause") || line.contains("PAUSE")) {
                            isPaused = true;
                            
                        }
                        if (line.contains("play") || line.contains("PLAY")) {
                            isPaused = false;
                            
                        }
                        
                        // 检测各种错误和中断
                        if (line.contains("Playback aborted")) {
                            
                        }
                        if (line.contains("Invalid") && line.contains("argument")) {
                            
                        }
                        if (line.contains("Could not open")) {
                            System.err.println("⚠ [错误] 无法打开文件: " + extractErrorDetail(line));
                        }
                        if (line.contains("No video") || line.contains("No video stream")) {
                            
                        }
                        if (line.contains("No audio") || line.contains("No audio stream")) {
                            
                        }
                        if (line.contains("Failed to initialize")) {
                            System.err.println("⚠ [错误] 初始化失败: " + extractErrorDetail(line));
                        }
                        if (line.contains("GL error")) {
                            System.err.println("⚠ [错误] OpenGL错误: " + extractErrorDetail(line));
                        }
                        if (line.contains("VO") && line.contains("error")) {
                            System.err.println("⚠ [错误] 视频输出错误: " + extractErrorDetail(line));
                        }
                        if (line.contains("AO") && line.contains("error")) {
                            System.err.println("⚠ [错误] 音频输出错误: " + extractErrorDetail(line));
                        }
                        if (line.contains("Seek") && line.contains("failed")) {
                            
                        }
                        if (line.contains("EOF") || line.contains("end of file")) {
                            
                        }
                        if (line.contains("Decoder") && line.contains("error")) {
                            
                        }
                        
                        // 打印所有日志（调试用）
                        
                    }
                } catch (Exception e) {
                    if (connected) {
                        System.err.println("监听线程错误: " + e.getMessage());
                    }
                }
            });
            listenerThread.setDaemon(true);
            listenerThread.start();
            
            // 同时监听错误流
            Thread errThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line;
                        while (connected && (line = errReader.readLine()) != null) {
                            
                        }
                    } catch (Exception e) {}
                }
            });
            errThread.setDaemon(true);
            errThread.start();
        }
        
        private String extractErrorDetail(String line) {
            // 提取错误详细信息
            int idx = line.indexOf(":");
            return idx > 0 ? line.substring(idx + 1).trim() : "";
        }
        
        // 从日志解析播放时间
        private void parseTimeFromLog(String line) {
            try {
                // 打印所有日志行（调试用）
                if (line.contains("time=") || line.contains("A-V:") || line.contains("AV:") || 
                    line.contains("Duration:") || line.contains("demuxer") || line.contains("video")) {
                    
                }
                
                // mpv日志格式可能是:
                // [...] A-V:  00:01:23 / 00:05:30
                // [...]   00:01:23 / 00:05:30
                // [...] AV: 00:01:23 / 00:05:30
                // [...] playback time: 00:01:23
                // [...] time=00:01:23
                
                // 查找形如 "XX:XX" 或 "XX:XX:XX" 的时间模式
                if (!line.contains("A-V:") && !line.contains("AV:") && 
                    !line.contains("/") && !line.contains("playback") && !line.contains("time=")) {
                    return;
                }
                
                java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");
                java.util.regex.Matcher matcher = timePattern.matcher(line);
                
                java.util.ArrayList<String> times = new java.util.ArrayList<>();
                while (matcher.find()) {
                    String t = matcher.group(0);
                    // 跳过无效时间如 "00:00"
                    if (t.equals("00:00")) continue;
                    times.add(t);
                }
                
                if (times.size() >= 2) {
                    // 第一个是当前时间，第二个是总时长
                    lastKnownTimeMs = parseTimeToMs(times.get(0));
                    durationMs = parseTimeToMs(times.get(1));
                    System.out.println("[时间解析] 当前: " + times.get(0) + ", 总时长: " + times.get(1));
                } else if (times.size() == 1) {
                    // 只有一个时间，假设是当前时间
                    lastKnownTimeMs = parseTimeToMs(times.get(0));
                    System.out.println("[时间解析] 当前: " + times.get(0));
                }
                
                // 检测视频流
                if (line.contains("Video:") && !videoLoaded) {
                    videoLoaded = true;
                    
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        
        // 将时间字符串转换为毫秒
        private long parseTimeToMs(String timeStr) {
            try {
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    int sec = Integer.parseInt(parts[parts.length - 1].trim());
                    int min = Integer.parseInt(parts[parts.length - 2].trim());
                    int hour = parts.length >= 3 ? Integer.parseInt(parts[parts.length - 3].trim()) : 0;
                    return ((hour * 60L) + min) * 60L * 1000 + sec * 1000L;
                }
            } catch (Exception e) {}
            return 0;
        }
        
        // 获取播放时间（秒）
        double getTimePosition() {
            return lastKnownTimeMs / 1000.0;
        }
        
        // 获取总时长（秒）
        double getDuration() {
            return durationMs / 1000.0;
        }
        
        boolean isPausedState() {
            return isPaused;
        }
        
        // 检查视频是否已加载（通过命令文件是否可写判断）
        boolean isVideoLoaded() {
            // 如果命令文件存在且可写，认为视频已加载
            if (commandFile != null && commandFile.exists()) {
                return true;
            }
            // 如果duration已知，也认为已加载
            return durationMs > 0;
        }
        
        // 检查最近是否有活动（进程是否存活）
        boolean wasActiveRecently() {
            // 检查mpv是否在进程列表中
            if (isMpvRunning()) {
                return true;
            }
            // 检查最后活动时间
            return System.currentTimeMillis() - lastActivityTime < 5000;
        }
        
        void pause() {
            
        }
        
        // 恢复播放
        void resume() {
            
        }
        
        // 发送按键 - 通过Windows API发送
        void sendKey(String key) {
            
        }
        
        // 跳转（秒）- 通过Windows API发送左右箭头
        void seek(double timeSeconds) {
            System.out.println("[控制] → 跳转: " + (int)timeSeconds + " 秒");
        }
        
        // 检查进程是否存活 - 直接检测任务列表中是否有mpv.exe
        boolean isAlive() {
            // 直接检查mpv.exe是否在进程列表中
            return isMpvRunning();
        }
        
        // 直接检查mpv.exe是否在进程列表中
        private boolean isMpvRunning() {
            try {
                // 使用tasklist检查mpv.exe
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq mpv.exe", "/FO", "CSV");
                pb.redirectErrorStream(true);
                Process checkProc = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkProc.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                checkProc.waitFor();
                
                String result = output.toString();
                // 如果结果中包含mpv.exe，则进程在运行
                return result.contains("mpv.exe");
            } catch (Exception e) {
                // 出错时回退
                return process != null && process.isAlive();
            }
        }
        
        // 获取退出码
        int getExitCode() {
            try {
                return process.exitValue();
            } catch (Exception e) {
                return -1;
            }
        }
        
        // 检查mpv是否正在读取命令文件
        boolean isReadingInputFile() {
            if (commandFile == null || !commandFile.exists()) {
                return false;
            }
            // 检查文件是否在变小（mpv读取后文件内容会被清空或删除）
            return commandFile.length() < 100; // 如果文件很小，说明可能被mpv读取了
        }
        
        // 停止
        void stop() {
            connected = false;
            if (process != null) {
                process.destroy();
                // 强制结束
                try {
                    Process killer = new ProcessBuilder("taskkill", "/F", "/PID", 
                        String.valueOf(process.pid())).start();
                    killer.waitFor();
                } catch (Exception e) {}
            }
            // 删除命令文件
            if (commandFile != null) {
                commandFile.delete();
            }
            
        }
    }
    
    // JNA接口定义 - 调用Windows API
    public interface User32 extends StdCallLibrary {
        User32 INSTANCE = (User32) Native.load("user32", User32.class, 
            W32APIOptions.DEFAULT_OPTIONS);
        
        int FindWindow(String lpClassName, String lpWindowName);
        int SendMessageTimeout(int hWnd, int Msg, int wParam, int lParam, 
            int fuFlags, int uTimeout, int[] lpdwResult);
        int SetParent(int hWndChild, int hWndNewParent);
        boolean SetWindowPos(int hWnd, int hWndInsertAfter, int X, int Y, 
            int cx, int cy, int uFlags);
        boolean SetLayeredWindowAttributes(int hwnd, int crKey, byte bAlpha, int dwFlags);
        int SetWindowLong(int hWnd, int nIndex, int dwNewLong);
        boolean EnumWindows(WNDENUMPROC lpEnumFunc, int lParam);
        int GetWindowTextLength(int hWnd);
        int GetWindowText(int hWnd, char[] lpString, int nMaxCount);
        int GetWindow(int hWnd, int uCmd);
        int FindWindowEx(int hWndParent, int hWndChildAfter, String lpszClass, String lpszWindow);
        int GetParent(int hWnd);
        int GetClassNameA(int hWnd, byte[] lpClassName, int nMaxCount);
        boolean IsWindowVisible(int hWnd);
        boolean GetWindowRect(int hWnd, int[] lpRect);
        int CreateWindowEx(int dwExStyle, String lpClassName, String lpWindowName, int dwStyle,
            int x, int y, int nWidth, int nHeight, int hWndParent, int hMenu, int hInstance, int lpParam);
        boolean ShowWindow(int hWnd, int nCmdShow);
        boolean UpdateWindow(int hWnd);
        boolean PostMessage(int hWnd, int Msg, int wParam, int lParam);
        int SendInput(int nInputs, byte[][] pInputs, int cbSize);
        boolean SetForegroundWindow(int hWnd);
        boolean BringWindowToTop(int hWnd);
        boolean IsWindow(int hWnd);
        
        interface WNDENUMPROC extends Callback {
            boolean callback(int hWnd, int lParam);
        }
        
        // 常量定义
        int SMTO_ABORTIFHUNG = 0x0002;
        int WM_SPAWN_WORKERW = 0x052C;
        int WM_KEYDOWN = 0x0100;
        int WM_KEYUP = 0x0101;
        int WM_CHAR = 0x0102;
        int WM_SYSCOMMAND = 0x0112;
        int SC_KEYMENU = 0xF100;
        int GW_HWNDNEXT = 2;
        int GW_CHILD = 5;
        int GW_OWNER = 4;
        int GWL_EXSTYLE = -20;
        
        int WS_EX_LAYERED = 0x00080000;
        int WS_EX_TRANSPARENT = 0x00000020;
        int WS_EX_TOOLWINDOW = 0x00000080;
        int WS_EX_APPWINDOW = 0x00040000;
        int WS_EX_NOREDIRECTIONBITMAP = 0x00200000;
        
        int WS_CHILD = 0x40000000;
        int WS_VISIBLE = 0x10000000;
        
        int LWA_COLORKEY = 0x00000001;
        int LWA_ALPHA = 0x00000002;
        
        int HWND_TOPMOST = -1;
        int HWND_BOTTOM = 1;
        int SWP_NOSIZE = 0x0001;
        int SWP_NOMOVE = 0x0002;
        int SWP_NOZORDER = 0x0004;
        int SWP_NOACTIVATE = 0x0010;
        int SWP_SHOWWINDOW = 0x0040;
        int SWP_NOOWNERZORDER = 0x0200;
        int SWP_NOSENDCHANGING = 0x0400;
        int SWP_HIDEWINDOW = 0x0080;
        
        int WS_POPUP = 0x80000000;
    }
    
    // Kernel32 接口（用于IPC named pipe操作）
    interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = (Kernel32) Native.load("kernel32", Kernel32.class);
        int GetLastError();
        long CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode, 
                        Pointer lpSecurityAttributes, int dwCreationDisposition, 
                        int dwFlagsAndAttributes, long hTemplateFile);
        boolean WriteFile(long hFile, byte[] lpBuffer, int nNumberOfBytesToWrite, 
                          int[] lpNumberOfBytesWritten, Pointer lpOverlapped);
        boolean CloseHandle(long hObject);
    }
    
    private static final String CONFIG_FILE = "背景动画播放配置.properties";
    private static final String LAST_FOLDER_KEY = "last.folder";
    private static final String LAST_VIDEO_KEY = "last.video";
    
    private Properties config;
    private SystemTray tray;
    private TrayIcon trayIcon;
    private JLabel statusLabel;
    private JLabel playingLabel;
    private JLabel timeLabel;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private boolean isLandscape = true; // 横屏模式
    private long startTime = 0;
    private String currentVideoPath;
    private Process videoProcess;
    private String windowTitle = "背景动画播放器";
    private JFrame embedFrame;
    private Process mpvProcess;
    private int workerWHandle = 0;
    private int currentEmbedWindow = 0;
    private String ipcServerPath = null;
    private String mpvPath = "C:\\Users\\Administrator\\Documents\\02-软件\\05-动画\\mpv\\mpv.exe";
    private long jvmPid;
    private Thread timeUpdateThread = null;
    private volatile boolean timeUpdateRunning = false;
    
    // IPC读取线程
    private Thread ipcReadThread = null;
    private java.io.BufferedReader ipcReader = null;
    private java.io.OutputStreamWriter ipcWriter = null;
    
    // 当前播放时间和总时长
    private volatile double currentTimePos = 0;
    private volatile double currentDuration = 0;
    private volatile boolean hasDuration = false;
    private volatile double userSeekTarget = -1; // 用户拖动进度条的目标位置（秒）
    private volatile long lastUserSeekTime = 0;  // 最后一次用户拖动的时间
    
    // 解析mpv输出获取时间
    private void startLogParser() {
        new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                 new java.io.InputStreamReader(mpvProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 解析格式: 00:00:00/00:03:15 或 123.5/240.0
                    if (line.contains("/") && !line.contains("●") && !line.contains("File tags")) {
                        parseTimeFromStatus(line);
                    }
                }
            } catch (Exception e) {}
        }).start();
    }
    
    private void parseTimeFromStatus(String line) {
        try {
            String trimmed = line.trim();
            if (!trimmed.contains("/")) return;
            
            String[] parts = trimmed.split("/");
            if (parts.length >= 2) {
                String posStr = parts[0].trim();
                String durStr = parts[1].trim();
                
                double pos = parseTimeValue(posStr);
                double dur = parseTimeValue(durStr);
                
                if (pos >= 0) currentTimePos = pos;
                if (dur > 0) {
                    currentDuration = dur;
                    hasDuration = true;
                }
                
                SwingUtilities.invokeLater(() -> {
                    updateTimeDisplay();
                });
            }
        } catch (Exception e) {}
    }
    
    private double parseTimeValue(String timeStr) {
        // 尝试解析为数字（秒）
        try {
            return Double.parseDouble(timeStr);
        } catch (NumberFormatException e) {}
        
        // 尝试解析为 HH:mm:ss 格式
        String[] parts = timeStr.split(":");
        try {
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600 + 
                       Integer.parseInt(parts[1]) * 60 + 
                       Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {}
        
        return 0;
    }
    
    private double parseTimeString(String timeStr) {
        // 格式: 00:02:30 或 02:30
        String[] parts = timeStr.split(":");
        try {
            if (parts.length == 3) {
                return Integer.parseInt(parts[0]) * 3600 + 
                       Integer.parseInt(parts[1]) * 60 + 
                       Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {}
        return 0;
    }
    
    // mpv客户端（用于真正的播放控制）
    private MpvClient mpvClient;
    
    // 进度条相关
    private JSlider progressSlider;
    private boolean isUserDragging = false;
    
    // 显示更新定时器（2秒更新一次显示）
    private javax.swing.Timer displayUpdateTimer;
    
    // 视频格式支持
    private static final String[] VIDEO_EXTENSIONS = {
        "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ogv", 
        "mpg", "mpeg", "m2v", "f4v", "rmvb", "rm", "asf", "vob", "ts", "mts", "m2ts"
    };
    
    public 背景动画播放() {
        super("背景动画播放器");
        loadConfig();
        initializeUI();
        setupSystemTray();
        startDisplayUpdater();
    }
    
    // 启动显示更新器
    private void startDisplayUpdater() {
        displayUpdateTimer = new javax.swing.Timer(2000, e -> refreshDisplay());
        displayUpdateTimer.start();
        
    }
    
    private void loadConfig() {
        config = new Properties();
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
            }
        } catch (IOException e) {
            System.err.println("加载配置文件失败: " + e.getMessage());
        }
    }
    
    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "背景动画播放器配置");
        } catch (IOException e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
        }
    }
    
    private void initializeUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(500, 280);
        setLocationRelativeTo(null);
        setTitle(windowTitle);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 标题面板
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        
        JLabel titleLabel = new JLabel("背景动画播放器");
        titleLabel.setFont(new Font("宋体", Font.BOLD, 18));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(new Color(41, 128, 185));
        
        titlePanel.add(Box.createVerticalGlue());
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalGlue());
        
        // 状态面板（显示正在播放的文件名）
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("就绪");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font("宋体", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(127, 140, 141));
        statusPanel.add(statusLabel, BorderLayout.NORTH);
        
        // 正在播放标签
        playingLabel = new JLabel("未播放视频");
        playingLabel.setHorizontalAlignment(SwingConstants.CENTER);
        playingLabel.setFont(new Font("宋体", Font.BOLD, 14));
        playingLabel.setForeground(new Color(52, 73, 94));
        playingLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusPanel.add(playingLabel, BorderLayout.CENTER);
        
        // 选择视频按钮
        JButton selectButton = new JButton("选择视频");
        selectButton.setFont(new Font("宋体", Font.BOLD, 12));
        selectButton.setBackground(new Color(52, 152, 219));
        selectButton.setForeground(Color.BLACK);
        selectButton.setFocusPainted(false);
        selectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        selectButton.addActionListener(e -> selectAndPlayVideo());
        statusPanel.add(selectButton, BorderLayout.SOUTH);
        
        // 控制面板（播放器控制）
        JPanel controlPanel = new JPanel(new BorderLayout(10, 10));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 15, 20));
        
        // 播放/暂停按钮
        JButton playPauseButton = new JButton("播放/暂停 (空格)");
        playPauseButton.setFont(new Font("宋体", Font.PLAIN, 11));
        playPauseButton.setBackground(new Color(46, 204, 113));
        playPauseButton.setForeground(Color.BLACK);
        playPauseButton.setFocusPainted(false);
        playPauseButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        playPauseButton.setActionCommand("playPause");
        playPauseButton.addActionListener(e -> {
            if ("playPause".equals(e.getActionCommand())) {
                togglePause();
            }
        });
        
        // 进度条 - 加长并自适应
        progressSlider = new JSlider(0, 100, 0);
        progressSlider.setFocusable(false);
        progressSlider.setBackground(new Color(236, 240, 241));
        progressSlider.setPreferredSize(new Dimension(400, 30));
        progressSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        progressSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isUserDragging = true;
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                isUserDragging = false;
                // 跳转到指定位置
                int pos = progressSlider.getValue();
                if (hasDuration && currentDuration > 0) {
                    double seekTime = (pos / 100.0) * currentDuration;
                    userSeekTarget = seekTime;
                    lastUserSeekTime = System.currentTimeMillis();
                    seekTo(seekTime);
                }
            }
        });
        
        // 时间标签
        timeLabel = new JLabel("0:00 / 0:00");
        timeLabel.setFont(new Font("宋体", Font.PLAIN, 11));
        timeLabel.setForeground(new Color(127, 140, 141));
        
        // 左箭头按钮
        JButton leftButton = new JButton("<-");
        leftButton.setFont(new Font("宋体", Font.BOLD, 12));
        leftButton.setBackground(new Color(52, 152, 219));
        leftButton.setForeground(Color.BLACK);
        leftButton.setFocusPainted(false);
        leftButton.setPreferredSize(new Dimension(50, 30));
        leftButton.addActionListener(e -> seekRelative(-5));
        
        // 右箭头按钮
        JButton rightButton = new JButton("->");
        rightButton.setFont(new Font("宋体", Font.BOLD, 12));
        rightButton.setBackground(new Color(52, 152, 219));
        rightButton.setForeground(Color.BLACK);
        rightButton.setFocusPainted(false);
        rightButton.setPreferredSize(new Dimension(50, 30));
        rightButton.addActionListener(e -> seekRelative(5));
        
        // 控制按钮行 - 使用BoxLayout使进度条自适应伸展
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(leftButton);
        buttonsPanel.add(Box.createHorizontalStrut(10));
        buttonsPanel.add(progressSlider);
        buttonsPanel.add(Box.createHorizontalStrut(10));
        buttonsPanel.add(rightButton);
        
        // 创建圆形旋转按钮（横竖屏切换）
        JButton rotateButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // 绘制圆形背景
                g2d.setColor(new Color(155, 89, 182));
                g2d.fillOval(0, 0, getWidth(), getHeight());
                
                // 绘制旋转图标（完整圆圈 + 向下箭头）
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int radius = 11;
                
                // 画完整圆圈
                g2d.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
                
                // 画向下箭头（在右边正中，以右边线为中心）
                int arrowSize = 5;
                int arrowX = cx + radius;  // 圆圈右边线上
                int arrowY = cy;
                
                // 箭头（向下，尖端朝下）
                int[] arrowXPoints = {arrowX, arrowX - arrowSize, arrowX + arrowSize};
                int[] arrowYPoints = {arrowY + arrowSize, arrowY - arrowSize / 2, arrowY - arrowSize / 2};
                g2d.fillPolygon(arrowXPoints, arrowYPoints, 3);
                
                g2d.dispose();
            }
        };
        rotateButton.setPreferredSize(new Dimension(36, 36));
        rotateButton.setFocusPainted(false);
        rotateButton.setContentAreaFilled(false);
        rotateButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        rotateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rotateButton.setToolTipText("切换横屏/竖屏");
        rotateButton.addActionListener(e -> toggleRotation());
        
        // 底部按钮行
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        bottomPanel.add(rotateButton);
        bottomPanel.add(playPauseButton);
        bottomPanel.add(timeLabel);
        
        controlPanel.add(buttonsPanel, BorderLayout.CENTER);
        controlPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(statusPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // 全局键盘事件监听 - 发送给mpv
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    e.consume();
                    togglePause();
                    sendKeyToMpv("space");  // 发送空格键
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    e.consume();
                    seekRelative(-5);
                    sendKeyToMpv("left");
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    e.consume();
                    seekRelative(5);
                    sendKeyToMpv("right");
                }
            }
            return false;
        });
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                setVisible(false);
            }
        });
    }
    
    // 发送按键到mpv（IPC方式）
    private void sendKeyToMpv(String key) {
        try {
            
            
            // 尝试通过IPC发送命令
            boolean success = sendIpcCommand(key);
            if (success) {
                
                return;
            }
            
            
            
            // IPC失败，尝试PostMessage到嵌入窗口
            int vkCode = getVkCode(key);
            if (vkCode == 0) return;
            
            boolean down = User32.INSTANCE.PostMessage(currentEmbedWindow, 0x0100, vkCode, 0);
            boolean up = User32.INSTANCE.PostMessage(currentEmbedWindow, 0x0101, vkCode, 0);
            
            
        } catch (Exception e) {
            System.err.println("[控制] 错误: " + e.getMessage());
        }
    }
    
    // 通过IPC发送命令到mpv
    private boolean sendIpcCommand(String key) {
        if (ipcServerPath == null) {
            
            return false;
        }
        
        // 构建JSON命令
        String jsonCmd;
        switch (key.toLowerCase()) {
            case "space": jsonCmd = "{\"command\":[\"cycle\",\"pause\"]}\n"; break;
            case "left": jsonCmd = "{\"command\":[\"seek\",-5,\"relative\"]}\n"; break;
            case "right": jsonCmd = "{\"command\":[\"seek\",5,\"relative\"]}\n"; break;
            default: return false;
        }
        
        // 尝试连接named pipe并发送命令
        try {
            // 使用RandomAccessFile连接Windows命名管道
            java.io.RandomAccessFile pipe = new java.io.RandomAccessFile(ipcServerPath, "rw");
            
            // 发送命令
            pipe.writeBytes(jsonCmd);
            pipe.close();
            
            System.out.println("[IPC] 已写入管道: " + jsonCmd.trim());
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    // 初始化IPC通信 - 创建持续的读写线程
    private void initIpcCommunication() {
        final java.io.File pipeFile = new java.io.File(ipcServerPath);
        
        // 等待管道创建
        new Thread(() -> {
            int waitCount = 0;
            while (!pipeFile.exists() && waitCount < 30) {
                try { Thread.sleep(100); } catch (Exception e) {}
                waitCount++;
            }
            
            if (!pipeFile.exists()) return;
            
            // 启动读取线程
            new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (timeUpdateRunning) {
                    try {
                        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(pipeFile, "r");
                        int bytesRead = raf.read(buffer);
                        raf.close();
                        
                        if (bytesRead > 0) {
                            String response = new String(buffer, 0, bytesRead);
                            parseIpcResponse(response);
                        }
                    } catch (Exception e) {
                        // 忽略
                    }
                    try { Thread.sleep(200); } catch (Exception e) {}
                }
            }).start();
            
            // 启动写入线程 - 定期请求时间
            new Thread(() -> {
                while (timeUpdateRunning) {
                    try {
                        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(pipeFile, "rw");
                        raf.writeBytes("{\"command\":[\"get_property\",\"time-pos\"]}\n");
                        raf.close();
                        Thread.sleep(500);
                    } catch (Exception e) {
                        try { Thread.sleep(500); } catch (Exception ex) {}
                    }
                }
            }).start();
        }).start();
    }
    
    // 解析IPC响应
    private void parseIpcResponse(String response) {
        try {
            // 解析 time-pos
            if (response.contains("\"name\":\"time-pos\"")) {
                int dataStart = response.indexOf("\"data\":");
                if (dataStart != -1) {
                    int valueStart = dataStart + 7;
                    int valueEnd = response.indexOf(",", valueStart);
                    if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                    String timeStr = response.substring(valueStart, valueEnd).trim();
                    currentTimePos = Double.parseDouble(timeStr);
                    
                    // 更新UI
                    SwingUtilities.invokeLater(() -> {
                        updateTimeDisplay();
                    });
                }
            }
            
            // 解析 duration
            if (response.contains("\"name\":\"duration\"")) {
                int dataStart = response.indexOf("\"data\":");
                if (dataStart != -1) {
                    int valueStart = dataStart + 7;
                    int valueEnd = response.indexOf(",", valueStart);
                    if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                    String timeStr = response.substring(valueStart, valueEnd).trim();
                    currentDuration = Double.parseDouble(timeStr);
                    hasDuration = true;
                }
            }
        } catch (Exception e) {
            // 解析失败
        }
    }
    
    // 发送IPC命令（内部使用）
    private void sendIpcCmd(String cmd) {
        try {
            String jsonCmd;
            switch (cmd) {
                case "get_time":
                    jsonCmd = "{\"command\":[\"get_property\",\"time-pos\"]}\n";
                    break;
                case "get_duration":
                    jsonCmd = "{\"command\":[\"get_property\",\"duration\"]}\n";
                    break;
                default:
                    return;
            }
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(ipcServerPath, "rw");
            raf.writeBytes(jsonCmd);
            raf.close();
        } catch (Exception e) {}
    }
    
    // 跳转到指定时间（秒）
    private void seekTo(double seconds) {
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(ipcServerPath, "rw");
            String jsonCmd = String.format("{\"command\":[\"seek\",%.1f,\"absolute\"]}\n", seconds);
            raf.writeBytes(jsonCmd);
            raf.close();
        } catch (Exception e) {
            // 发送失败
        }
    }
    
    // 通过IPC获取总时长
    private double getMpvDuration() {
        if (ipcServerPath == null) return -1;
        
        try {
            java.io.RandomAccessFile pipe = new java.io.RandomAccessFile(ipcServerPath, "rw");
            pipe.writeBytes("{\"command\":[\"get_property\",\"duration\"]}\n");
            
            byte[] buffer = new byte[1024];
            int bytesRead = pipe.read(buffer);
            pipe.close();
            
            if (bytesRead > 0) {
                String response = new String(buffer, 0, bytesRead);
                int dataStart = response.indexOf("\"data\":");
                if (dataStart != -1) {
                    int valueStart = dataStart + 7;
                    int valueEnd = response.indexOf(",", valueStart);
                    if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                    String timeStr = response.substring(valueStart, valueEnd).trim();
                    return Double.parseDouble(timeStr);
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return -1;
    }
    
    // 启动时间更新线程
    private void startTimeUpdater() {
        timeUpdateRunning = true;
        
        // 请求获取总时长
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (Exception e) {}
            if (!timeUpdateRunning) return;
            try {
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(ipcServerPath, "rw");
                raf.writeBytes("{\"command\":[\"get_property\",\"duration\"]}\n");
                raf.close();
            } catch (Exception e) {}
        }).start();
        
        // 启动时间更新线程
        timeUpdateThread = new Thread(() -> {
            while (timeUpdateRunning && mpvProcess != null && mpvProcess.isAlive()) {
                try {
                    SwingUtilities.invokeLater(() -> {
                        updateTimeDisplay();
                    });
                    Thread.sleep(500);
                } catch (Exception e) {
                    break;
                }
            }
        });
        timeUpdateThread.setDaemon(true);
        timeUpdateThread.start();
    }
    
    // 更新时间和进度条显示
    private void updateTimeDisplay() {
        String timeStr = formatTime(currentTimePos);
        String totalStr = formatTime(currentDuration);
        timeLabel.setText(timeStr + " / " + totalStr);
        
        // 如果最近有用户拖动（1秒内），使用用户目标位置，否则使用实际播放时间
        long now = System.currentTimeMillis();
        // 用户拖动时不更新进度条
        if (!isUserDragging && currentDuration > 0) {
            double posToUse = currentTimePos;
            // 用户释放后1秒内使用用户拖动的目标位置
            if (userSeekTarget >= 0 && (now - lastUserSeekTime) <= 1000) {
                posToUse = userSeekTarget;
                // 1秒后清除目标位置
                if ((now - lastUserSeekTime) > 500) {
                    userSeekTarget = -1;
                }
            }
            int value = (int)((posToUse / currentDuration) * 100);
            progressSlider.setValue(value);
        }
    }
    
    // 停止时间更新线程
    private void stopTimeUpdater() {
        timeUpdateRunning = false;
        if (timeUpdateThread != null) {
            timeUpdateThread.interrupt();
            timeUpdateThread = null;
        }
    }
    
    // 重启mpv
    private void restartMpv() {
        try {
            String videoPath = currentVideoPath;
            if (videoPath == null) return;
            int embedWindow = createBackgroundWindow();
            currentEmbedWindow = embedWindow;
            ipcServerPath = "\\\\.\\pipe\\mpv-ipc-" + jvmPid;
            String cmd = String.format("\"%s\" --no-border --no-osd-bar --loop=inf --mute=no --vo=gpu --wid=%d --input-ipc-server=\"%s\" \"%s\"",
                mpvPath, embedWindow, ipcServerPath, videoPath);
            Process mpvProc = Runtime.getRuntime().exec(cmd);
            Thread.sleep(3000);
            if (mpvProc.isAlive()) {
                mpvProcess = mpvProc;
                startTimeUpdater();
            }
        } catch (Exception e) {}
    }
    
    // 格式化时间（秒 -> HH:MM:SS）
    private String formatTime(double seconds) {
        if (seconds < 0) return "--:--";
        int totalSec = (int)seconds;
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int secs = totalSec % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
    
    // 查找mpv窗口（通过类名，排除smtc）
    private long findMpvWindow() {
        final long[] foundHandle = {0};
        final String[] foundClass = {null};
        final String[] foundTitle = {null};
        
        
        
        // 枚举所有窗口
        User32.INSTANCE.EnumWindows((hWnd, lParam) -> {
            // 获取类名
            byte[] classBytes = new byte[256];
            User32.INSTANCE.GetClassNameA(hWnd, classBytes, 256);
            String className = new String(classBytes).trim();
            
            // 获取标题
            char[] title = new char[256];
            User32.INSTANCE.GetWindowText(hWnd, title, 256);
            String windowTitle = new String(title).trim();
            
            // 跳过完全空的窗口
            if (className.isEmpty() && windowTitle.isEmpty()) return true;
            
            // 打印所有窗口（调试用）
            if (className.contains("mpv") || windowTitle.contains("mpv") || 
                className.contains("Mpv") || windowTitle.contains("Mpv") ||
                className.toLowerCase().contains("mpv")) {
                
            }
            
            // 跳过smtc
            if (className.toLowerCase().contains("smtc") || windowTitle.toLowerCase().contains("smtc")) {
                return true;
            }
            
            // 方法1：通过类名查找
            if ((className.equals("mpv") || className.equals("Mpv") || className.toLowerCase().equals("mpv")) 
                && !windowTitle.toLowerCase().contains("smtc")) {
                
                foundHandle[0] = hWnd;
                foundClass[0] = className;
                foundTitle[0] = windowTitle;
                return false;
            }
            
            // 方法2：通过窗口标题查找
            if ((windowTitle.contains("mpv") || windowTitle.contains("Mpv") ||
                 windowTitle.endsWith(".mp4") || windowTitle.endsWith(".avi") || 
                 windowTitle.endsWith(".mkv") || windowTitle.endsWith(".flv") || 
                 windowTitle.endsWith(".wmv")) && !windowTitle.toLowerCase().contains("smtc")) {
                if (foundHandle[0] == 0) {
                    
                    foundHandle[0] = hWnd;
                    foundClass[0] = className;
                    foundTitle[0] = windowTitle;
                }
                // 继续查找更好的匹配
            }
            
            return true;
        }, 0);
        
        if (foundHandle[0] != 0) {
            
        } else {
            
        }
        
        return foundHandle[0];
    }
    
    // 将快捷键转换为mpv命令
    private String getMpvCommand(String key) {
        switch (key.toLowerCase()) {
            case "space": return "{\"command\":[\"cycle\",\"pause\"]}";
            case "left": return "{\"command\":[\"seek\",-5,\"relative\"]}";
            case "right": return "{\"command\":[\"seek\",5,\"relative\"]}";
            default: return null;
        }
    }
    
    // 使用PostMessage发送按键到窗口
    private void sendKeyPress(long hwnd, int vkCode) {
        try {
            // 发送KEYDOWN
            boolean result1 = User32.INSTANCE.PostMessage((int)hwnd, User32.WM_KEYDOWN, vkCode, 0);
            Thread.sleep(20);
            // 发送KEYUP
            boolean result2 = User32.INSTANCE.PostMessage((int)hwnd, User32.WM_KEYUP, vkCode, 0);
            
            
        } catch (Exception e) {
            System.err.println("[PostMessage] 错误: " + e.getMessage());
        }
    }
    
    // 将快捷键名称转换为虚拟键码
    private int getVkCode(String key) {
        switch (key.toLowerCase()) {
            case "space": case " ": return 0x20;  // 空格
            case "left": return 0x25;  // 左箭头
            case "right": return 0x27;  // 右箭头
            default: return 0;
        }
    }
    
    private JButton createStyledButton(String text, Color backgroundColor) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(100, 35));
        button.setFont(new Font("微软雅黑", Font.BOLD, 12));
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(backgroundColor.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(backgroundColor);
            }
        });
        
        return button;
    }
    
    private void setupSystemTray() {
        if (SystemTray.isSupported()) {
            tray = SystemTray.getSystemTray();
            
            Image image = createTrayImage();
            trayIcon = new TrayIcon(image, "背景动画播放器");
            trayIcon.setImageAutoSize(true);
            
            trayIcon.setImageAutoSize(true);
            
            // 使用JPopupMenu并手动控制显示位置
            JPopupMenu popup = new JPopupMenu();
            popup.setFont(new Font("宋体", Font.PLAIN, 12));
            
            JMenuItem showItem = new JMenuItem("显示窗口");
            showItem.setFont(new Font("宋体", Font.PLAIN, 12));
            showItem.addActionListener(e -> {
                setVisible(true);
                setState(JFrame.NORMAL);
            });
            
            JMenuItem selectItem = new JMenuItem("选择视频");
            selectItem.setFont(new Font("宋体", Font.PLAIN, 12));
            selectItem.addActionListener(e -> selectAndPlayVideo());
            
            JMenuItem pauseItem = new JMenuItem("播放/暂停");
            pauseItem.setFont(new Font("宋体", Font.PLAIN, 12));
            pauseItem.addActionListener(e -> togglePause());
            
            JMenuItem stopItem = new JMenuItem("停止");
            stopItem.setFont(new Font("宋体", Font.PLAIN, 12));
            stopItem.addActionListener(e -> stopVideo());
            
            JMenuItem exitItem = new JMenuItem("退出");
            exitItem.setFont(new Font("宋体", Font.PLAIN, 12));
            exitItem.addActionListener(e -> exitApplication());
            
            popup.add(showItem);
            popup.addSeparator();
            popup.add(selectItem);
            popup.add(pauseItem);
            popup.add(stopItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            // 监听托盘图标鼠标点击，手动显示JPopupMenu
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopupMenu();
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopupMenu();
                    }
                }
                
                private void showPopupMenu() {
                    // 获取当前鼠标位置
                    PointerInfo pi = MouseInfo.getPointerInfo();
                    Point location = pi.getLocation();
                    popup.show(null, location.x, location.y);
                }
            });
            
            try {
                tray.add(trayIcon);
                trayIcon.displayMessage("背景动画播放器", 
                    "程序已启动，右键点击图标查看菜单", 
                    TrayIcon.MessageType.INFO);
            } catch (AWTException e) {
                System.err.println("添加托盘图标失败: " + e.getMessage());
            }
        }
    }
    
    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(new Color(52, 152, 219));
        g.fillRect(0, 0, 16, 16);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("宋体", Font.BOLD, 10));
        g.drawString("背", 2, 11);
        
        g.dispose();
        return image;
    }
    
    void selectAndPlayVideo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择视频文件 - 背景动画播放器");
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "所有视频文件 (" + String.join(", ", VIDEO_EXTENSIONS) + ")", 
            VIDEO_EXTENSIONS
        );
        fileChooser.setFileFilter(filter);
        
        String lastFolder = config.getProperty(LAST_FOLDER_KEY);
        if (lastFolder != null) {
            File lastDir = new File(lastFolder);
            if (lastDir.exists() && lastDir.isDirectory()) {
                fileChooser.setCurrentDirectory(lastDir);
            }
        } else {
            File defaultDir = new File("C:\\Users\\" + System.getProperty("user.name") + "\\Videos");
            if (defaultDir.exists() && defaultDir.isDirectory()) {
                fileChooser.setCurrentDirectory(defaultDir);
            }
        }
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            config.setProperty(LAST_FOLDER_KEY, selectedFile.getParent());
            config.setProperty(LAST_VIDEO_KEY, selectedFile.getAbsolutePath());
            saveConfig();
            
            playVideo(selectedFile.getAbsolutePath());
        }
    }
    
    private void playVideo(String videoPath) {
        try {
            stopVideo();
            
            currentVideoPath = videoPath;
            File videoFile = new File(videoPath);
            
            // 更新界面显示
            playingLabel.setText("Playing: " + videoFile.getName());
            statusLabel.setText("Initializing...");
            progressSlider.setValue(0);
            
            // 使用JNA调用Windows API进行真正的背景播放
            int embedWindow = createBackgroundWindow();
            
            if (embedWindow == 0) {
                throw new Exception("无法创建背景窗口");
            }
            
            // 跳过IPC模式，直接使用传统方式启动mpv
            
            playAsBackground(videoPath);
            
            isPlaying = true;
            isPaused = false;
            startTime = System.currentTimeMillis();
            
            if (trayIcon != null) {
                trayIcon.displayMessage("正在播放", 
                    videoFile.getName(), 
                    TrayIcon.MessageType.INFO);
            }
            
        } catch (Exception e) {
            statusLabel.setText("错误: " + e.getMessage());
            playingLabel.setText("未播放视频");
            isPlaying = false;
            JOptionPane.showMessageDialog(this, 
                "播放失败: " + e.getMessage(),
                "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    // 跳转到指定位置（百分比）- 真正的mpv跳转
    private void seekToPosition(int percentage) {
        
        
        if (mpvClient != null && mpvClient.isAlive()) {
            double duration = mpvClient.getDuration();
            if (duration > 0) {
                double targetTime = (percentage / 100.0) * duration;
                mpvClient.seek(targetTime);
            }
        } else {
            
        }
    }
    
    // 相对跳转（秒）
    private void seekRelative(int seconds) {
        System.out.println("[控制] 跳转: " + (seconds > 0 ? "+" : "") + seconds + " 秒");
        
        long mpvWindow = findMpvWindow();
        if (mpvWindow != 0) {
            int vkKey = seconds > 0 ? 0x27 : 0x25;
            for (int i = 0; i < Math.abs(seconds); i++) {
                            User32.INSTANCE.PostMessage((int)mpvWindow, User32.WM_KEYDOWN, vkKey, 0);
                            User32.INSTANCE.PostMessage((int)mpvWindow, User32.WM_KEYUP, vkKey, 0);                try { Thread.sleep(50); } catch (Exception e) {}
            }
            
        } else {
            
        }
    }
    
    /**
     * 创建背景播放窗口，返回窗口句柄
     */
    private int createBackgroundWindow() throws Exception {
        
        
        // 获取屏幕尺寸
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;
        
        
        // 第一步：找到Progman窗口
        
        int progman = User32.INSTANCE.FindWindow("Progman", null);
        if (progman == 0) {
            throw new Exception("找不到Progman窗口");
        }
        
        
        // 第二步：发送消息创建WorkerW
        
        int[] result = new int[1];
        User32.INSTANCE.SendMessageTimeout(progman, 0x052C, 0, 0, 2, 1000, result);
        Thread.sleep(500);
        
        // 第三步：查找正确的WorkerW（包含SHELLDLL_DefView的）
        
        int workerW = 0;
        int shellDefView = 0;
        
        // 方法1：直接查找SHELLDLL_DefView，再找其父窗口
        shellDefView = User32.INSTANCE.FindWindowEx(progman, 0, "SHELLDLL_DefView", null);
        if (shellDefView != 0) {
            
            workerW = User32.INSTANCE.GetParent(shellDefView);
            
        }
        
        // 方法2：枚举查找包含SHELLDLL_DefView的WorkerW
        if (workerW == 0) {
            final int[] found = {0};
            final int[] foundShell = {0};
            User32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
                @Override
                public boolean callback(int hWnd, int lParam) {
                    byte[] className = new byte[256];
                    User32.INSTANCE.GetClassNameA(hWnd, className, 256);
                    String wc = new String(className).trim();
                    
                    if ("WorkerW".equals(wc)) {
                        int sv = User32.INSTANCE.FindWindowEx(hWnd, 0, "SHELLDLL_DefView", null);
                        if (sv != 0) {
                            
                            found[0] = hWnd;
                            foundShell[0] = sv;
                            return false;
                        }
                    }
                    return true;
                }
            }, 0);
            workerW = found[0];
            shellDefView = foundShell[0];
        }
        
        // 方法3：查找Progman的直接子窗口
        if (workerW == 0) {
            workerW = User32.INSTANCE.FindWindowEx(progman, 0, "WorkerW", null);
            if (workerW != 0) {
                shellDefView = User32.INSTANCE.FindWindowEx(workerW, 0, "SHELLDLL_DefView", null);
                
            }
        }
        
        if (workerW == 0) {
            
        } else {
            
        }
        
        // 第四步：创建嵌入窗口
        
        
        int embedWindow = User32.INSTANCE.CreateWindowEx(
            0, "Static", "",
            User32.WS_CHILD | User32.WS_VISIBLE,
            0, 0, width, height,
            progman, 0, 0, 0);
        
        int lastError = Kernel32.INSTANCE.GetLastError();
        
        
        if (embedWindow == 0) {
            throw new Exception("无法创建嵌入窗口，错误码: " + lastError);
        }
        
        
        
        // 尝试设置层级
        if (shellDefView != 0) {
            User32.INSTANCE.SetWindowPos(embedWindow, shellDefView, 
                0, 0, width, height, User32.SWP_NOACTIVATE);
            
        }
        
        User32.INSTANCE.UpdateWindow(embedWindow);
        
        return embedWindow;
    }
    
    private void playAsBackground(String videoPath) throws Exception {
        File mpvFile = new File(mpvPath);
        
        
        
        
        if (!mpvFile.exists()) {
            throw new Exception("找不到mpv播放器: " + mpvPath);
        }
        
        
        // 获取屏幕尺寸
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = screenSize.width;
        int height = screenSize.height;
        
        
        // 嵌入方式启动
        int embedWindow = createBackgroundWindow();
        currentEmbedWindow = embedWindow;
        
        // 生成IPC管道路径
        jvmPid = java.lang.management.ManagementFactory.getRuntimeMXBean().getPid();
        ipcServerPath = "\\\\.\\pipe\\mpv-ipc-" + jvmPid;
        
        // 启动mpv（嵌入模式 + IPC）
        String cmd = String.format("\"%s\" --no-border --no-osd-bar --loop=inf --mute=no --vo=gpu --wid=%d --input-ipc-server=\"%s\" --term-status-msg=\"${=time-pos}/${=duration}\" \"%s\"",
            mpvPath, embedWindow, ipcServerPath, videoPath);
        
        Process mpvProc = Runtime.getRuntime().exec(cmd);
        Thread.sleep(3000);
        
        if (!mpvProc.isAlive()) {
            throw new Exception("mpv进程启动后立即退出");
        }
        
        this.mpvProcess = mpvProc;
        this.embedWindowHandle = embedWindow;
        
        // 等待mpv就绪
        Thread.sleep(1000);
        
        // 启动日志解析（获取时间）
        startLogParser();
        
        // 初始化IPC读写
        initIpcCommunication();
        
        // 启动监控线程
        monitorMpvProcess(mpvProc);
        
        // 启动时间更新线程
        startTimeUpdater();
        
        
    }
    
    private int embedWindowHandle = 0;
    
    // 监控mpv进程
    private void monitorMpvProcess(Process proc) {
        java.util.concurrent.atomic.AtomicReference<Process> currentProc = new java.util.concurrent.atomic.AtomicReference<>(proc);
        Thread monitorThread = new Thread(() -> {
            while (currentProc.get() != null && currentProc.get().isAlive()) {
                try {
                    Thread.sleep(5000);
                    // 检查窗口是否还活着
                    boolean windowValid = currentEmbedWindow != 0 && User32.INSTANCE.IsWindow(currentEmbedWindow);
                    if (!windowValid) {
                        currentProc.get().destroyForcibly();
                        Thread.sleep(1000);
                        restartMpv();
                        currentProc.set(mpvProcess);
                    }
                } catch (Exception e) {
                    break;
                }
            }
            stopTimeUpdater();
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * 使用JNA调用Windows API查找真正的桌面背景容器
     * 找到SHELLDLL_DefView的父窗口作为背景容器
     */
    private int findBackgroundContainer() {
        
        
        try {
            // 1. 首先找到Progman窗口
            int progman = User32.INSTANCE.FindWindow("Progman", null);
            if (progman == 0) {
                
                return 0;
            }
            
            
            // 2. 发送消息创建WorkerW
            int[] result = new int[1];
            int sendResult = User32.INSTANCE.SendMessageTimeout(progman, 
                User32.WM_SPAWN_WORKERW, 0, 0, User32.SMTO_ABORTIFHUNG, 1000, result);
            
            
            
            // 3. 等待WorkerW创建
            Thread.sleep(1000);
            
            // 4. 使用JNA枚举窗口，查找正确的WorkerW（包含SHELLDLL_DefView的）
            final int[] foundWorkerW = {0};
            
            User32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
                @Override
                public boolean callback(int hWnd, int lParam) {
                    // 获取窗口类名
                    byte[] className = new byte[256];
                    int classNameLength = User32.INSTANCE.GetClassNameA(hWnd, className, 256);
                    String windowClass = new String(className, 0, classNameLength);
                    
                    // 获取窗口标题
                    char[] windowText = new char[256];
                    int textLength = User32.INSTANCE.GetWindowText(hWnd, windowText, 256);
                    String windowTitle = new String(windowText, 0, textLength).trim();
                    
                    // 
                    
                    // 只关注WorkerW窗口（没有标题的）
                    if ("WorkerW".equals(windowClass) && windowTitle.isEmpty()) {
                        
                        
                        // 在WorkerW内部查找SHELLDLL_DefView（子窗口）
                        int shellDefView = User32.INSTANCE.FindWindowEx(hWnd, 0, "SHELLDLL_DefView", null);
                        if (shellDefView != 0) {
                            
                            foundWorkerW[0] = hWnd;
                            return false; // 停止枚举，找到正确的容器了
                        }
                    }
                    
                    return true; // 继续枚举
                }
            }, 0);
            
            // 5. 如果找到了包含SHELLDLL_DefView的WorkerW，返回它
            if (foundWorkerW[0] != 0) {
                return foundWorkerW[0];
            }
            
            // 6. 备选方案：在所有WorkerW中查找SHELLDLL_DefView
            
            final int[] searchResult = {0};
            User32.INSTANCE.EnumWindows(new User32.WNDENUMPROC() {
                @Override
                public boolean callback(int hWnd, int lParam) {
                    byte[] className = new byte[256];
                    User32.INSTANCE.GetClassNameA(hWnd, className, 256);
                    String windowClass = new String(className, 0, 256);
                    
                    if ("WorkerW".equals(windowClass)) {
                        int shellDefView = User32.INSTANCE.FindWindowEx(hWnd, 0, "SHELLDLL_DefView", null);
                        if (shellDefView != 0) {
                            
                            searchResult[0] = hWnd;
                            return false;
                        }
                    }
                    return true;
                }
            }, 0);
            
            if (searchResult[0] != 0) {
                return searchResult[0];
            }
            
            // 7. 尝试查找任何包含图标的窗口
            
            int workerW = User32.INSTANCE.FindWindowEx(progman, 0, "WorkerW", null);
            if (workerW != 0) {
                
                return workerW;
            }
            
            // 8. 最后备选：使用Progman
            
            return progman;
            
        } catch (Exception e) {
            System.err.println("查找背景容器失败: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    private void createEmbedWindow() {
        if (embedFrame != null) {
            embedFrame.dispose();
        }
        
        // 获取屏幕尺寸
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
        embedFrame = new JFrame();
        embedFrame.setTitle("VideoEmbedFrame");
        embedFrame.setUndecorated(true);
        embedFrame.setType(JFrame.Type.UTILITY);
        embedFrame.setAlwaysOnTop(false);
        embedFrame.setSize(screenSize);
        embedFrame.setLocation(0, 0);
        
        // 创建标签
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        embedFrame.add(label);
        
        // 设置窗口透明
        embedFrame.setBackground(new Color(0, 0, 0, 0));
        
        // 显示窗口
        embedFrame.setVisible(true);
        embedFrame.setState(JFrame.ICONIFIED);
        
        // 强制更新窗口
        embedFrame.repaint();
    }
    
    private int getEmbedWindowHandle() {
        // 查找嵌入窗口
        int handle = User32.INSTANCE.FindWindow("SunAwtFrame", "VideoEmbedFrame");
        if (handle != 0) {
            
            return handle;
        }
        
        // 尝试查找Java窗口
        handle = User32.INSTANCE.FindWindow("SunAwtFrame", "背景动画播放器");
        if (handle != 0) {
            
            return handle;
        }
        
        return 0;
    }
    
    void togglePause() {
        if (!isPlaying) return;
        
        // 切换暂停状态
        isPaused = !isPaused;
        
        // 发送到mpv窗口
        long mpvWindow = findMpvWindow();
        if (mpvWindow != 0) {
            User32.INSTANCE.PostMessage((int)mpvWindow, User32.WM_KEYDOWN, 0x20, 0);
            User32.INSTANCE.PostMessage((int)mpvWindow, User32.WM_KEYUP, 0x20, 0);
            
        } else if (currentEmbedWindow != 0) {
            User32.INSTANCE.PostMessage(currentEmbedWindow, User32.WM_KEYDOWN, 0x20, 0);
            User32.INSTANCE.PostMessage(currentEmbedWindow, User32.WM_KEYUP, 0x20, 0);
            System.out.println("[控制] 暂停/播放(嵌入)");
        } else {
            
        }
        statusLabel.setText(isPaused ? "已暂停" : "播放中");
    }
    
    // 切换横屏/竖屏
    void toggleRotation() {
        if (!isPlaying) return;
        
        isLandscape = !isLandscape;
        
        if (isLandscape) {
            switchToLandscape();
            statusLabel.setText("横屏模式");
        } else {
            switchToPortrait();
            statusLabel.setText("竖屏模式");
        }
    }
    
    // 切换到横屏
    private void switchToLandscape() {
        if (currentEmbedWindow == 0) return;
        
        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int width = screenSize.width;
            int height = screenSize.height;
            
            User32.INSTANCE.SetWindowPos(currentEmbedWindow, 0, 0, 0, width, height, 
                User32.SWP_NOZORDER | User32.SWP_NOACTIVATE);
            User32.INSTANCE.UpdateWindow(currentEmbedWindow);
            
            System.out.println("[横竖屏] 切换到横屏: " + width + "x" + height);
        } catch (Exception e) {
            System.err.println("[横竖屏] 切换横屏失败: " + e.getMessage());
        }
    }
    
    // 切换到竖屏
    private void switchToPortrait() {
        if (currentEmbedWindow == 0) return;
        
        try {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            // 竖屏：宽度为高度的9/16（约人像比例）
            int height = screenSize.height;
            int width = (int)(height * 9.0 / 16.0);
            int x = (screenSize.width - width) / 2;
            int y = 0;
            
            User32.INSTANCE.SetWindowPos(currentEmbedWindow, 0, x, y, width, height, 
                User32.SWP_NOZORDER | User32.SWP_NOACTIVATE);
            User32.INSTANCE.UpdateWindow(currentEmbedWindow);
            
            System.out.println("[横竖屏] 切换到竖屏: " + width + "x" + height + " (" + x + ", " + y + ")");
        } catch (Exception e) {
            System.err.println("[横竖屏] 切换竖屏失败: " + e.getMessage());
        }
    }
    
    // 刷新显示（从mpv获取实际状态）
    private void refreshDisplay() {
        if (!isPlaying) return;
        
        try {
            // 使用tasklist直接检查mpv.exe进程
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq mpv.exe", "/FO", "CSV");
            pb.redirectErrorStream(true);
            Process checkProc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(checkProc.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            checkProc.waitFor();
            boolean mpvRunning = output.toString().contains("mpv.exe");
            
            if (mpvRunning) {
                playingLabel.setText("正在播放: " + new File(currentVideoPath).getName());
                statusLabel.setText(isPaused ? "已暂停" : "播放中");
                
            } else {
                // mpv进程已退出
                
                statusLabel.setText("播放已停止 (mpv已退出)");
                playingLabel.setText("未播放视频");
                isPlaying = false;
            }
        } catch (Exception e) {
            System.err.println("刷新显示失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    void stopVideo() {
        isPlaying = false;
        isPaused = false;
        currentVideoPath = null;
        startTime = 0;
        
        // 停止显示更新器
        if (displayUpdateTimer != null) {
            displayUpdateTimer.stop();
            displayUpdateTimer = null;
        }
        
        // 停止mpv客户端（带IPC）
        if (mpvClient != null) {
            mpvClient.stop();
            mpvClient = null;
            
        }
        
        // 停止传统mpv进程
        if (mpvProcess != null) {
            mpvProcess.destroy();
            mpvProcess = null;
        }
        
        // 尝试结束所有mpv进程
        try {
            Process killProcess = new ProcessBuilder("taskkill", "/F", "/IM", "mpv.exe").start();
            killProcess.waitFor();
            
        } catch (Exception e) {
            System.err.println("结束mpv进程失败: " + e.getMessage());
        }
        
        statusLabel.setText("已停止");
        playingLabel.setText("未播放视频");
        progressSlider.setValue(0);
        if (timeLabel != null) timeLabel.setText("0:00");
        
        // 重新启动显示更新器（供下次播放使用）
        startDisplayUpdater();
        
        if (trayIcon != null) {
            trayIcon.displayMessage("已停止", "视频已停止", TrayIcon.MessageType.INFO);
        }
    }
    
    void exitApplication() {
        stopVideo();
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
        System.exit(0);
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            背景动画播放 player = new 背景动画播放();
            player.setVisible(true);
            
            String lastVideo = player.config.getProperty(LAST_VIDEO_KEY);
            if (lastVideo != null && new File(lastVideo).exists()) {
                int result = JOptionPane.showConfirmDialog(player,
                    "是否继续播放上次选择的视频？\n" + new File(lastVideo).getName(),
                    "继续播放",
                    JOptionPane.YES_NO_OPTION);
                
                if (result == JOptionPane.YES_OPTION) {
                    player.playVideo(lastVideo);
                }
            }
        });
    }
}
