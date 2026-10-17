# 算法对照输入

- `calibration.png`：项目通过 `tools/RenderQuantizerPreview.java` 程序生成的原测试图，552×768。固定保留作为灰阶、文字、色块回归输入，不以单张图决定照片算法优劣。
- `astronaut.png`：scikit-image v0.25.2 数据集中的 NASA 图像，512×512。该项目官方文档说明其来自 NASA Great Images，已释放到 public domain，无已知版权限制。
  - 图像：https://raw.githubusercontent.com/scikit-image/scikit-image/v0.25.2/skimage/data/astronaut.png
  - 来源及权利说明：https://scikit-image.org/docs/stable/api/skimage.data.html#skimage.data.astronaut
  - SHA-256：`88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5`

额外的文字细线、肤色/中性梯度、暗部纹理图由 `tools/AlgorithmLab.java` 生成。肤色色块仅为算法压力测试，不是经测量的标准色卡。暗部纹理为合成图，不冒充自然风景照片。评选通用照片算法时仍建议补入用户授权的逆光人像、风景、宠物与实际用途图片。
