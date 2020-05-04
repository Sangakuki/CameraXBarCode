# CameraXBarCode
基于CameraX的高效二维码识别工具

使用CameraX作为摄像头的采集与预览端，极大的简化了Camera的操作与适配。
在ImageAnalysis.Analyzer 里处理图像（裁剪与旋转等），交由Zxing识别，识别高效（帧率接近35帧）。

本项目基于Google的CameraX Demo。

未来规划：yuv数据的裁剪与旋转交由RenderScript或libyuv处理。
