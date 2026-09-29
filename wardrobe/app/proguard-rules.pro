# it-071 P3 / ADR-028：R8 首开的定点规则文件。
# 三方库（coil / kotlinx-serialization / lottie / compose / onnxruntime…）自带 consumer rules，
# 默认不追加全局 -keep。release 冒烟若暴露类/方法被误剥离，在此定点补 keep 并在 it-071 验证记录注明。
