# 应用图标

`icon.svg` 是源文件。各平台用它生成：

| 文件 | 用途 |
|------|------|
| `icon.ico` | Windows |
| `icon.icns` | macOS |
| `icon.png` | 通用 512 |
| `16x16.png` 到 `512x512.png` | Linux |
| `tray-icon-template.svg` | 菜单栏图标源 |
| `tray-icon-Template.png` | macOS 状态栏，文件名里的 Template 要保留 |

在仓库的 `studio-frontend/` 下生成：

```bash
./scripts/generate-icons.sh
```

macOS 需要 ImageMagick 和 librsvg。Linux 安装 `imagemagick` 与 `librsvg2-bin`。
