// 首屏同步应用上次缓存的主题，消除运行时切换导致的闪白（见 App.tsx 的主题 effect）。
//
// 这个文件必须是**经典阻塞脚本**、不能进模块图：`type="module"` 会延迟到文档解析完
// 再执行，暗色用户就会先看到一帧白底。index.html 里以 <script src> 引用。
(function () {
  try {
    var mode = localStorage.getItem('jy.theme');
    if (mode === 'dark' || mode === 'light') {
      document.documentElement.classList.add(mode);
    }
  } catch (e) { /* 忽略：隐私模式或存储不可用 */ }
})();
