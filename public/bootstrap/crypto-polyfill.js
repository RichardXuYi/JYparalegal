// crypto.randomUUID 只在安全上下文（HTTPS/localhost）可用；用 getRandomValues 兜底，
// 让纯 HTTP 部署也能生成 id。与 theme.js 同属首屏前置脚本，故保持经典脚本。
if (window.crypto && !window.crypto.randomUUID) {
  window.crypto.randomUUID = function () {
    var b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    var h = Array.from(b, function (x) {
      return x.toString(16).padStart(2, '0');
    }).join('');
    return (
      h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' +
      h.slice(16, 20) + '-' + h.slice(20)
    );
  };
}
