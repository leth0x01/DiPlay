# LynkPlay 领克车机图标

## 来源

- 来源页面：[领克官方媒体库 Logos](https://press.lynkco.com/de-DE/assets/226129/)。
- 官方文件：[Black Lynk & Co Logo](https://d21buns5ku92am.cloudfront.net/69367/images/381737-379614-logo-black-b14ecb-original-1613650285-2019c4-original-1615387947.png)。
- 获取日期：2026-10-11。
- 原始文件：`asset/lynkco-wordmark-black.png`，837 × 210 PNG。
- 标志及商标归 Lynk & Co 所有；下载来源不表示获得品牌授权或官方认证，也不将该商标素材声明为项目 GPL 许可内容。

## 应用位置与处理

`common/src/main/res/raw/ic_car_home.png` 是 CarPlay 应用列表中“返回车机”按钮的默认图标，同时用于设置页默认图标预览。原图为 192 × 192 白底红色 BYD 标志。

替换图保持 192 × 192 白底，官方黑色 LYNK & CO 标志等比缩放到宽 160 px，水平和垂直居中。不重绘字形、不裁切标志、不改变布局尺寸。成品为 2921 字节，低于原图的 4218 字节，保持现有 AirPlay 图标传输约束。

默认车机按钮名称同步改为 `LYNK & CO`。用户已保存的自定义图标和名称继续优先使用；如仍看到原图，可在设置中恢复默认图标，并在下一次 CarPlay 连接时查看。

本次替换不涉及绿色应用启动图标、BYD HUD 导航箭头或车辆功能接口。
