// grandpoem-launcher - a tiny splash bootstrap for GrandPoem Studio.
//
// Why this exists: the packaged app is a ~200 MB unsigned executable plus ~50k
// files. On a machine that has just installed it, the OS and the antivirus can
// spend tens of seconds before Electron reaches `app.whenReady()` and is able to
// draw anything at all. During that window the app itself cannot paint a splash,
// because it has not started yet.
//
// This launcher is a ~15 KB WinForms binary: it paints instantly, starts the real
// executable, and closes itself once that process has a message pump (i.e. its
// main window is being created) or after a hard timeout. It does not change how
// long the app takes to start - it only makes the wait visible.
//
// Build: scripts/build-launcher.mjs (uses the in-box .NET Framework compiler, so
// no SDK is required). See docs/startup-performance-plan.md (scheme 3b).

using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Windows.Forms;

namespace GrandPoemStudio
{
    internal static class Program
    {
        private const string ProductExeName = "GrandPoem Studio.exe";

        [STAThread]
        private static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            string target = ResolveTarget(args);
            if (target == null)
            {
                MessageBox.Show(
                    "Cannot find " + ProductExeName + " next to this launcher.",
                    "GrandPoem Studio",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                return;
            }

            Process process;
            try
            {
                ProcessStartInfo startInfo = new ProcessStartInfo(target);
                startInfo.UseShellExecute = false;
                startInfo.WorkingDirectory = Path.GetDirectoryName(target);

                // Forward anything after the executable path (e.g. --updated).
                if (args.Length > 1)
                {
                    string[] forwarded = new string[args.Length - 1];
                    Array.Copy(args, 1, forwarded, 0, forwarded.Length);
                    startInfo.Arguments = string.Join(" ", forwarded);
                }

                process = Process.Start(startInfo);
            }
            catch (Exception error)
            {
                MessageBox.Show(
                    "Failed to start GrandPoem Studio:\n" + error.Message,
                    "GrandPoem Studio",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                return;
            }

            using (SplashForm splash = new SplashForm(process))
            {
                Application.Run(splash);
            }
        }

        /// <summary>
        /// Locate the real executable. The launcher is shipped inside
        /// resources/bin, so the app root is two levels up; the search also
        /// covers a flat layout in case the launcher is moved next to the app.
        /// </summary>
        private static string ResolveTarget(string[] args)
        {
            if (args.Length > 0 && !string.IsNullOrEmpty(args[0]) && File.Exists(args[0]))
            {
                return args[0];
            }

            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string[] candidates = new string[]
            {
                Path.Combine(baseDir, ProductExeName),
                Path.Combine(Path.GetFullPath(Path.Combine(baseDir, "..")), ProductExeName),
                Path.Combine(Path.GetFullPath(Path.Combine(baseDir, "..", "..")), ProductExeName),
            };

            foreach (string candidate in candidates)
            {
                if (File.Exists(candidate))
                {
                    return candidate;
                }
            }

            // Last resort: an installed app registered under the user profile.
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string programs = Path.Combine(localAppData, "Programs");
            if (Directory.Exists(programs))
            {
                string[] matches = Directory.GetFiles(programs, ProductExeName, SearchOption.AllDirectories);
                if (matches.Length > 0)
                {
                    return matches[0];
                }
            }

            return null;
        }
    }

    internal sealed class SplashForm : Form
    {
        private const int MinimumVisibleMs = 900;
        private const int HardTimeoutMs = 30000;
        private const int PollIntervalMs = 200;

        private readonly Process _process;
        private readonly Timer _timer;
        private readonly Stopwatch _elapsed = Stopwatch.StartNew();
        private readonly Font _titleFont;
        private readonly Font _subtitleFont;
        private readonly Font _logoFont;
        private int _dotPhase;

        public SplashForm(Process process)
        {
            _process = process;

            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(420, 220);
            ShowInTaskbar = true;
            TopMost = true;
            Text = "GrandPoem Studio";
            DoubleBuffered = true;
            BackColor = Color.FromArgb(248, 250, 252);

            _titleFont = CreateFont("Microsoft YaHei UI", 15f, FontStyle.Bold, "Segoe UI");
            _subtitleFont = CreateFont("Microsoft YaHei UI", 9.5f, FontStyle.Regular, "Segoe UI");
            _logoFont = CreateFont("Segoe UI", 20f, FontStyle.Bold, "Segoe UI");

            _timer = new Timer();
            _timer.Interval = PollIntervalMs;
            _timer.Tick += OnTick;
            _timer.Start();
        }

