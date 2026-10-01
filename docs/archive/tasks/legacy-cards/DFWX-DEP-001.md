# DFWX-DEP-001：vendor、依赖和合规

状态：待执行
前置：TEST-001、AI-001

## 目标

固定 RikkaHub 上游版本和补丁重放方式，降低 SNAPSHOT/JitPack 不可复现风险，补齐 SBOM、LICENSE 和 NOTICE。

## 禁止

不擅自升级依赖；不删除上游署名；不引入绕过上游付费的能力；不把未核对的 master 当稳定版本；不输出凭据。

## 验收

上游 tag/commit 可追溯；patch 可逐条重放；动态依赖有处置；依赖来源和许可证有记录；构建在规定环境可复现。
