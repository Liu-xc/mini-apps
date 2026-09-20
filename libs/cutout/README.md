# cutout

mini-apps 各应用的主体抠图 SDK：u2netp 显著性分割 + ONNX Runtime，RGBA bytes 进出，纯 Kotlin JVM。

- **状态**：阶段 A 已实现（[specs/00-architecture.md](specs/00-architecture.md)），JVM 真模型自测 14 用例全绿；wardrobe 接入 = wardrobe it-016 阶段 B（待其并行功能落地）
- **构建/自测**：`wardrobe/gradlew -p libs/cutout test`（桌面 JVM 跑真实 u2netp.onnx 推理，无需模拟器）
- **模型**：`src/test/resources/u2netp.onnx`（4.4MB，rembg 同款，SHA-256 见 specs/06-decisions.md ADR-001）；阶段 B 时复制进 wardrobe `app/src/main/assets/`
- **与 [libs/store](../store/) 的关系**：互不依赖；同为纯 JVM composite 模块（store 管数据，cutout 管图像）

> 注：本目录为公共基础设施（非应用代码），根 AGENTS.md「不在应用目录外放代码」条款例外与 store/carddeck/sync 相同。