        private static Font CreateFont(string preferred, float size, FontStyle style, string fallback)
        {
            try
            {
                Font font = new Font(preferred, size, style);
                return font;
            }
            catch
            {
                return new Font(fallback, size, style);
            }
        }

        private void OnTick(object sender, EventArgs e)
        {
            _dotPhase = (_dotPhase + 1) % 4;
            Invalidate();

            bool exited = false;
            try
            {
                exited = _process.HasExited;
            }
            catch
            {
                exited = true;
            }

            if (exited)
            {
                Close();
                return;
            }

            if (_elapsed.ElapsedMilliseconds < MinimumVisibleMs)
            {
                return;
            }

            // Hand over as soon as the real app owns a visible top-level window.
            // This is deliberately NOT WaitForInputIdle: Electron/Chromium
            // creates message-only windows within the first moments of the
            // process, which would hand over long before anything is on screen.
            bool hasWindow = false;
            try
            {
                _process.Refresh();
                hasWindow = _process.MainWindowHandle != IntPtr.Zero;
            }
            catch
            {
                hasWindow = false;
            }

            if (hasWindow || _elapsed.ElapsedMilliseconds >= HardTimeoutMs)
            {
                Close();
            }
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            Rectangle bounds = ClientRectangle;
            using (LinearGradientBrush background = new LinearGradientBrush(
                bounds,
                Color.FromArgb(248, 250, 252),
                Color.FromArgb(239, 246, 255),
                45f))
            {
                g.FillRectangle(background, bounds);
            }

            using (Pen border = new Pen(Color.FromArgb(226, 232, 240)))
            {
                g.DrawRectangle(border, 0, 0, Width - 1, Height - 1);
            }

            // Logo tile
            int logoSize = 64;
            int logoX = 36;
            int logoY = 46;
            Rectangle logoRect = new Rectangle(logoX, logoY, logoSize, logoSize);
            using (GraphicsPath path = RoundedRect(logoRect, 16))
            using (LinearGradientBrush logoBrush = new LinearGradientBrush(
                logoRect,
                Color.FromArgb(37, 99, 235),
                Color.FromArgb(147, 51, 234),
                45f))
            {
                g.FillPath(logoBrush, path);
            }

            using (StringFormat centered = new StringFormat())
            {
                centered.Alignment = StringAlignment.Center;
                centered.LineAlignment = StringAlignment.Center;
                g.DrawString("JY", _logoFont, Brushes.White, logoRect, centered);
            }

            int textX = logoX + logoSize + 20;
            using (SolidBrush titleBrush = new SolidBrush(Color.FromArgb(30, 41, 59)))
            using (SolidBrush subtitleBrush = new SolidBrush(Color.FromArgb(100, 116, 139)))
            {
                g.DrawString("JYparalegal", _titleFont, titleBrush, textX, logoY + 6);
                g.DrawString("给中国法律人用的 AI 合同工作台", _subtitleFont, subtitleBrush, textX, logoY + 38);
            }

            string[] dots = new string[] { "", ".", "..", "..." };
            string status = "正在启动" + dots[_dotPhase];
            using (SolidBrush statusBrush = new SolidBrush(Color.FromArgb(71, 85, 105)))
            {
                g.DrawString(status, _subtitleFont, statusBrush, logoX, logoY + logoSize + 34);
            }
        }

        private static GraphicsPath RoundedRect(Rectangle bounds, int radius)
        {
            int diameter = radius * 2;
            GraphicsPath path = new GraphicsPath();
            path.AddArc(bounds.X, bounds.Y, diameter, diameter, 180, 90);
            path.AddArc(bounds.Right - diameter, bounds.Y, diameter, diameter, 270, 90);
            path.AddArc(bounds.Right - diameter, bounds.Bottom - diameter, diameter, diameter, 0, 90);
            path.AddArc(bounds.X, bounds.Bottom - diameter, diameter, diameter, 90, 90);
            path.CloseFigure();
            return path;
        }

        protected override void Dispose(bool disposing)
        {
            if (disposing)
            {
                if (_timer != null)
                {
                    _timer.Stop();
                    _timer.Dispose();
                }
                if (_titleFont != null) _titleFont.Dispose();
                if (_subtitleFont != null) _subtitleFont.Dispose();
                if (_logoFont != null) _logoFont.Dispose();
            }
            base.Dispose(disposing);
        }
    }
}
