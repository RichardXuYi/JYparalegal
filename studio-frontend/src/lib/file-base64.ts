/** 上传体积上限，与后端 `MaxUploadSizeExceededException` 提示的 50MB 保持一致。 */
export const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

const BINARY_CHUNK = 0x8000;

/**
 * File → base64 字符串。
 *
 * <p>不能写成 `btoa(String.fromCharCode(...new Uint8Array(buf)))`：展开几 MB 的数组会超过
 * 引擎的实参个数上限，浏览器直接抛 RangeError，几 MB 的文件就永远传不上去。</p>
 */
export async function fileToBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = '';
  for (let i = 0; i < bytes.length; i += BINARY_CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + BINARY_CHUNK));
  }
  return btoa(binary);
}
